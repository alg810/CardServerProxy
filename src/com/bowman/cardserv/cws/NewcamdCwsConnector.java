package com.bowman.cardserv.cws;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.*;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 15, 2005
 * Time: 12:58:19 PM
 * @noinspection SynchronizeOnNonFinalField
 */
public class NewcamdCwsConnector extends AbstractCwsConnector implements CamdConstants {

  String user, password;
  private byte[] configKey14;
  byte[] clientId;
  boolean noEncrypt;
  boolean overrideChecks;
  private boolean tracing;

  CardData remoteCard;

  private Thread keepAliveThread = null;
  private int keepAliveInterval;
  private int emmCount, keepAliveCount;

  NewcamdConnection conn;
  private ProxyXmlConfig lastXml;
  private Map caidProfileMap;

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {

    super.configUpdated(xml);

    String host = xml.getStringValue("host");
    int port = xml.getPortValue("port");
    String user = xml.getStringValue("user");
    String password = xml.getStringValue("password");
    boolean enabled = "true".equalsIgnoreCase(xml.getStringValue("enabled", "true"));
    noEncrypt = "true".equalsIgnoreCase(xml.getStringValue("no-encryption", "false"));

    byte[] configKey14;
    try {
      configKey14 = xml.getBytesValue("des-key");
    } catch (ConfigException e) {
      configKey14 = config.getDefaultConnectorDesKey();
      if(configKey14 == null) throw e;
    }

    keepAliveInterval = xml.getTimeValue("keepalive-interval", 0, "s");
    if(keepAliveInterval == 0) keepAliveInterval = config.getDefaultConnectorKeepAlive();

    clientId = new byte[] {0x00, 0x00};
    try {
      clientId = xml.getBytesValue("client-id");
    } catch (ConfigException e) {
      clientId = config.getDefaultClientId();
    }

    try {
      auUsers.clear();
      UserManager um = config.getUserManager();
      String auUsersStr = xml.getStringValue("au-users");
      StringTokenizer st = new StringTokenizer(auUsersStr);
      String auUser;
      while(st.hasMoreTokens()) {
        auUser = st.nextToken().trim();
        if(!um.exists(auUser)) logger.warning("AU-user '" + auUser + "' for " + getLabel() + " doesn't exist, skipping...");
        else auUsers.add(auUser);
      }
    } catch (ConfigException e) {
      auUsers.clear();
    }
    
    tracing = "true".equalsIgnoreCase(xml.getStringValue("tracing", "false"));
    if(conn != null) conn.setTraceLabel(tracing?name:null);
    overrideChecks = "true".equalsIgnoreCase(xml.getStringValue("override-checks", "false"));
    asynchronous = "true".equalsIgnoreCase(xml.getStringValue("asynchronous", "false"));
    if(asynchronous) logger.info("Using asynchronous newcamd mode.");

    boolean changed = !(host.equals(this.host) && port == this.port && user.equals(this.user) &&
        password.equals(this.password) && enabled == this.enabled && Arrays.equals(configKey14, this.configKey14));

    this.host = host;
    this.port = port;
    this.user = user;
    this.password = password;
    this.enabled = enabled;
    this.configKey14 = configKey14;

    if(!enabled) close();

    if(changed && enabled) {
      close();
      if(connManager != null) {
        synchronized(connManager) {
          connManager.notifyAll();
        }
      }
    }
    
    if(noProfile && !(this instanceof ChameleonCwsConnector)) {
      String mapStr = xml.getStringValue("caid-profile-map");
      String[] pairs = mapStr.split(" ");
      if(pairs.length < 1) throw new ConfigException(xml.getFullName(), "caid-profile-map", "Invalid profile map: " + mapStr);
      Map map = new HashMap();
      String[] kv;
      for(int i = 0; i < pairs.length; i++) {
        kv = pairs[i].split("=");
        if(kv.length != 2) throw new ConfigException(xml.getFullName(), "caid-profile-map", "Invalid map entry: " + pairs[i]);
        if(config.getProfile(kv[1]) == null) {
          if(config.isProfileDisabled(kv[1])) continue;
          else throw new ConfigException(xml.getFullName(), "caid-profile-map", "Invalid map entry in '" + mapStr + "', unknown profile: " + kv[1]);
        }
        try {
          map.put(new Integer(Integer.parseInt(kv[0], 16)), kv[1]);
        } catch (NumberFormatException nfe) {
          throw new ConfigException(xml.getFullName(), "caid-profile-map", "Invalid map entry in '" + mapStr + "', bad hex integer: " + kv[0]);
        }
      }

      if(profile == null) logger.warning("No profile configured for '" + name + "', will attempt to find a profile by ca-id on connect, using map: " + mapStr);
      caidProfileMap = map;
    } else caidProfileMap = null;


    logger.fine("Configuration updated. Enabled: " + enabled + " Changed: " + changed + " AU-users: " + auUsers);
    lastXml = xml;
  }

