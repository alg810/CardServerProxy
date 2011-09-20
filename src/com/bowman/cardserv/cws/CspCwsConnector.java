package com.bowman.cardserv.cws;

import com.bowman.cardserv.*;
import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.web.FileFetcher;
import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.ProxyXmlConfig;

import java.io.*;
import java.util.*;
import java.net.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Jan 2, 2010
 * Time: 11:02:29 AM
 * @noinspection SynchronizeOnNonFinalField
 */
public class CspCwsConnector extends AbstractCwsConnector implements MultiCwsConnector {

  private String user, password, urlStr;
  private boolean ssl, wantCache;
  private int remoteProxyId;
  private Set unmappedIds = new HashSet();
  private Set excludeProfiles = new HashSet();
  private ProxyXmlConfig backupConfig;

  private CardData fakeCard;

  private Thread keepAliveThread = null;
  private int keepAliveInterval;
  private int keepAliveCount;

  private CspConnection conn;

  // keep track of what has been received from this connector to minimize state updates
  private Map receivedState = new HashMap();

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    super.configUpdated(xml);

    String urlStr = xml.getStringValue("url");
    String user = xml.getStringValue("user");
    String password = xml.getStringValue("password");

    boolean enabled = "true".equalsIgnoreCase(xml.getStringValue("enabled", "true"));
    boolean wantCache = "true".equalsIgnoreCase(xml.getStringValue("request-cache-updates", "false"));

    this.excludeProfiles.clear();
    String excludeProfiles = xml.getStringValue("exclude-profiles", "");
    if(excludeProfiles.length() > 0) this.excludeProfiles.addAll(Arrays.asList(excludeProfiles.split(" ")));

    this.keepAliveInterval = xml.getIntValue("keepalive-interval", config.getDefaultConnectorKeepAlive());

    asynchronous = true;
    minDelay = 0;

    boolean changed = !(urlStr.equals(this.urlStr) && user.equals(this.user) && password.equals(this.password) &&
        wantCache == this.wantCache && enabled == this.enabled);

    this.user = user;
    this.password = password;
    this.enabled = enabled;
    this.wantCache = wantCache;

    if(!enabled) close();
    else {
      try {
        URL url = new URL(urlStr);
        if("http".equalsIgnoreCase(url.getProtocol())) ssl = false;
        else if("https".equalsIgnoreCase(url.getProtocol())) ssl = true;
        else throw new ConfigException(xml.getFullName(), "url", "Protocol must be http or https: " + url.getProtocol());
        this.urlStr = urlStr;
        host = url.getHost();
        port = url.getPort();
        if(port == -1) port = ssl?443:80;
      } catch(MalformedURLException e) {
        throw new ConfigException(xml.getFullName(), "url", "Malformed URL: " + urlStr);
      }
    }

    if(changed) clearRemoteState(false);
    if(changed && enabled) {
      close();
      if(connManager != null) {
        synchronized(connManager) {
          connManager.notifyAll();
        }
      }
    }

    profile = CaProfile.MULTIPLE;
    fakeCard = CardData.createEmptyData(0);
    if(!receivedState.isEmpty()) refreshServiceMapper(true);

    String backupUrl = xml.getStringValue("url-backup", "");
    if(backupUrl.length() > 0) {
      xml.setStringOverride("url", backupUrl);
      xml.setStringOverride("url-backup", "");
      xml.setStringOverride("name", name + "-backup");
      backupConfig = xml;
    } else backupConfig = null;

