package com.bowman.cardserv.session;

import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.*;
import com.bowman.cardserv.cws.CwsConnectorManager;
import com.bowman.util.Globber;

import java.net.*;
import java.io.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 9, 2005
 * Time: 4:29:19 AM
 */
public class NewcamdSession extends AbstractSession {

  private static final int LOGIN_SO_TIMEOUT = 30 * 1000;

  private int emmCount, keepAliveCount;

  private byte[] cfgKey;
  protected NewcamdConnection conn;
  private CardData card;
  private boolean noValidation;

  public NewcamdSession(Socket conn, ListenPort listenPort, CamdMessageListener listener) {
    super(listenPort, listener);
    boolean noEncrypt = "true".equalsIgnoreCase(listenPort.getStringProperty("no-encryption"));
    noValidation = "true".equalsIgnoreCase(listenPort.getStringProperty("no-validation"));
    cfgKey = listenPort.getBytesProperty("des-key");
    if(cfgKey == null) {
      cfgKey = ProxyConfig.getInstance().getDefaultProfileDesKey();
      if(cfgKey == null) {
        logger.severe("No des-key set for listen-port '" + listenPort + "' and no default-des-key set in ca-profiles.");
        try {
          conn.close();
        } catch(IOException e) {}
        return;
      } else logger.fine("Using default des-key: " + DESUtil.bytesToString(cfgKey));
    } else {
      logger.fine("Using des-key configured for port '" + listenPort + "': " + DESUtil.bytesToString(cfgKey));
    }
    this.conn = new NewcamdConnection(conn, noEncrypt, ProxyConfig.getInstance().isDebug());

    String className = getClass().getName();
    className = className.substring(className.lastIndexOf('.') + 1);
    sessionThread = new Thread(this, className + "Thread-" + sessionId);
    sessionThread.start();
  }

  protected CamdNetMessage getLoginOkMsg() {
    return new CamdNetMessage(MSG_CLIENT_2_SERVER_LOGIN_ACK);
  }