  void doLogin(CamdNetMessage loginMsg) throws IOException {
    CamdNetMessage msg;

    conn.init(logger);
    conn.setMaxSize(config.getNewcamdMaxMsgSize());
    conn.serverHandshake(configKey14);
    sendMessage(loginMsg);

    if(conn == null) throw new SocketException("Connection aborted during newcamd handshake.");
    msg = conn.readMessage();
    if(msg == null || msg.getCommandTag() != MSG_CLIENT_2_SERVER_LOGIN_ACK) {
      if(msg == null) logger.warning("Connection was closed on login, bad credentials or des key?");
      else logger.warning("Login failed, received: " + msg.getCommandName());
      connManager.cwsConnectionFailed(this, "Login failed (bad password or des key)");
      close();
      connecting = true;
    } else {
      conn.setDesKey16(DESUtil.desKeySpread(DESUtil.xorUserPass(configKey14, DESUtil.cryptPassword(password))));
      logger.info("Login successful, key changed");
      sendMessage(new CamdNetMessage(MSG_CARD_DATA_REQ));
      msg = conn.readMessage();
      if(msg != null) {
        while(msg.getCommandTag() != MSG_CARD_DATA && conn != null) {
          logger.warning("Expected card data, received: " + msg + " (ignored and discarded)");
          msg = conn.readMessage();
        }
        if(msg != null) setRemoteCard(new CardData(msg.getCustomData(), getName()));
      }
      if(conn != null) {
        connectTimeStamp = System.currentTimeMillis();
        connManager.cwsConnected(this);
      }
    }
  }

  public void run() {
    alive = true;
    CamdNetMessage msg;
    try {

      doLogin(CamdNetMessage.getNewcamdLoginMessage(user, password, clientId));

      if(conn != null && conn.isConnected()) {
        conn.setSoTimeout(SESSION_SO_TIMEOUT);
        connecting = false;

        while(alive && conn != null) {
          msg = conn.readMessage();
          lastSent = null;
          if(msg == null) {
            alive = false;
            logger.warning("Connection closed");
          } else {
            msg.setCaId(remoteCard.getCaId());            
            if(msg.isEcm()) { // ecm reply, report to listener
              if(!reportReply(msg)) logger.fine("No listener found for ECM reply: " + msg);
            } else if(msg.isEmm()) { // emm reply, do nothing
              logger.fine("EMM reply ignored: " + msg);
            } else if(msg.isKeepAlive()) { // keep alive reply, report to reset time-out mode if set
              logger.fine("Keep-alive reply received: [" + msg.getSequenceNr() + "]");
              reportReply(msg);
            } else if(msg.isOsdMsg()) {
              logger.info("Mgcamd OSD message received: " + msg);
            } else {
              logger.warning("Unknown msg received from CWS: " + DESUtil.bytesToString(msg.getRawIn()));
            }
          }
        }
      }

    } catch(SocketException e) {
      logger.warning("Connection closed: " + e.getMessage());
    } catch(IOException e) {
      logger.throwing("Exception reading/decrypting message: " + e, e);
    } catch(Exception e) {
      e.printStackTrace();
    }

    conn = null;
    remoteCard = null;

    // this connection died, any pending request isn't likely to succeed :) tell the cache
    reset();

    if(!connecting) {
      lastDisconnectTimeStamp = System.currentTimeMillis();
      connManager.cwsDisconnected(this);
      synchronized(connManager) {
        connManager.notifyAll();
      }
    }
    readerThread = null;
    connecting = false;

    if(noProfile) profile = null; // reset this to trigger autodetect in case card has changed when reconnecting later

    logger.info("Connector dying");
  }

