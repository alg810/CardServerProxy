package com.bowman.cardserv.session;

import com.bowman.cardserv.*;
import com.bowman.cardserv.cws.*;
import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.interfaces.*;
import com.bowman.httpd.HttpRequest;

import java.net.*;
import java.util.*;
import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: johan
 * Date: Jan 2, 2010
 * Time: 8:40:07 PM
 */
public class CspSession extends AbstractSession implements CwsListener {

  private CspConnection conn;
  private int remoteProxyId;
  private int keepAliveCount;
  private boolean sendExtra;

  // autoconfigure clustered cache if requested
  private int cachePort = -1;
  private String cacheHost;

  // keep track of sids communicated to this client, to minimize redundant status updates
  private Map stateHashes = new HashMap();
  private Map sentState = new HashMap();

  private CwsConnectorManager cm;

  public CspSession(HttpRequest req, String user, ListenPort listenPort, CamdMessageListener listener, boolean cache) {
    super(listenPort, listener);
    this.conn = new CspConnection(req.getConnection());
    this.user = ProxyConfig.getInstance().getUserManager().getUserName(user); // use stored case variations, if any
    if(this.user == null) this.user = user;
    this.loginName = user;
    this.clientId = req.getHeader("user-agent");
    this.remoteProxyId = Integer.parseInt(req.getHeader("proxy-origin-id"));
    String s = req.getHeader("cache-port");
    if(s != null && cache) {
      cachePort = Integer.parseInt(s);
      cacheHost = req.getHeader("cache-host");
    } else {
      cachePort = -1;
      cacheHost = null;
    }
    sendExtra = (req.getHeader("send-extra") != null);
    this.maxPending = 10; // todo
    this.cm = ProxyConfig.getInstance().getConnManager();

    logger.fine(req.getHeaders().toString());

    String key, hash;
    for(Iterator iter = req.getHeaders().keySet().iterator(); iter.hasNext(); ) {
      key = ((String)iter.next()).toLowerCase();
      if(key.startsWith("state-hash-")) {
        hash = req.getHeader(key);
        key = key.substring(12); // onid-caid in hex
        stateHashes.put(new CspNetMessage.ProfileKey(key), Integer.valueOf(hash));
      }
    }

    sessionThread = new Thread(this, "CspSessionThread-" + sessionId + "[" + user + "]");
    sessionThread.start();
  }

  public boolean stateChanged(CspNetMessage.ProfileKey key, List updates) {
    Integer oldHash = (Integer)stateHashes.get(key);
    int newHash = CspNetMessage.statusHashCode(updates);
    if(oldHash == null || oldHash.intValue() != newHash) {
      stateHashes.put(key, new Integer(newHash));
      return true;
    }
    logger.fine("State unchanged for: " + key + " (" + stateHashes.get(key) + ")");
    return false;
  }