  public void run() {

    logger.fine("Starting...");

    alive = true;
    CamdNetMessage msg;
    ProxyConfig config = ProxyConfig.getInstance();
    SessionManager sm = SessionManager.getInstance();
    UserManager um = config.getUserManager();
    boolean skipAuth = false;
    int originId = config.getProxyOriginId();

    try {
      conn.init(logger);
      remoteAddress = conn.getRemoteAddress();
      conn.setMaxSize(config.getNewcamdMaxMsgSize());
      conn.clientHandshake(LOGIN_SO_TIMEOUT, cfgKey);

      msg = readMessage();
      if(msg == null) {
        close();
      } else {
        fireCamdMessage(msg, false);
        if(msg.getCommandTag() != MSG_CLIENT_2_SERVER_LOGIN) {
          close();
        } else {
          String user = msg.getStringData()[0];
          String cryptPw = msg.getStringData()[1];
          String storedPw = null;
          try {
            storedPw = um.getPassword(user);
            loginName = user.length() > 32 ? user.substring(0, 32) : user;
            // ensure the username for the session has the stored case variations, not the ones used for the login
            String userName = um.getUserName(user);
            if(userName != null) user = userName;
          } catch (RuntimeException re) {
            if(config.isUserAllowOnFailure()) skipAuth = true;
          }
          clientId = msg.getClientIdStr();
          String ipMask = um.getIpMask(user);
          Set profiles = um.getAllowedProfiles(user);
          boolean checkProfile = (profiles != null && !profiles.isEmpty() && getProfile() != CaProfile.MULTIPLE);

          if(!skipAuth && (storedPw == null || !DESUtil.checkPassword(storedPw, cryptPw))) {
            loginFailure(user, "unauthorized/invalid/missing password", msg);
          } else if(!um.isEnabled(user)) {
            loginFailure(user, "account disabled", msg);
          } else if(!Globber.match(ipMask, getRemoteAddress(), false)) {
            loginFailure(user, "ip check failed: " + ipMask, msg);
          } else if(sm.checkSessionIP(user, getRemoteAddress())) {
            loginFailure(user, "session from different source ip exists, denying multiple", msg);
          } else if(!checkClientId(clientId)) {
            loginFailure(user, "client-id not allowed/supported: " + clientId, msg);
          } else if(checkProfile && !profiles.contains(getProfileName())) {
            loginFailure(user, "no access for profile: " + getProfileName(), msg);
          } else { // successful login

            maxSessions = config.getUserManager().getMaxConnections(user);
            if(maxSessions == -1) maxSessions = getProfile().getNewcamdPortCount();

            int sessionCount = sm.syncCountSessions(user, getProfileName());

            logger.info("User '" + user + "' (" + com.bowman.cardserv.util.CustomFormatter.formatAddress(getRemoteAddress()) +
                ") login successful. Client: " + clientId + " - [" + (sessionCount + 1) + "/" + maxSessions + "]");

            if(sessionCount >= maxSessions) {
              long idleMins = sm.closeOldestSession(user, false, getProfileName()) / 60000;
              logger.info("User '" + user + "' already has [" + maxSessions + "] connection(s), closing oldest (idle " +
                  idleMins + " mins)");
            }

            if(config.getMaxConnectionsIP() > 0 && sessionCount >= config.getMaxConnectionsIP()) {
              long idleMins = sm.closeOldestSession(getRemoteAddress(), true, getProfileName()) / 60000;
              logger.info("IP '" + com.bowman.cardserv.util.CustomFormatter.formatAddress(getRemoteAddress()) +
                  "' already has [" + sessionCount + "] connection(s), closing oldest (idle " +
                  idleMins + " mins)");
            }

            this.user = user;
            this.allowedServices = um.getAllowedServices(user, getProfile().getName());
            this.blockedServices = um.getBlockedServices(user, getProfile().getName());
            this.allowedConnectors = um.getAllowedConnectors(user);
            this.allowedRate = um.getAllowedEcmRate(user);
            if(allowedRate != -1) allowedRate = allowedRate * 1000;

            byte[] desKey = cfgKey;
            desKey = DESUtil.xorUserPass(desKey, cryptPw);
            desKey = DESUtil.desKeySpread(desKey);
            CamdNetMessage loginOkMsg = getLoginOkMsg();
            conn.sendMessage(loginOkMsg, msg.getSequenceNr(), desKey);
            fireCamdMessage(loginOkMsg, true);
            if(!"0.0.0.0".equals(getRemoteAddress())) {
              sm.addSession(this);
            } else {
              close();
              return;
            }
            conn.setSoTimeout(config.getSessionTimeout());
            Thread.currentThread().setName(Thread.currentThread().getName() + "[" + user + "]");
          }
        }
      }

      String reason = "";
      boolean checksOk;

      while(alive) {
        try {
          msg = readMessage();
        } catch (SocketTimeoutException e) {
          msg = null;
          String idleTime = conn==null?"":((System.currentTimeMillis() - conn.getLastTrafficTimeStamp()) / 60000) +
              " mins since last activity";
          logger.fine("Idle timeout" + (getUser()==null?"":" for user '" + getUser() + "'") +
               " (" + com.bowman.cardserv.util.CustomFormatter.formatAddress(getRemoteAddress()) + ") " + idleTime);
          if(config.getSessionKeepAlive() != 0) {
            Set clients = config.getKeepAliveExcludedClients();
            if(!clients.contains(getClientId().toLowerCase())) {
              sendMessage(new CamdNetMessage(CamdConstants.MSG_KEEPALIVE));
              continue;
            }
          } else {
            reason = "(" + idleTime + ")";
            close();
          }
        }
        if(msg == null) {
          alive = false;
          logger.info("Connection closed" + (getUser()==null?"":" for user '" + getUser() + "' ") + reason);
        } else {
          msg.setOriginId(originId);
          if(um.isEnabled(user)) {
            checksOk = checkLimits(msg); // checks use setFilteredBy to indicate a unwanted/bad message should not be processed
            checksOk = checksOk && handleMessage(msg);
            fireCamdMessage(msg, false); // still need to notify the rest of the proxy about the bad message to give plugins and logging a chance to see it
            if(!checksOk) {
              setFlag(msg, 'B');
              if(isConnected()) sendEcmReply(msg, msg.getEmptyReply()); // nothing elsewhere will acknowledge a filtered message so do it here
            }
          } else {
            logger.warning("'User '" + user + "' kicked, account disabled");
            close();
          }

        }
      }
    } catch (SocketException e) {
      logger.info(e.getMessage() + (getUser()==null?"":" for user '" + getUser() + "' (" + e + ")"));
    } catch (Exception e) {
      logger.throwing("Unexpected exception reading/decrypting message: " + e, e);
    }
    alive = false;
    endSession();
  }

  protected boolean checkClientId(String id) {
    return true;
  }

  protected boolean handleMessage(CamdNetMessage msg) {
    if(msg.isEcm() && !noValidation) {
      int id = msg.getCaIdFromHdr();
      String s;
      if(id != 0 && id != msg.getCaId()) {
        s = "Wrong ca-id in newcamd header: " + DESUtil.intToHexString(id, 4) + " (expected: " + DESUtil.intToHexString(msg.getCaId(), 4) + ") - ClientId: " + clientId;
        msg.setFilteredBy(s);
        logger.warning(s);
        logger.fine("Offending msg: " + msg.getCommandName() + " - " + DESUtil.bytesToString(msg.getRawIn()));
        return false;
      }
      id = msg.getProviderFromHdr();
      if(id != 0) {
        String ps = DESUtil.intToByteString(id, 3);
        if(!msg.getProviderContext().contains(ps)) {
          s = "Provider ident in newcamd header doesn't match card-data: " + ps + " (expected one of: " + msg.getProviderContext() + ")";
          msg.setFilteredBy(s);
          logger.warning(s);
          return false;
        } else { // ident existed in communicated card-data, but doesn't exist any more for this profile
          if(!getProfile().getProviderSet().contains(new Integer(id))) {
            s = "Provider ident '" + ps + "' doesn't exist for profile '" + getProfile().getName() + "', kicking session to force an update...";
            msg.setFilteredBy(s);
            logger.warning(s);
            close();
            return false;
          }
        }
      }
    }

    return handleNewcamd(msg);
  }