  protected void reportChannelStatus(QueueEntry qe) {
    super.reportChannelStatus(qe);
    CamdNetMessage reply = qe.getReply();
    if(!reply.isEmpty()) {      
      if(remoteCard != null && remoteCard.getProviders() != null) {
        int provider = reply.getUpperBits() >> 4; 
        // is this really the index of the provider used, as it appears in the card-data provider list?
        // assuming it is, and the servers sets it properly, decode it and store the provider ident as string
        if(provider >= 0 && provider < remoteCard.getProviders().length)
          reply.setProviderContext(new String[] {remoteCard.getProviders()[provider]});
      }
    }
  }

  protected void setRemoteCard(CardData card) {
    // attempt auto-mapping to profile by ca-id, only works if no two ca-profiles share the same system
    this.remoteCard = card;

    boolean skipChecks = overrideChecks;

    if(remoteCard.getParseException() != null) {
      logger.warning("Failed to parse card data: " + DESUtil.bytesToString(remoteCard.getData(false)) + " (" +
          remoteCard.getParseException() + ") - Skipping card checks...");
      skipChecks = true;
    } else logger.info("Received card data: " + remoteCard);
    
    if(providerIdents == null || providerIdents.isEmpty()) {
      providerIdents = new HashSet();
      providerIdents.addAll(Arrays.asList(card.getProvidersAsInt()));
    }

    if(profile != null) {
      logger.fine("Preconfigured card profile: " + profile);

      if(!skipChecks) checkCard(card);
      dumpCard(card);
      return; // already pre-set by config, skip auto detect by ca-id
    }

    // auto-detect profile by ca-id
    if(caidProfileMap != null) {
      String profileName = (String)caidProfileMap.get(new Integer(remoteCard.getCaId()));
      if(profileName != null) profile = ProxyConfig.getInstance().getProfile(profileName);
    }

    if(profile == null) {
      logger.warning("Unknown card! No profile map entry matching id '" + remoteCard.getCaIdStr() + "', disconnecting...");
      close();
    } else {
      logger = ProxyLogger.getLabeledLogger(getClass().getName(), getLabel());
      if(conn != null) conn.setLogger(logger);
      logger.info("Identified card profile: " + profile);
      dumpCard(card);
      try {
        connManager.updateDecodeMaps(lastXml, name, profile.getName());
        connManager.updateAuUsers();
      } catch(ConfigException e) {
        ProxyConfig.logConfigException(logger, e);
      }
    }

    if(!skipChecks) checkCard(card);
  }

  private void dumpCard(CardData card) {
    File cardFile = new File("etc", "Cws[" + name + "].card");
    if(!card.dump(cardFile)) logger.warning("Failed to save card-data to: " + cardFile);
  }

  private void checkCard(CardData card) {

    if(card.getCaId() == 0) {
      failCheck("Ca-id of remote card is 0000, disconnecting and awaiting retry...", false);
      return;
    }
    
    // check preconfigured ca-id
    if(profile.getCaId() != 0) {
     if(profile.getCaId() != card.getCaId()) {
       failCheck("Ca-id of remote card (" + card.getCaIdStr() +
          ") does not match preconfigured ca-id for profile: " + profile + " (disabling)");
       return;
     }
    } // else no preconfigured ca-id

    // check other already connected newcamd-connectors
    CwsConnector cws;
    for(Iterator iter = connManager.getConnectors(profile.getName()).values().iterator(); iter.hasNext(); ) {
      cws = (CwsConnector)iter.next();
      if(cws == this || !(cws instanceof NewcamdCwsConnector) || !cws.isReady()) continue;
      if(cws.getRemoteCard().getParseException() != null) continue; // dont compare with failed parse

      if(!cws.getRemoteCard().getProvidersStr().equals(card.getProvidersStr())) {
        profile.setMismatchedCards(true);
        logger.warning("Cards with different provider idents detected within one profile, merging may be less " +
            "efficient ('" + name + "' doesn't match '" + cws.getName() + "')."); 
      }

      if(cws.getRemoteCard().getCaId() != card.getCaId()) {
        failCheck("Ca-id of remote card (" + card.getCaIdStr() + ") does not match already connected newcamd-connector (" +
          cws.getRemoteCard().getCaIdStr() + " (disabling)");
      }
    }
  }