  public void run() {
    logger.fine("Starting...");

    alive = true;
    CspNetMessage msg;
    ProxyConfig config = ProxyConfig.getInstance();
    SessionManager sm = SessionManager.getInstance();
    UserManager um = config.getUserManager();
    CacheHandler ch = config.getCacheHandler();
    ClusteredCache.CachePeer peer = null;
    maxSessions = 1;
    remoteAddress = conn.getRemoteAddress();

    logger.info("User '" + user + "' (" + com.bowman.cardserv.util.CustomFormatter.formatAddress(getRemoteAddress()) +
        ") login successful. Client: " + clientId + " Origin: " + DESUtil.intToHexString(remoteProxyId, 8));

    // only allow one csp connect per user
    ProxySession oldSession = sm.hasSession(user, this.getClass().getName());
    if(oldSession != null) {
      long idleMins = oldSession.getIdleTime() / 60000;
      logger.info("User '" + user + "' already has [1] csp connection, closing oldest (idle " + idleMins + " mins)");
      oldSession.close();
    }

    setupLimits(config.getUserManager());

    if(!"0.0.0.0".equals(getRemoteAddress())) {
      sm.addSession(this);
    } else {
      close();
      return;
    }

    try {
      conn.init();
      conn.setSoTimeout(config.getSessionTimeout());
      boolean checksOk;
      CamdNetMessage camdMsg;

      // send sid and profile information to client proxy, unless hashes in http header indicate they are already known
      CspNetMessage statusMsg = new CspNetMessage(CspNetMessage.TYPE_FULLSTATE);
      statusMsg.setOriginId(config.getProxyOriginId());

      Set profiles = new HashSet(config.getProfiles().values());
      CaProfile profile;
      List updates; CspNetMessage.ProfileKey key;
      for(Iterator iter = profiles.iterator(); iter.hasNext(); ) {
        profile = (CaProfile)iter.next();
        if(profile.getNetworkId() > 0) { // only present those profiles that have onid set
          key = new CspNetMessage.ProfileKey(profile);
          if(profileAllowed(profile)) { // and only if the user has access to them
            updates = CspNetMessage.buildProfileUpdate(profile, sendExtra);
            sentState.put(key, updates);
            if(stateChanged(key, updates)) statusMsg.addStatusUpdates(key, updates);
          }
        }
      }

      conn.sendCspMessage(statusMsg);
      conn.readMessage(); // ignore ack

      cm.addCwsListener(this); // listen for future changes to servicemap states

      if(ch instanceof ClusteredCache) {
         peer = ((ClusteredCache)ch).addCachePeer(this);
      } else logger.warning("Client requested cache updates but ClusteredCache isn't in use, ignoring...");

      while(alive) {
        msg = conn.readMessage();
        if(msg == null) {
          alive = false;
          logger.info("Connection closed");
        } else {

          switch(msg.getType()) {
            case CspNetMessage.TYPE_ECMREQ:
              // wrapped ecm request, handle as if newcamd
              ecmCount++;
              camdMsg = msg.getCamdMessage();
              if(um.isEnabled(user)) {
                checksOk = checkLimits(camdMsg);
                fireCamdMessage(camdMsg, false);
                if(!checksOk) {
                  setFlag(camdMsg, 'B');
                  sendEcmReply(camdMsg, camdMsg.getEmptyReply());
                }
              } else {
                logger.warning("'User '" + user + "' kicked, account disabled");
                alive = false;
                close();
              }
              break;
            case CspNetMessage.TYPE_INCRSTATE:
              if(msg.isKeepAlive()) {
                keepAliveCount++;
                logger.fine("Keep-alive received from '" + user + "' (" + clientId + "), responding...");
              } else logger.warning("Unexpected status information received from client");
              conn.sendCspMessage(new CspNetMessage(CspNetMessage.TYPE_STATEACK), msg.getSeqNr());
              break;
            case CspNetMessage.TYPE_STATEACK:
              // ignore
              break;
            default:
              logger.warning("Unexpected message type: " + DESUtil.byteToString((byte)msg.getType()));
              break;
          }

        }

      }

    } catch (EOFException e) {
      logger.info("Connection closed");      
    } catch (SocketException e) {
      logger.info(e.getMessage() + " for user '" + getUser() + "' (" + e + ")");
    } catch (Exception e) {
      logger.throwing("Unexpected exception reading/parsing message: " + e, e);
    }
    alive = false;
    if(peer != null) ((ClusteredCache)ch).removeCachePeer(peer);
    cm.removeCwsListener(this);
    endSession();
  }

  protected boolean profileAllowed(CaProfile profile) {
    Set allowed = ProxyConfig.getInstance().getUserManager().getAllowedProfiles(user);
    if(allowed == null || allowed.isEmpty() || allowed.contains(profile.getName())) {
      List connectors = ProxyConfig.getInstance().getConnManager().getReadyConnectorList(profile.getName());
      CwsConnector cws;
      for(Iterator iter = connectors.iterator(); iter.hasNext(); ) {
        cws = (CwsConnector)iter.next();
        if(cws.getProfile() != CaProfile.MULTIPLE) return true;
      }

    }
    return false;
  }

  protected int sendEcmReplyNative(CamdNetMessage ecmRequest, CamdNetMessage ecmReply) {
    CspNetMessage msg = new CspNetMessage(CspNetMessage.TYPE_DCWRPL);
    msg.setCamdMessage(ecmReply);
    int result;
    try {
      result = conn.sendCspMessage(msg, ecmRequest.getSequenceNr());
    } catch (IOException e) {
      result = -1;
      logger.throwing(e);
      conn.close();
    }
    endTransaction(ecmRequest, ecmReply, result);
    return result;
  }

  public int sendMessage(CamdNetMessage msg) {
    if(msg.isKeepAlive() && conn != null) {
      try {
        if(conn.isConnected())
          return conn.sendCspMessage(new CspNetMessage(CspNetMessage.TYPE_FULLSTATE)); // empty status req = keep-alive
      } catch (IOException e) {
        logger.throwing(e);
      }
    }
    return -1;
  }

  public String getProtocol() {
    return "Csp";
  }

  public String getLastContext() {
    return "Dummy";
  }

  public int getKeepAliveCount() {
    return keepAliveCount;
  }

  public long getIdleTime() {
    if(conn == null) return -1;
    else return System.currentTimeMillis() - conn.getLastTrafficTimeStamp();
  }

   public String getstartMsg() {
      return startMsg;
    }

  public void close() {
    if(conn != null) conn.close();
    if(sessionThread != null) sessionThread.interrupt();
  }

  public boolean isConnected() {
    return sessionThread != null;
  }