    logger.fine("Configuration updated. Enabled: " + enabled + " Changed: " + changed);
  }

  private void reportRemoteState(CspNetMessage.ProfileKey key, List updates, boolean overWrite) {

    CaProfile profile = config.getProfileById(key.onid, key.caid);

    if(CspNetMessage.isDeletion(updates)) {
      logger.fine("Received delete for id " + key);
      receivedState.remove(key);
      if(profile != null) {
        connManager.reportMultiStatus(this, profile, new ServiceMapping[0], true, false); // clear candecode
        connManager.reportMultiStatus(this, profile, new ServiceMapping[0], false, false); // clear cannotdecode
      }

      return;
    }
    if(profile != null && excludeProfiles.contains(profile.getName())) profile = null;
    if(profile == null) unmappedIds.add(key);

    ServiceMapping[] added = extractServiceMap(updates, true);
    ServiceMapping[] removed = extractServiceMap(updates, false);

    if(overWrite) {
      receivedState.put(key, updates); // full state report
      if(profile != null) {
        // handle removals
        if(added.length == 0) connManager.reportMultiStatus(this, profile, new ServiceMapping[0], true, false);
        if(removed.length == 0) connManager.reportMultiStatus(this, profile, new ServiceMapping[0], false, false);
      }
    } else { // incremental, todo merge states?
      logger.fine("Incremental state updates not yet fully implemented.");
    }

    if(added.length > 0) {
      logger.fine("Received " + (overWrite?"full":"incremental") + " can-decode status update for id [" +
          key + "]: " + Arrays.asList(added));
      if(profile != null) connManager.reportMultiStatus(this, profile, added, true, !overWrite);
    }
    if(removed.length > 0) {
      logger.fine("Received " + (overWrite?"full":"incremental") + " cannot-decode status update for id [" +
          key + "]: " + Arrays.asList(removed));
      if(profile != null) connManager.reportMultiStatus(this, profile, removed, false, !overWrite);
    }

  }

  private void handleStatusUpdate(CspNetMessage msg, boolean full) throws IOException {
    // notify service mappers
    if(!msg.isEmpty()) {
      Set keys = msg.getProfileKeys(); CspNetMessage.ProfileKey key; List updates;
      for(Iterator iter = keys.iterator(); iter.hasNext(); ) {
        key = (CspNetMessage.ProfileKey)iter.next();
        updates = msg.getStatusUpdatesForKey(key);
        if(updates != null) reportRemoteState(key, updates, full);
      }
      saveRemoteState();
    }
    conn.sendCspMessage(new CspNetMessage(CspNetMessage.TYPE_STATEACK), msg.getSeqNr());
  }

  private String getCacheName() {
    return getName() + "_" + user + "@" + host + ".state";
  }

  private void saveRemoteState() {
    try {
      File mapFile = new File("cache", getCacheName());
      ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(mapFile)));
      oos.writeObject(receivedState);
      oos.flush();
      oos.close();
    } catch (IOException e) {
      logger.severe("Failed to save remote state cache: " + e);
      logger.throwing(e);
    }
  }

  private void loadRemoteState() {
    try {
      File mapFile = new File("cache", getCacheName());
      ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(mapFile)));
      receivedState = (Map)ois.readObject();
      ois.close();
      refreshServiceMapper(true);
    } catch (ClassCastException e) {
      clearRemoteState(true);
      logger.fine("Incompatible remote state cache, deleting...");
    } catch (FileNotFoundException e) {
      logger.fine("No remote state cache found");
    } catch (Exception e) {
      logger.severe("Failed to load remote state cache: " + e, e);
    }
  }

  public void clearRemoteState(boolean all) {
    receivedState.clear();
    if(all) {
      File cacheDir = new File("cache");
      String[] caches = cacheDir.list(new FilenameFilter() {
        public boolean accept(File dir, String name) {
          return name.startsWith(getName() + "_");
        }
      });
      for(int i = 0; i < caches.length; i++) {
        if(!new File("cache", caches[i]).delete()) logger.warning("Failed to delete: " + caches[i]);
      }
    }
    refreshServiceMapper(true);
  }

  private static ServiceMapping[] extractServiceMap(List state, boolean canDecode) {
    List sids = CspNetMessage.getStatusItems(CspNetMessage.STATE_SIDS, canDecode, state);
    List customs = CspNetMessage.getStatusItems(CspNetMessage.STATE_CUSTOM, canDecode, state);

    ServiceMapping[] sm = new ServiceMapping[sids.size()];
    Iterator ia = sids.iterator();
    Iterator ic = sids.size() == customs.size()?customs.iterator():null;
    for(int i = 0; i < sm.length; i++) {
      sm[i] = new ServiceMapping(((Integer)ia.next()).intValue(), ic==null?0:((Long)ic.next()).longValue());
      if(ic == null) sm[i].setProviderIdent(ServiceMapping.NO_PROVIDER);
    }
    return sm;
  }

  private void refreshServiceMapper(boolean overWrite) {
    unmappedIds.clear();
    Set profiles = config.getRealProfiles();
    // report to received state to service mapper for all profiles that exist locally, in case this has changed
    CaProfile profile; CspNetMessage.ProfileKey key; List state;
    for(Iterator iter = receivedState.keySet().iterator(); iter.hasNext(); ) {
      key = (CspNetMessage.ProfileKey)iter.next();
      profile = config.getProfileById(key.onid, key.caid);
      if(profile != null) profiles.remove(profile);
      if(profile != null && excludeProfiles.contains(profile.getName())) {
        profiles.add(profile);
        profile = null;
      }
      state = (List)receivedState.get(key);
      if(profile != null) {
        connManager.reportMultiStatus(this, profile, extractServiceMap(state, true), true, !overWrite);
        connManager.reportMultiStatus(this, profile, extractServiceMap(state, false), false, !overWrite);
      } else unmappedIds.add(key);
    }
    // no state data exist for remaning profiles, remove any mappings for this connector name
    if(connManager != null) {
      for(Iterator iter = profiles.iterator(); iter.hasNext(); ) {
        profile = (CaProfile)iter.next();
        connManager.reportMultiStatus(this, profile, new ServiceMapping[0], true, false);
        connManager.reportMultiStatus(this, profile, new ServiceMapping[0], false, false);
      }
    }
  }

  public void run() {
    alive = true;
    CspNetMessage msg;
    try {

      conn.init();

      msg = conn.readMessage(); // read initial service map states
      if(msg.getType() != CspNetMessage.TYPE_FULLSTATE) {
        logger.warning("Unexpected message on connect, aborting: " + DESUtil.byteToString((byte)msg.getType()));
        connManager.cwsConnectionFailed(this, "Connect failed");
        close();
        connecting = true;
      } else {
        remoteProxyId = msg.getOriginId();
        handleStatusUpdate(msg, true);

        if(remoteProxyId == config.getProxyOriginId()) {
          logger.warning("Remote proxy has same id as local (" + DESUtil.intToHexString(remoteProxyId, 4) + "), aborting...");
          close();
          connecting = true;
        }        
      }

      // logger.info("Remote state: " + getRemoteInfo().getProperty("remote-sids"));
      logger.info("Remote state: " + receivedState);

      if(conn == null) throw new SocketException("Connection aborted during service map sync");
      else {
        connectTimeStamp = System.currentTimeMillis();
        connManager.cwsConnected(this);
      }

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

            switch(msg.getType()) {
              case CspNetMessage.TYPE_DCWRPL:
                // ecm reply, report to listener
                if(!reportReply(msg.getCamdMessage())) logger.fine("No listener found for ECM reply: " + msg);
                break;
              case CspNetMessage.TYPE_FULLSTATE:
                handleStatusUpdate(msg, true);
                break;
              case CspNetMessage.TYPE_INCRSTATE:
                handleStatusUpdate(msg, false);
                break;
              case CspNetMessage.TYPE_STATEACK:
                logger.fine("Keep-alive reply received: [" + msg.getSeqNr() + "]");
                reportReply(new CamdNetMessage(CamdNetMessage.MSG_KEEPALIVE)); // dummy, to reset timeout states etc
                break;
              default:
                logger.warning("Unexpected message type from server: " + DESUtil.byteToString((byte)msg.getType()));
                break;
            }
          }
        }
      }

    } catch(SocketException e) {
      logger.warning("Connection closed: " + e.getMessage());
    } catch(IOException e) {
      if(e.getMessage() != null) {
        if(e.getMessage().indexOf(" 401 ") != -1) {
          logger.warning("Login failed, received: " + e.getMessage());
          connManager.cwsConnectionFailed(this, "Login failed (bad username/password)");
        } else logger.warning("Unexpected Csp reply: " + e);
      }
      logger.throwing("Exception reading/parsing message: " + e, e);
    } catch(Exception e) {
      e.printStackTrace();
    }

    conn = null;

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

    logger.info("Connector dying");
  }

  protected void connectNative() throws IOException {
    unmappedIds.clear();
    loadRemoteState();
    Socket s = ssl?FileFetcher.socketFactory.createSocket():new Socket();
    if(qosClass != -1) s.setTrafficClass(qosClass);
    s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT);
    s.setSoTimeout(LOGIN_SO_TIMEOUT);

    logger.info("Connected, authorizing...");

    String auth = user + ":" + password;
    auth = new String(com.bowman.util.Base64Encoder.encode(auth.getBytes("ISO-8859-1")));

    String CRLF = "" + (char)0x0D + (char)0x0A;
    PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), "ISO-8859-1"));
    pw.print("CONNECT /cspHandler" + CRLF);
    pw.print("Authorization: Basic " + auth + CRLF);
    pw.print("User-Agent: Csp " + CardServProxy.APP_VERSION + CRLF);
    pw.print("Proxy-Origin-Id: " + config.getProxyOriginId() + CRLF);
    pw.print("Send-Extra: true" + CRLF); // request extra meta-data added after RC6

    if(wantCache) {
      if(config.getCacheHandler() instanceof ClusteredCache)
        pw.print("Cache-port: " + ((ClusteredCache)config.getCacheHandler()).getLocalPort() + CRLF);
      else logger.warning("Configured to request cache updates, but ClusteredCache isn't in use, ignoring...");
    }

    // include hashes to indicate the known sid state of each remote profile
    CspNetMessage.ProfileKey key;
    for(Iterator iter = receivedState.keySet().iterator(); iter.hasNext(); ) {
      key = (CspNetMessage.ProfileKey)iter.next();
      pw.print("state-hash-" + key + ": " + CspNetMessage.statusHashCode((List)receivedState.get(key)) + CRLF);
    }
    
    pw.print(CRLF);
    pw.flush();

    conn = new CspConnection(s);
  }

  public boolean isConnecting() {
    return connecting;
  }

  public long getLastTrafficTimeStamp() {
    if(conn == null) return -1;
    else return conn.getLastTrafficTimeStamp();
  }

  public int getKeepAliveCount() {
    return keepAliveCount;
  }

  public int getKeepAliveInterval() {
    return keepAliveInterval;
  }

  public boolean isReady() {
    return isConnected();
  }

  public CardData getRemoteCard() {
    return fakeCard;
  }

  public int getRemoteProxyId() {
    return remoteProxyId;
  }

  public synchronized int sendMessage(CamdNetMessage camdMsg) {
    if(conn == null || !conn.isConnected()) return -1;
    CspNetMessage msg = null;
    if(camdMsg.isEcm()) {
      // if(camdMsg.getServiceId() == 0) camdMsg.setServiceId(connManager.getUnknownSid(profile.getName()));
      msg = new CspNetMessage(CspNetMessage.TYPE_ECMREQ);
      msg.setCamdMessage(camdMsg);
    } else if(camdMsg.isKeepAlive()) {
      keepAliveCount++;
      msg = new CspNetMessage(CspNetMessage.TYPE_INCRSTATE); // empty incremental state change = keep-alive
    }
    if(msg == null) return -1; // unsupported type
    try {
      return conn.sendCspMessage(msg);
    } catch (IOException e) {
      logger.warning("Connection closed");
      logger.throwing(e);
      return -1;
    }
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

  public String getProtocol() {
    return "Csp";
  }

  public String getProfileName() {
    return CaProfile.MULTIPLE.getName();
  }

  public Properties getRemoteInfo() {
    Properties p = new Properties();
    CspNetMessage.ProfileKey key; List state; CaProfile profile;
    for(Iterator iter = receivedState.keySet().iterator(); iter.hasNext(); ) {
      key = (CspNetMessage.ProfileKey)iter.next();
      profile = config.getProfileById(key.onid, key.caid);
      if(profile != null && !excludeProfiles.contains(profile.getName())) {
        state = (List)receivedState.get(key);
        p.setProperty(key + "-sids", String.valueOf(CspNetMessage.getStatusItems(CspNetMessage.STATE_SIDS, true, state).size()));
        p.setProperty(key + "-sids-cd", String.valueOf(CspNetMessage.getStatusItems(CspNetMessage.STATE_SIDS, false, state).size()));
        p.setProperty(key + "-providers", ProxyConfig.providerIdentsToString(new HashSet(CspNetMessage.getStatusItems(CspNetMessage.STATE_PROVIDERS, true, state))));
        p.setProperty(key + "-extra", CspNetMessage.getStatusItems(CspNetMessage.STATE_EXTRA, true, state).toString());
      }
    }

    p.setProperty("unmapped-networks", unmappedIds.toString());
    p.setProperty("ssl", String.valueOf(ssl));
    p.setProperty("request-cache-updates", String.valueOf(wantCache && config.getCacheHandler() instanceof ClusteredCache));
    p.setProperty("remote-origin-id", DESUtil.intToHexString(remoteProxyId, 4));
    if(urlStr != null) p.setProperty("url", urlStr);
    return p;
  }

  public boolean canDecode(CamdNetMessage request) {
    CspNetMessage.ProfileKey key = new CspNetMessage.ProfileKey(request.getNetworkId(), request.getCaId());
    List state = (List)receivedState.get(key);
    if(state == null) return false; // don't process messages without onid, or where onid isn't known remotely
    else {
      CaProfile profile = config.getProfileById(request.getNetworkId(), request.getCaId());
      if(profile == null || excludeProfiles.contains(profile.getName())) return false; // don't process messages for unbound onids
      else {        
        if(request.getOriginId() == remoteProxyId) {
          logger.finer("Blocked attempt to forward " + request.hashCodeStr() + " back to origin: " + DESUtil.intToHexString(remoteProxyId, 4));
          return false; // don't process messages that originated here
        } else {
          if(request.getProviderIdent() != -1 && profile.isRequireProviderMatch()) // check that provider exists remotely
            return CspNetMessage.getStatusItems(CspNetMessage.STATE_PROVIDERS, true, state).contains(new Integer(request.getProviderIdent()));
        }
      }
    }
    return true;
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

  public boolean hasMatchingProfile(int networkId, int caId) {
    if(networkId == 0 || caId == 0) return false;
    else if(unmappedIds.contains(new CspNetMessage.ProfileKey(networkId, caId))) return false;
    else return receivedState.containsKey(new CspNetMessage.ProfileKey(networkId, caId));
  }

  public ProxyXmlConfig getBackupConfig() {
    return backupConfig;
  }
}
