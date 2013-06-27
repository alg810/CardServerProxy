package com.bowman.cardserv;

import com.bowman.cardserv.cws.*;
import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.rmi.*;
import com.bowman.cardserv.session.*;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.web.WebBackend;
import com.bowman.cardserv.crypto.DESUtil;

import java.io.*;
import java.net.*;
import java.rmi.RMISecurityManager;
import java.rmi.registry.*;
import java.rmi.server.RMISocketFactory;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 8, 2005
 * Time: 3:29:23 PM
 */
public class CardServProxy implements CamdMessageListener, XmlConfigurable, Runnable {

  public static final String APP_VERSION = "0.9.0";
  public static String APP_BUILD = "";
  static ProxyLogger logger;
  private static Registry registry;

  static {
    System.setProperty("java.security.policy", "etc/policy.all");
    try {
      RMISocketFactory.setSocketFactory(new IpCheckSocketFactory());
    } catch(IOException e) {
      e.printStackTrace();
    }
    try {
      Properties props = new Properties();
      props.load(CardServProxy.class.getResourceAsStream("build.properties"));
      String svnRev = props.getProperty("svn.revision");
      if(svnRev != null && svnRev.indexOf('$') == -1) APP_BUILD = "r" + svnRev;
    } catch(Exception e) {}
  }

  private ProxyConfig config;
  private CwsConnectorManager connManager;
  private UserManager userManager;
  private CacheHandler cacheHandler;
  private RemoteHandler remoteHandler;
  private WebBackend webBackend;
  private SessionManager sessionManager;
  private String remoteName;

  private long startTimeStamp;
  private boolean alive;

  private int ecmCount, ecmForwards, ecmCacheHits, ecmFailures, ecmDenied, ecmFiltered;
  private int emmCount;
  private TimedAverageList ecmRate = new TimedAverageList(10);

  private List broadcastQueue = new ArrayList();