  private void failCheck(String message) {
    failCheck(message, true);
  }

  private void failCheck(String message, boolean disable) {
    if(disable) {
      logger.severe(message);
      this.enabled = false;
    } else logger.warning(message);
    close();
    connManager.cwsInvalidCard(this, message);
  }

  public String getUser() {
    return user;
  }

  public boolean isConnecting() {
    return connecting;
  }

  public int getEmmCount() {
    return emmCount;
  }

  public int getKeepAliveCount() {
    return keepAliveCount;
  }

  public boolean isReady() {
    return isConnected() && remoteCard != null;
  }

  public CardData getRemoteCard() {
    return remoteCard;
  }

  public long getLastTrafficTimeStamp() {
    if(conn == null) return -1;
    else return conn.getLastTrafficTimeStamp();
  }

  public int getKeepAliveInterval() {
    return keepAliveInterval;
  }

  public boolean sendEcmRequest(CamdNetMessage request, ProxySession listener) {
    // ensure outgoing messages have the caid and provider ident in the newcamd header
    if(request.getCaId() > 0) request.setCaIdInHdr(request.getCaId());
    if(request.getProviderIdent() > 0) request.setProviderInHdr(request.getProviderIdent());
    return super.sendEcmRequest(request, listener);
  }

  public synchronized int sendMessage(CamdNetMessage msg) {
    if(conn == null || !conn.isInitialized()) return -1;    
    if(msg.isEmm()) emmCount++;
    else if(msg.isKeepAlive()) keepAliveCount++;

    if(!asynchronous && msg.isEcm())
      if(!waitForPending()) return -1;

    return conn.sendMessage(msg);
  }
 
  public void sendKeepAlive() {
    if(isConnected() && timeoutCount > 0) {
      long now = System.currentTimeMillis();
      if(now - lastEcmTimeStamp > MIN_PROBE_INTERVAL) {
        lastEcmTimeStamp = now;
        // connector has been in a timeout state for more than 3 seconds, send a keep-alive to force it to fail-fast
        if(keepAliveThread == null) {
          keepAliveThread = new Thread("CwsKeepAliveThread") {
            public void run() {
              logger.info("Connector unresponsive, forcing keep-alive...");
              int result = sendMessage(new CamdNetMessage(CamdConstants.MSG_KEEPALIVE));
              logger.fine("Result from keep-alive: " + result);
              if(result == -1) {
                close();
              } else {
                try {
                  Thread.sleep(MIN_PROBE_INTERVAL);
                } catch (InterruptedException e) {
                  logger.throwing(e);
                }
                if(timeoutCount > 0) timeoutCount++;
              }
              keepAliveThread = null;
            }
          };
          if(keepAliveThread != null) keepAliveThread.start();
        }
      }
    }
  }

  protected synchronized void connectNative() throws IOException {     
    Socket s = new Socket();
    if(qosClass != -1) s.setTrafficClass(qosClass);
    s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT);
    s.setSoTimeout(LOGIN_SO_TIMEOUT);
    conn = new NewcamdConnection(s, noEncrypt, true);
    conn.setTraceLabel(tracing?name:null);

    logger.info("Connected, authorizing...");
    emmCount = 0;
  }

  public boolean isAuAllowed(String userName) {
    return auUsers.contains(userName);
  }

  public String[] getAuUsers() {
    return (String[])auUsers.toArray(new String[auUsers.size()]);
  }

  public String getProtocol() {
    return "Newcamd";
  }

  public String getProfileName() {
    String name = super.getProfileName();
    if(name == null) return "?";
    else return name;
  }

  public String getRemoteAddress() {
    if(conn == null) return "0.0.0.0";
    else return conn.getRemoteAddress();
  }

  public void close() {
    if(conn != null) conn.close();
    conn = null;

    if(keepAliveThread != null) {
      keepAliveThread.interrupt();
      keepAliveThread = null;
    }
    
    super.close();
  }

}