  protected boolean handleNewcamd(CamdNetMessage msg) {
    switch(msg.getCommandTag()) {

      case MSG_KEEPALIVE:
        logger.fine("Keep-alive received from '" + user + "' (" + getClientId() + "), responding...");
        CamdNetMessage keepAliveMsg = new CamdNetMessage(MSG_KEEPALIVE);
        sendMessageNative(keepAliveMsg, msg.getSequenceNr(), true);
        fireCamdMessage(keepAliveMsg, true);
        break;

      case MSG_CARD_DATA_REQ:
        CwsConnectorManager cm = ProxyConfig.getInstance().getConnManager();
        CwsConnector anyCws = cm.getCwsConnector(getProfileName());

        String cardDataType = listenPort.getStringProperty("card-data", "type");
        if(getProfile().isCacheOnly() && cardDataType == null) cardDataType = "empty";

        if(cardDataType == null && anyCws == null) { // no card-data specified and no connectors available

          Map map = cm.getMultiConnectors(getProfile().getNetworkId(), getProfile().getCaId());
          if(map.isEmpty() && getProfile().getProviderSet().isEmpty()) {
            close();
            logger.warning("Connection with no '" + getProfileName() + "' CWS connectors available, aborted...");
            return true;
          }

        }

        CardData card; boolean au = false;
        if(cardDataType != null) { // data has been specified

          String arg = listenPort.getStringProperty("card-data", "ca-id");
          if(arg == null) arg = listenPort.getStringProperty("card-data", "name");
          if("true".equalsIgnoreCase(listenPort.getStringProperty("card-data", "override-au"))) {
            // use specified even for au-users
            card = getCardData(cardDataType, arg);
          } else {
            card = getAuCardData(cm.getAUCardDataConnector(getProfileName(), getUser())); // see if this is au-user and if so use data from a relevant card
            if(card == null) card = getCardData(cardDataType, arg); // no
            else au = true;
          }
        } else { // send card-data from any available connector in the profile, unless au-user
          card = getAuCardData(cm.getAUCardDataConnector(getProfileName(), getUser()));
          if(card == null) {
            if(anyCws != null) card = anyCws.getRemoteCard();
            else {
              // no real connectors, create data from profile definition
              card = CardData.createData(getProfile().getCaId(), getProfile().getProviderIdents());
            }
          } else au = true;

          if(getProfile().isMismatchedCards()) { // cards with different provider sets in profile, return merged data
            Set merged = getProfile().getProviderSet();
            if(merged != null) {
              merged.removeAll(Arrays.asList(card.getProvidersAsInt()));
              card = CardData.createMergedData(card, (Integer[])merged.toArray(new Integer[merged.size()]));
            }
          }
        }

        if(au) this.card = card;
        else this.card = new CardData(card.getData(true));

        CamdNetMessage cardDataMsg = new CamdNetMessage(MSG_CARD_DATA);
        cardDataMsg.setCustomData(card.getData(!au));
        sendMessageNative(cardDataMsg, msg.getSequenceNr(), true);

        fireCamdMessage(cardDataMsg, true);
        break;
    }
    return true;
  }

  protected CardData getAuCardData(CwsConnector auCws) {
    if(auCws != null) {
      logger.info("Returning au-user card-data from " + auCws);
      return auCws.getRemoteCard();
    } else return null;
  }