  public CardServProxy(File cfgFile) throws FileNotFoundException, ConfigException {
    config = ProxyConfig.getInstance();
    config.readConfig(this, cfgFile);

    new Thread(this, "CwsProbeThread").start();
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {

    ProxyLogger.initFormatter(config.isDebug(), config.isHideIPs());

    if(logger != null) {
      logger.close(); // force new filehandles on updated config
    } else { // first load
      ProxyLogger.initConsole(config.getLogLevel());
    }

    try {
      logger = ProxyLogger.getFileLogger(ProxyLogger.LOG_BASE, new File(config.getLogFile()), config.getLogLevel(),
          config.getLogRotateCount(), config.getLogRotateLimit(), false);

    } catch(IOException e) {
      throw new ConfigException(xml.getFullName(), "log-file", "Unable to initialize logger FileHandler: " + e, e);
    }

    logger.setSilent(config.isSilent());

    ProxyXmlConfig rmi;
    try {
      rmi = xml.getSubConfig("rmi");
    } catch (ConfigException e) {
      rmi = null;
    }
    updateRmi(rmi);
    config.setRemoteHandler(remoteHandler);

    logger.fine("Configuration updated");
  }

  private void updateRmi(ProxyXmlConfig rmiConfig) throws ConfigException {
    if(rmiConfig != null && "true".equalsIgnoreCase(rmiConfig.getStringValue("enabled", "true"))) {

      System.setSecurityManager(new RMISecurityManager());

      try {
        IpCheckServerSocket.clearIpMasks();
        String[] allowedIps = rmiConfig.getStringValue("allowed-ip-masks").split(" ");
        for(int i = 0; i < allowedIps.length; i++) IpCheckServerSocket.addIpMask(allowedIps[i]);
      } catch (ConfigException e) {
      }

      InetAddress bindAddr = null; String bindIp = null;
      try {
        bindIp = rmiConfig.getStringValue("bind-ip");
        bindAddr = InetAddress.getByName(bindIp);
      } catch (ConfigException e) {
      } catch (UnknownHostException e) {
        throw new ConfigException(rmiConfig.getFullName(), "bind-ip", "Invalid rmi bind-ip: " + bindIp);
      }
      IpCheckServerSocket.setBindAddress(bindAddr);

      try {
        if(registry == null) registry = LocateRegistry.createRegistry(rmiConfig.getPortValue("registry-port", 4099));
        if(remoteHandler == null) remoteHandler = new RemoteHandler(rmiConfig.getPortValue("local-port", 4098), this);
        remoteHandler.setName(rmiConfig.getStringValue("display-name"));
        remoteName = rmiConfig.getStringValue("local-name", "cardservproxy");
        registry.rebind(remoteName, remoteHandler);
      } catch(IOException e) {
        throw new ConfigException(rmiConfig.getFullName(), "Unable to initialize rmi: " + e, e);
      }

      ProxyXmlConfig webConfig = rmiConfig.getSubConfig("status-web");
      if(webConfig != null) {
        if("true".equalsIgnoreCase(webConfig.getStringValue("enabled", "true"))) {
          if(webBackend == null) webBackend = new WebBackend(remoteHandler);
          webBackend.configUpdated(webConfig);
        } else {
          if(webBackend != null) webBackend.stop();
        }
      }

    } else { // disable rmi
       try {
        if(registry != null && remoteName != null) registry.unbind(remoteName);
      } catch(Exception e) {
        logger.throwing(e);
      }
      if(remoteHandler != null) remoteHandler.destroy();
      remoteHandler = null;
    }

    startPlugins();
  }

  private void startPlugins() {
    ProxyPlugin plugin;
    for(Iterator iter = config.getProxyPlugins().values().iterator(); iter.hasNext(); ) {
      plugin = (ProxyPlugin)iter.next();
      try {
        logger.info("Starting plugin: " + plugin.getName() + " - " + plugin.getDescription());
        plugin.start(this);
      } catch (Throwable t) {
        logger.severe("Exception starting plugin '" + plugin.getClass().getName() + "': " + t, t);
      }
    }
  }

  public boolean isAlive() {
    return alive;
  }

  public void setAlive(boolean alive) {
    this.alive = alive;
  }

  public void init() throws IOException, InterruptedException {

    startTimeStamp = System.currentTimeMillis();

    logger.info("-= CardServProxy " + APP_VERSION + APP_BUILD + " initialized =-");
    logger.fine("CA-Profiles: ");
    CaProfile cp;
    for(Iterator iter = config.getProfiles().values().iterator(); iter.hasNext(); ) {
      cp = (CaProfile)iter.next();
      long wait = config.getCacheHandler().getMaxCacheWait(config.getConnManager().getMaxCwWait(cp));
      logger.fine("  - " + cp + ((cp != CaProfile.MULTIPLE)?" (cache wait: " + wait + ")":""));
    }
    logger.fine("Connectors: ");
    for(Iterator iter = config.getConnManager().getConnectors().values().iterator(); iter.hasNext(); )
      logger.fine("  - " + iter.next());
    logger.fine("User-Manager: " + config.getUserManager().getClass().getName());
    logger.fine("Cache-Handler: " + config.getCacheHandler().getClass().getName());
    logger.fine("Plugins: " + config.getProxyPlugins());
    logger.fine("Services: " + config.getServiceCount() + " parsed");

    CaProfile[] profiles = openPorts();

    if(remoteHandler != null) remoteHandler.start();

    cacheHandler = config.getCacheHandler();
    cacheHandler.start();

    connManager = config.getConnManager();
    connManager.start();

    userManager = config.getUserManager();
    userManager.start();

    sessionManager = SessionManager.getInstance();

    if(webBackend != null) webBackend.start();

    logger.info("Waiting for connection manager to finish one cycle...");
    while(!connManager.isReady()) Thread.sleep(500);
    logger.info("Ready. Receiving connections...");

    startListening(profiles);

  }

  private CaProfile[] openPorts() throws IOException {
    Set set = config.getRealProfiles();
    CaProfile[] profiles = (CaProfile[])set.toArray(new CaProfile[set.size()]);
    List portList = new ArrayList();

    for(int i = 0; i < profiles.length; i++) {
      ListenPort lp = null;
      try {
        if(profiles[i].getServiceConflicts() > 0)
          logger.warning("Service conflict(s) for Ca[" + profiles[i].getName() +"]: " + profiles[i].getServiceConflicts());

        for(Iterator iter = profiles[i].getListenPorts().iterator(); iter.hasNext(); ) {
          lp = (ListenPort)iter.next();
          lp.createServerSocket();
          portList.add(lp.toString());
        }

      } catch(IOException e) {
        logger.severe("Failed to open listen port [" + lp + "] for '" + profiles[i].getName() +
            "' (" + e.getMessage() + ")", e);
        throw e;
      }
    }
    Collections.sort(portList);
    logger.info("Listening on: " + portList);
    return profiles;
  }

  private void startListening(CaProfile[] profiles) {
    setAlive(true);
    for(int i = 0; i < profiles.length; i++) {
      profiles[i].startListening(this);
    }
    config.setDefaultMsgListener(this); // register as listener for any ports added later
  }


  public static void main(String[] args) {

    if("true".equalsIgnoreCase(System.getProperty("com.bowman.cardserv.allowanyjvm"))) {
      // any jvm allowed
    } else {
      boolean start = true;
      String vendorUrl = System.getProperty("java.vendor.url");
      String vmName = System.getProperty("java.vm.name");
      if(vendorUrl != null)
        if(!vendorUrl.startsWith("http://java.sun.com")) start = false; 
        if(vendorUrl.startsWith("http://java.oracle.com/")) start = true;     // Support Oracle Sun Java Ver 1.7 
      if(vmName != null)
        if(vmName.startsWith("OpenJDK")) start = false;
      if(!start) {
        System.err.println("Startup failed: Unsupported java vm '" + System.getProperty("java.vm.name") +
            "', only the original sun vm has been tested with csp.");
        System.exit(5);
      }
    }

    File cfgFile = null;
    if(args.length > 0) cfgFile = new File(args[0]);
    try {
      new CardServProxy(cfgFile).init();
    } catch(FileNotFoundException e) {
      System.err.println("Configuration file not found: " + e.getMessage());
      System.exit(1);
    } catch(ConfigException e) {
      System.err.println("Configuration error:");
      if(e.getLabel() != null) {
        if(e.getLabel().indexOf(' ') == -1) System.err.println("- Element: <" + e.getLabel() + ">");
        else {
          System.err.println("- Element: <" + e.getLabel());
          if(e.getSubLabel() != null) System.err.println("- Attribute: " + e.getSubLabel());
        }
      }
      System.err.println("- Message: " + e.getMessage());
      System.exit(2);
    } catch(IOException e) {
      System.err.println("Failed to open listen port: " + e.getMessage());
      System.exit(3);
    } catch(InterruptedException e) {
      System.err.println("Startup aborted: " + e);
      System.exit(4);
    }
  }

  private CamdNetMessage applyFilters(ProxySession session, CamdNetMessage msg) {    
    for(Iterator iter = new ArrayList(config.getProxyPlugins().values()).iterator(); iter.hasNext(); ) {
      try {
        msg = ((ProxyPlugin)iter.next()).doFilter(session, msg);
        if(msg == null) break;
      } catch (Throwable t) {
        logger.severe("Exception in plugin filtering: " + t, t);
      }
    }

    if(msg != null && msg.getFilteredBy() != null) {
      if(config.isLogEcm() || userManager.isDebug(session.getUser())) {
        logger.info("ECM " + msg.hashCodeStr() + " (0x" + Integer.toHexString(msg.getDataLength()) + " " +
            DESUtil.intToHexString(msg.getNetworkId(), 4) + " " + DESUtil.intToHexString(msg.getCaId(), 4) + ") " +
            (msg.getProviderIdent()<0?"":"[" + DESUtil.intToByteString(msg.getProviderIdent(), 3) + "]") + " - " +
            session + "[" + config.getServiceName(msg) + "] -> " + msg.getFilteredBy());
      }
    }

    return msg;
  }

  private Set applySelectors(ProxySession session, CamdNetMessage msg, Set connectors) {
    ProxyPlugin plugin;
    for(Iterator iter = new ArrayList(config.getProxyPlugins().values()).iterator(); iter.hasNext(); ) {
      try {
        plugin = (ProxyPlugin)iter.next();
        if(plugin instanceof CwsSelector) connectors = ((CwsSelector)plugin).doSelection(session, msg, connectors);
        if(connectors == null || connectors.isEmpty()) break;
      } catch (Throwable t) {
        logger.severe("Exception in plugin connector selection: " + t, t);
      }
    }    
    return connectors;
  }

  public void messageReceived(ProxySession session, CamdNetMessage msg) {

    msg = applyFilters(session, msg);
    if(msg == null || msg.isFiltered()) {
      if(msg.isEcm()) {
        ecmFiltered++;
        ecmCount++;
      }
      return;
    }
    CaProfile profile = session.getProfile();

    if(msg.isEcm() || msg.isEmm()) {
      if(profile == CaProfile.MULTIPLE) { // deny ambigious ecms from multi-context sessions
        profile = config.getProfileById(msg.getNetworkId(), msg.getCaId());
        if(profile == null) {
          if(msg.getNetworkId() == -1) logger.warning("Denying multi-context message without network id from: " + session);
          else logger.warning("Denying csp message with unknown network-id from '" + session + "': " +
              DESUtil.intToHexString(msg.getNetworkId(), 4));
          denyMessage(session, msg);
          return;
        } else {
          if(msg.getNetworkId() == -1) msg.setNetworkId(profile.getNetworkId());
        }
      } else { // allow plugins to move messages to other profiles regardless of origin
        if(msg.getNetworkId() != profile.getNetworkId()) {
          profile = config.getProfileById(msg.getNetworkId(), msg.getCaId());
          if(profile == null) profile = session.getProfile();
        }
      }
      msg.setProfileName(profile.getName()); // just a lookup shortcut for all remaining processing
    }

    switch(msg.getCommandTag()) { 

      case EXT_GET_VERSION:
      case MSG_CLIENT_2_SERVER_LOGIN:
      case MSG_CARD_DATA_REQ:
      case MSG_KEEPALIVE:
        // handled by NewcamdSession
        break;

      case 0x80: // ECM
      case 0x81: // ECM
        ecmCount++;
        ecmRate.addRecord(1);

        Set allowed = session.getAllowedConnectors();
        if(allowed == null || allowed.isEmpty()) {
          allowed = new HashSet(connManager.getReadyConnectors(profile.getName()).keySet());
          // add multi connectors not registered with the profile
          allowed.addAll(connManager.getMultiConnectors(profile.getNetworkId(), profile.getCaId()).keySet());
        }
               
        Set modified = applySelectors(session, msg, allowed); // give plugins a chance to modify the allowed list
        logger.finer("Allowed connectors '" + modified + "' for: " + msg.hashCodeStr());
        modified = filterConnectors(msg, modified); // remove connectors that claim they cant decode this message

        if(modified.isEmpty() && !profile.isCacheOnly()) {
          logger.fine("Denying message with no connector candidates from '" + session + "': " + msg.hashCodeStr());
          denyMessage(session, msg);
          return;
        }

        ServiceMapping id = new ServiceMapping(msg);

        ConnectorSelection connectors;

        if(!profile.isCacheOnly()) {
          // request optimal connector to handle this ecm (+ any connectors with unknown status for this sid, for broadcast)
          connectors = connManager.getConnectorsForService(profile.getName(), id, modified);

          // do a faster fail if there is reason to believe this is a decoy? i.e skip cache check
          if(msg.getCustomId() != 0 && connectors.isEmpty()) {
            msg.setFilteredBy("No available connectors for cid: " + DESUtil.intToHexString(msg.getCustomId(), 4));
            session.setFlag(msg, 'B');
            session.sendEcmReply(msg, msg.getEmptyReply());
            ecmFiltered++;
            return;
          }

        } else connectors = ConnectorSelection.EMPTY;

        // this calculation is for the experimental sync-period used by the clustered-cache
        int successFactor = -1; // numerical value indicating how fast this proxy can get a cw result for this ecm
        CwsConnector cws = connectors.getPrimary();
        if(cws != null) {
          Boolean status = connManager.canDecode(cws, id);
          successFactor = cws.getEstimatedQueueTime();
          if(successFactor < 5 && successFactor != -1) {
            successFactor = -1;
          } else {
            if(cws.getTimeoutCount() > 0) successFactor = successFactor * 3; // connector might be dead, reduce chance
            if(status == null || id.serviceId == 0) successFactor = successFactor * 3; // status is unknown, reduce chance
            else if(status == Boolean.FALSE) successFactor = -1; // can't decode at all, no chance of success
          }
        }

        CamdNetMessage cached = checkCache(successFactor, msg, session, false);
        if(!session.isConnected()) return;        
        if(cached != null) {

          // cache hit, we're done
          // well except for probing...
          probeConnectors(connectors.getUnknown(), session, msg);
          session.sendEcmReply(msg, cached);

        } else {

          if(msg.isTimeOut() && cws != null) { // see if there is time left for a forward
            int qt = cws.getEstimatedQueueTime();
            if(msg.getCacheTime() + qt > connManager.getMaxCwWait(profile)) {
              // no point in continuing
              logger.warning("Cache timeout at " + msg.getCacheTime() + " ms left no time for forwarding to '" +
                  cws.getName() + "' (" + qt + " ms queue), discarding request and returning empty for: " + session +
                  " - max-cw-wait is " + connManager.getMaxCwWait(profile) + " ms");
              session.setFlag(msg, 'T');
              session.sendEcmReply(msg, msg.getEmptyReply());
              return;
            }
          }

          try {
            forwardEcmRequest(profile, session, msg, connectors);
          } catch (IllegalStateException e) { // this ecm was already pending on the chosen connector
            if(!session.isConnected()) return;

            logger.fine("Duplicate request in connector queue '" + e.getMessage()  +"' for:" + msg + " (" + session
                + ") - sleeping 200 and rechecking once...");

            try {
              Thread.sleep(200);
              cached = checkCache(successFactor, msg, session, false);
              if(cached == null) {
                cached = msg.getEmptyReply();
                session.setFlag(msg, 'H');
              }
              session.sendEcmReply(msg, cached);
            } catch(InterruptedException e1) {
              return;
            }

          }
        }

        break;

      default:
        if(msg.isEmm()) {  // 0x82 - 0x8F - only newcamd sessions send these atm
          emmCount++;
          int seqNr = msg.getSequenceNr();
          if(!profile.isCacheOnly() && msg.getProfileName() != null) forwardEmmRequest(session, msg);
          CamdNetMessage emmReplyMsg = msg.getEmmReply();
          ((NewcamdSession)session).sendMessageNative(emmReplyMsg, seqNr, true); // acknowledge the emm by giving the client the correct signature?
          ((NewcamdSession)session).fireCamdMessage(emmReplyMsg, true);
        } else {
          // unknown command?
          logger.warning("Unknown message received and ignored: " + msg + " (" + session + ")");
        }
    }

  }

  private Set filterConnectors(CamdNetMessage msg, Set modified) {

    /*
    System.out.println(msg.hashCodeStr() + " [" + DESUtil.intToHexString(msg.getServiceId(), 4) + " " +
        DESUtil.intToHexString(msg.getCaId(), 4) + " " + DESUtil.intToByteString(msg.getProviderIdent(), 3) +
        "] connectors before: " + modified);
    */

    // remove connectors that blacklist this message, or that lack the proper provider ident
    int count = modified.size();
    Set set = new HashSet();
    String cwsName; CwsConnector cws;
    for(Iterator iter = new ArrayList(modified).iterator(); iter.hasNext(); ) {
      cwsName = (String)iter.next();
      cws = connManager.getCwsConnectorByName(cwsName);
      if(cws.isBlackListed(msg)) {
        if(count > 1) { // dont blacklist if there is only one candidate
          modified.remove(cwsName);
          set.add(cwsName);
        }
      } else if(!cws.canDecode(msg)) {
        modified.remove(cwsName);
        set.add(cwsName);
      }
    }
    if(!set.isEmpty()) logger.fine("Blacklisting/filtering removed allowed connector(s) " + set +
        " for: " + msg.hashCodeStr() + " [" + DESUtil.intToHexString(msg.getServiceId(), 4) + " " +
        DESUtil.intToHexString(msg.getCaId(), 4) + " - " + DESUtil.intToByteString(msg.getProviderIdent(), 3) + "]");

    /*
    System.out.println(msg.hashCodeStr() + " [" + DESUtil.intToHexString(msg.getServiceId(), 4) + " " +
        DESUtil.intToHexString(msg.getCaId(), 4) + " " + DESUtil.intToByteString(msg.getProviderIdent(), 3) +
        "] connectors after: " + modified);

    */

    return modified;
  }

  private CamdNetMessage checkCache(int successFactor, CamdNetMessage msg, ProxySession session, boolean peek) {
    CamdNetMessage cached;
    CaProfile profile = config.getProfile(msg.getProfileName());
    if(!peek) {
      if(msg.getServiceId() == 0 && connManager.getDelayNoSid() > 0) try {
        Thread.sleep(connManager.getDelayNoSid());
      } catch(InterruptedException e) {
        session.setFlag(msg, 'U');
        logger.warning(session + " thread interrupted while in no-sid-delay, session closed?");
        return null;
      }
      cached = cacheHandler.processRequest(successFactor, msg, session.getProfile().isCacheOnly() || config.isCatchAll(), connManager.getMaxCwWait(profile));
    } else {
      cached = cacheHandler.peekReply(msg);
      if(cached != null) session.setFlag(msg, 'W');
    }
    if(cached != null) {
      ecmCacheHits++;
      if(msg.getLinkedService() != null) session.setFlag(msg, 'L');
      session.setFlag(msg, cached.getOriginAddress()==null?'C':'R');
      if(config.isLogEcm() || userManager.isDebug(session.getUser())) {
        String origin = (cached.getOriginAddress() == null)?"":" - (remote origin)";
        logger.info("ECM cache hit for   - " + session + ": " + msg.hashCodeStr() + " [" +
            config.getServiceName(msg) + "]" + origin);
      }
      sessionManager.updateUserStatus(session, msg, userManager.isDebug(session.getUser()));
    } else {
      if(msg.isTimeOut()) {
        if(msg.getCacheTime() >= cacheHandler.getMaxCacheWait(connManager.getMaxCwWait(profile))) session.setFlag(msg, 'O');
        else session.setFlag(msg, 'Q');
      }
    }
    return cached;
  }

  private void forwardEcmRequest(CaProfile profile, ProxySession session, CamdNetMessage msg, ConnectorSelection connectors) {

    CwsConnector cws = connectors.getPrimary();

    // any additional connectors returned for channel auto-discovery?
    probeConnectors(connectors.getUnknown(), session, msg);

    // any connectors selected for broadcast?
    if(connectors.getSecondary() != null) {
      session.setFlag(msg, '2');
      broadcastMessage(msg, connectors.getSecondary(), false);
    }

    if(cws != null) {
      if(config.isLogEcm() || userManager.isDebug(session.getUser()))
        logger.info("ECM " + msg.hashCodeStr() + " (0x" + Integer.toHexString(msg.getDataLength()) + " " +
            DESUtil.intToHexString(msg.getNetworkId(), 4) + " " + DESUtil.intToHexString(msg.getCaId(), 4) + ") " +
            (msg.getProviderIdent()<0?"":"[" + DESUtil.intToByteString(msg.getProviderIdent(), 3) + "]") + " - " +
            session + "[" + config.getServiceName(msg) + "] -> " + cws.getLabel());

      if(!cws.sendEcmRequest(msg, session)) {
        ecmFailures++;

        if(session.getTransactionTime() >= connManager.getMaxCwWait(profile)) { // is there time left for a retry?                 
          session.setFlag(msg, 'S');
          session.sendEcmReply(msg, msg.getEmptyReply());
          logger.warning(session + " transaction timeout in send queue, returned empty...");
        } else {
          if(!session.isConnected()) return;
          logger.warning(session + " lost card? Trying another in 100 ms...");
          session.setFlag(msg, 'Y');
          try {
            Thread.sleep(100);
            forwardEcmRequest(profile, session, msg, connManager.getConnectorsForService(profile.getName(),
                new ServiceMapping(msg), session.getAllowedConnectors())); // try again recursively while there are still connectors and time
          } catch (InterruptedException e) {
            logger.throwing(e);
          }
        }
      } else {
        ecmForwards++;        
        session.setFlag(msg, 'F');
        sessionManager.updateUserStatus(session, msg, userManager.isDebug(session.getUser()));
      }

    } else { // no connectors available to decode this ecm, no forward possible

      if(!profile.isCacheOnly() && connManager.getCannotDecodeWait() > 0) { // wait and re-check cache before failing
        try {
          Thread.sleep(connManager.getCannotDecodeWait() * 1000);
        } catch(InterruptedException e) {
          logger.throwing(e);
        }
        CamdNetMessage cached = checkCache(-1, msg, session, true);
        if(cached != null) {
          session.sendEcmReply(msg, cached);
          logger.fine("Session " + session + " received cache-reply after cannot-decode-wait for: [ " +
              config.getServiceName(profile.getName(), msg.getServiceId()) + "]");
          return;
        }

      }

      logger.fine("Session " + session + " has no available card for [" + config.getServiceName(msg) + "], sending empty...");

      cacheHandler.processReply(msg, null); // notify cache to release any other sessions waiting on this ECM
      denyMessage(session, msg);
    }
  }

  private void denyMessage(ProxySession session, CamdNetMessage msg) {
    session.setFlag(msg, 'N');
    ecmDenied++;
    sessionManager.updateUserStatus(session, msg, userManager.isDebug(session.getUser()));
    session.sendEcmReply(msg, msg.getEmptyReply());
  }

  private void forwardEmmRequest(ProxySession session, CamdNetMessage msg) {
    CwsConnector cws = connManager.getConnectorForAU(msg.getProfileName(), session.getUser());
    if(cws != null) {
      cws.sendMessage(msg); // just send the emm, ignore any reply
      if(config.isLogEmm() || userManager.isDebug(session.getUser()))
        logger.info("EMM " + msg.hashCodeStr() + " (0x" + Integer.toHexString(msg.getDataLength()) + ", 0x"
            + Integer.toHexString(msg.getUpperBits()) + ") from " + session + " -> " + cws.getName());
    } else if(config.isLogEmm() || userManager.isDebug(session.getUser()))
      logger.info("EMM " + msg.hashCodeStr() + " (0x" + Integer.toHexString(msg.getDataLength()) + ", 0x"
          + Integer.toHexString(msg.getUpperBits()) + ") from " + session);
  }

  private void probeConnectors(List candidates, ProxySession session, CamdNetMessage msg) {
    if(candidates != null) {
      if(!userManager.isMapExcluded(session.getUser())) {
        session.setFlag(msg, 'P');
        msg.setOriginAddress(session.toString()); // hack to track the origin user of the probe later
        broadcastMessage(msg, candidates, true);
      }
    }
  }

  private void broadcastMessage(CamdNetMessage msg, List connectors, boolean probe) {
    broadcastQueue.add(new BroadcastEntry(msg, connectors, probe));
    if(!broadcastQueue.isEmpty()) {
      synchronized(this) {
        notifyAll();
      }
    }
  }

  public void messageSent(ProxySession session, CamdNetMessage msg) {
    if(msg.isEcm())
      if(config.isLogEcm() || userManager.isDebug(session.getUser())) {
        if(!msg.isFiltered()) logger.info("CW  " + msg.hashCodeStr() + " (0x" + Integer.toHexString(msg.getDataLength()) + ") - [" +
            session.getLastTransactionTime() + ":" + session.getLastTransactionFlags() + "] -> " + session);
      }
    
    applyFilters(session, msg);
  }

  public int getEcmCount() {
    return ecmCount;
  }

  public int getEcmForwards() {
    return ecmForwards;
  }

  public int getEcmCacheHits() {
    return ecmCacheHits;
  }

  public int getEcmFailures() {
    return ecmFailures;
  }

  public int getEcmDenied() {
    return ecmDenied;
  }

  public int getEcmFiltered() {
    return ecmFiltered;
  }

  public int getEcmRate() {
    double rate = ((double)ecmRate.getTotal(true) / 10.0);
    return (int)Math.round(rate);
  }

  public int getEmmCount() {
    return emmCount;
  }

  public long getStartTime() {
    return startTimeStamp;
  }

  public RemoteHandler getRemoteHandler() {
    return remoteHandler;
  }

  public CacheHandler getCacheHandler() {
    return cacheHandler;
  }

  public UserManager getUserManager() {
    return userManager;
  }

  public CwsConnectorManager getConnManager() {
    return connManager;
  }

  public WebBackend getWebBackend() {
    return webBackend;
  }

  public SessionManager getSessionManager() {
    return sessionManager;
  }

  public int getProbeQueue() {
    return broadcastQueue.size();
  }

  public void run() {
    while(true) {
      synchronized(this) {
        try {
          wait();
        } catch (InterruptedException e) {
          break;
        }
      }      
      try {

        CwsConnector conn; ServiceMapping id; BroadcastEntry entry;
        for(Iterator iter = new ArrayList(broadcastQueue).iterator(); iter.hasNext(); ) {
          entry = (BroadcastEntry)iter.next();
          if(entry == null) continue;
          broadcastQueue.remove(entry);
          id = new ServiceMapping(entry.msg);

          for(Iterator i = entry.connectors.iterator(); i.hasNext();) {
            conn = (CwsConnector)i.next();
            if(conn.isReady()) {

              if(entry.probe) { // perform probing               
                if(connManager.canDecode(conn, id) == null) { // is status still unknown?
                  if(conn.getEstimatedQueueTime() < (connManager.getMaxCwWait(null) / 2)) { // only probe if there is capacity
                    if(!conn.isPending(entry.msg) && conn.canDecode(entry.msg)) try {
                      if(conn.sendEcmRequest(new CamdNetMessage(entry.msg), null))
                        if(entry.msg.getServiceId() != 0) logger.info("Probing " + conn.getLabel() + " for service [" +
                            config.getServiceName(entry.msg) + "] ...");

                    } catch (IllegalStateException e) {
                      logger.fine("Skipping probe for " + entry.msg.hashCodeStr() + ", request already in connector queue: " + conn.getName());
                    }
                  }
                }
              } else { // perform broadcast

                if(conn.getEstimatedQueueTime() < (connManager.getMaxCwWait(null) / 2)) { // only broadcast if there is capacity - todo change criteria?
                  if(!conn.isPending(entry.msg) && conn.canDecode(entry.msg)) try {
                    if(conn.sendEcmRequest(new CamdNetMessage(entry.msg), null)) {
                      if(!connManager.isServiceUnknown(entry.msg.getProfileName(), entry.msg.getServiceId()))
                        logger.fine("Redundant forward to " + conn.getLabel() + " for service [" + config.getServiceName(entry.msg) + "] ...");
                      else logger.fine("Broadcasting " + entry.msg.hashCodeStr() + " with unknown service to " + conn.getLabel() + "...");
                    }

                  } catch (IllegalStateException e) {
                    logger.fine("Skipping broadcast for " + entry.msg.hashCodeStr() + ", request already in connector queue: " + conn.getName());
                  }
                }

              }
            }
          }

        }

        if(!alive) break;

      } catch (Exception e) {
        logger.severe("Uncaught exception in broadcast loop: " + e, e);
      }
    }
  }

  static class BroadcastEntry {

    CamdNetMessage msg;
    List connectors;
    boolean probe;

    BroadcastEntry(CamdNetMessage msg, List connectors, boolean probe) {
      this.msg = msg;
      this.connectors = connectors;
      this.probe = probe;
    }
  }

}