  private boolean updateProfileState(CaProfile profile) {
    if(profile.getNetworkId() == 0) return false;
    CspNetMessage.ProfileKey key = new CspNetMessage.ProfileKey(profile);
    // for now, just resend the entire sid state if it changed at all - todo
    if(profileAllowed(profile)) {
      List updates = CspNetMessage.buildProfileUpdate(profile, sendExtra);
      if(updates == null) return false; // todo - MULTIPLE
      if(stateChanged(key, updates)) {
        CspNetMessage statusMsg = new CspNetMessage(CspNetMessage.TYPE_FULLSTATE);
        statusMsg.setStatusUpdates(updates);
        try {
          conn.sendCspMessage(statusMsg);
          sentState.put(key, updates);
        } catch(IOException e) {
          logger.throwing(e);
        }
        return true;
      } else {
        return false;
      }
    }
    return false;
  }

  public void cwsConnected(CwsConnector cws) {
    if(updateProfileState(cws.getProfile())) {
      logger.fine("Connector '" + cws.getName() +"' connected/disconnected and caused change in profile state, sent full update.");
    } else {
      logger.fine("Connector '" + cws.getName() + "' connected/disconnected but no state change for profile.");
    }
  }

  public void cwsDisconnected(CwsConnector cws) {
    cwsConnected(cws);
  }

  public void cwsLostService(CwsConnector cws, TvService service, boolean show) {
    CaProfile profile = cws==null?ProxyConfig.getInstance().getProfile(service.getProfileName()):cws.getProfile();
    if(profile == null || profile.getNetworkId() == 0) return;
    // check if this service has disappeared entirely from all connectors
    ConnectorSelection conns = cm.getConnectorsForService(profile.getName(), new ServiceMapping(service), null);
    CspNetMessage.ProfileKey key = new CspNetMessage.ProfileKey(profile);
    Integer sid = new Integer(service.getId());
    List state = (List)sentState.get(key);
    List sentSids = CspNetMessage.getStatusItems(CspNetMessage.STATE_SIDS, true, state);
    if(sentSids != null && conns.isEmpty()) {
      CspNetMessage statusMsg = new CspNetMessage(CspNetMessage.TYPE_INCRSTATE);
      statusMsg.addSidUpdate(key, sid, false);
      logger.fine("Lost service caused state changed, sending incremental update...");
      try {
        conn.sendCspMessage(statusMsg);
        sentSids.add(sid);
        stateHashes.put(key, new Integer(CspNetMessage.statusHashCode(state)));
      } catch(IOException e) {
        logger.throwing(e);
      }
    }
  }

  public void cwsFoundService(CwsConnector cws, TvService service, boolean show) {
    CaProfile profile = cws==null?ProxyConfig.getInstance().getProfile(service.getProfileName()):cws.getProfile();
    if(profile == null || profile.getNetworkId() == 0) return;
    CspNetMessage.ProfileKey key = new CspNetMessage.ProfileKey(profile);
    Integer sid = new Integer(service.getId());
    List state = (List)sentState.get(key);
    List sentSids = CspNetMessage.getStatusItems(CspNetMessage.STATE_SIDS, true, state);
    // check if this is a service not previously communicated as decodable
    if(sentSids != null && !sentSids.contains(sid)) {
      CspNetMessage statusMsg = new CspNetMessage(CspNetMessage.TYPE_INCRSTATE);
      statusMsg.addSidUpdate(key, sid, true);
      logger.fine("Found service caused state change, sending incremental update...");
      try {
        conn.sendCspMessage(statusMsg);
        sentSids.add(sid);
        stateHashes.put(key, new Integer(CspNetMessage.statusHashCode(state)));
      } catch(IOException e) {
        logger.throwing(e);
      }      
    }
  }

  public void cwsConnectionFailed(CwsConnector cws, String message) {}
  public void cwsEcmTimeout(CwsConnector cws, String message, int failureCount) {}
  public void cwsInvalidCard(CwsConnector cws, String message) {}

  public void cwsProfileChanged(CaProfile profile, boolean added) {
    if(!profile.isEnabled()) { // profile removed
      CspNetMessage.ProfileKey key = new CspNetMessage.ProfileKey(profile);
      CspNetMessage statusMsg = new CspNetMessage(CspNetMessage.TYPE_FULLSTATE);
      statusMsg.addDeleteUpdate(key);
      try {
        conn.sendCspMessage(statusMsg);
        sentState.remove(key);
        stateHashes.remove(key);
      } catch(IOException e) {
        logger.throwing(e);
      }
    } else { // profile added or updated
      if(updateProfileState(profile)) {
        if(user != null) setupLimits(ProxyConfig.getInstance().getUserManager());
        logger.fine("Profile '" + profile.getName() + "' updated, sent altered state information.");
      }
    }
  }

  public int getCachePort() {
    return cachePort;
  }

  public String getCacheHost() {
    if(cacheHost == null) return getRemoteAddress();
    else return cacheHost;
  }

}