  private CardData getCardData(String type, String name) {
    if("connector".equalsIgnoreCase(type)) {
      if(name == null) {
        logger.warning("No name specified for card-data type connector (" + getListenPort() + "), using empty data...");
        type = "empty";
      } else {
        type = "file";
        name = "etc/Cws[" + name + "].card";
      }
    }
    if("file".equalsIgnoreCase(type)) {
      if(name == null) {
        logger.warning("No name specified for card-data type file (" + getListenPort() + "), using empty data...");
        type = "empty";
      } else {
        try {
          CardData card = CardData.createFromFile(new File(name));
          if(card.getParseException() != null) {
            logger.warning("Parser exception occured while reading card-data file '"
              + name + "' (" + card.getParseException() + ")");
            logger.throwing(card.getParseException());
          }
          logger.info("Returning specified card-data (for " + getListenPort() + ") from file: " + name);
          return card;
        } catch (FileNotFoundException fne) {
          logger.warning("Card-data file (for " + getListenPort() + ") not found '" + name + "', using empty data...");
          type = "empty";
        } catch(IOException e) {
          logger.throwing(e);
          logger.warning("Error reading card-data file '" + name + "' (" + e + "), using empty data...");
          type = "empty";
        }
      }
    }
    if("config".equalsIgnoreCase(type)) {
      int caId = getProfile().getCaId();
      try {
        if(name != null) caId = Integer.parseInt(name, 16);
        String providers = getListenPort().getStringProperty("card-data", "providers");
        if(providers == null) providers = getListenPort().getStringProperty("card-data", "provider-idents");
        if(providers == null || providers.length() == 0) {
          CardData card = CardData.createEmptyData(caId);
          Integer[] idents = getProfile().getProviderIdents();
          if(idents.length != 0) card = CardData.createMergedData(card, idents);
          return card;
        } else return CardData.createData(caId, providers.split(","));
      } catch (Exception e) {
        logger.throwing(e);
        logger.warning("Failed to parse configured card-data for " + getListenPort() + ", returning empty... (" + e + ")");
        type = "empty";
      }
    }

    if(!"empty".equalsIgnoreCase(type)) {
      logger.warning("Invalid card-data type specified for '" + getListenPort() + "' (" + type + "), using empty data...");
    }

    logger.info("Returning empty card-data with ca-id " + getProfile().getCaIdStr());
    return CardData.createEmptyData(getProfile().getCaId());
  }

  private void loginFailure(String user, String cause, CamdNetMessage loginMsg) {
    if(ProxyConfig.getInstance().isLogFailures())
      logger.warning("User '" + user + "' (" + com.bowman.cardserv.util.CustomFormatter.formatAddress(getRemoteAddress()) +
          ") login denied, " + cause);
    SessionManager.getInstance().fireUserLoginFailed(user, listenPort + "/" + getProfileName(), getRemoteAddress(), cause);
    CamdNetMessage loginFailedMsg = new CamdNetMessage(MSG_CLIENT_2_SERVER_LOGIN_NAK);
    sendMessageNative(loginFailedMsg, loginMsg.getSequenceNr(), true);
    fireCamdMessage(loginFailedMsg, true);
    close();
  }

  public void close() {
    if(conn != null) conn.close();
    if(sessionThread != null) sessionThread.interrupt();
  }

  public boolean isConnected() {
    return conn != null && conn.isConnected();
  }

  CamdNetMessage readMessage() throws IOException {
    CamdNetMessage msg = conn.readMessage();
    if(msg != null) {
      msgCount++;
      if(msg.isEcm()) ecmCount++;
      else if(msg.isEmm()) emmCount++;
      else if(msg.isKeepAlive()) keepAliveCount++;
      if(card != null) {
        msg.setProviderContext(card.getProviders());
      }
      msg.setCaId(listenPort.getProfile().getCaId());
      msg.setProviderIdent(msg.getProviderFromHdr()); // todo - this could break things
      msg.setNetworkId(listenPort.getProfile().getNetworkId());
    }
    return msg;
  }

  public int sendMessage(CamdNetMessage msg) {
    int status = sendMessageNative(msg, -1, true);
    if(status != -1) fireCamdMessage(msg, true);
    return status;
  }

  public int sendEcmReplyNative(CamdNetMessage ecmRequest, CamdNetMessage ecmReply) {
    int result = sendMessageNative(ecmReply, ecmRequest.getSequenceNr(), true);
    endTransaction(ecmRequest, ecmReply, result);
    return result;
  }

  public synchronized int sendMessageNative(CamdNetMessage msg, int seqNr, boolean flush) {
    return conn.sendMessage(msg, seqNr, flush);
  }

  public String getProtocol() {
    return "Newcamd";
  }

  public int getEmmCount() {
    return emmCount;
  }

  public int getKeepAliveCount() {
    return keepAliveCount;
  }

  public long getIdleTime() {
    if(conn == null) return -1;
    else return System.currentTimeMillis() - conn.getLastTrafficTimeStamp();
  }

  public String getLastContext() {
    if(card == null) return "?";
    else return card.toString();
  }

  public boolean sendOsdMessage(String message) {
    if("Mgcamd".equals(clientId) || "Acamd".equals(clientId)) {
      CamdNetMessage osdMsg = new CamdNetMessage(CamdNetMessage.EXT_OSD_MESSAGE);
      try {
        osdMsg.setCustomData(message.getBytes("ISO-8859-1"));
      } catch(UnsupportedEncodingException e) {
        e.printStackTrace();
      }
      return sendMessage(osdMsg) != -1;
    } else return false;
  }
}
