package com.bowman.cardserv.cws;

import com.bowman.cardserv.*;
import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.session.*;
import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.web.*;
import com.bowman.util.*;
import com.bowman.xml.*;

import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 15, 2005
 * Time: 2:42:10 PM
 */
public class CwsConnectorManager implements XmlConfigurable, Runnable, CronTimerListener, CwsListener {
  private static final long DEFAULT_RECONNECT_INTERVAL = 60 * 1000;

  private Map connectors = new HashMap();
  private Map externalConnectors = new HashMap();
  private Map multiConnectors = new HashMap();
  private Map serviceMappers = new HashMap();
  private Map auUsers = new HashMap();

  private ProxyLogger logger;
  private ProxyConfig config;
  private List listeners = new ArrayList();

  private CronTimer excludeCron, rediscoverCron;
  private Thread keepAliveThread, connectorThread;

  private long reconnectInterval, maxCwWait, cannotDecodeWait, congestionLimit, timeoutThreshold, delayNoSid;
  private boolean ready, hardLimit, logSidMismatch;

  private URL connectorFileUri;
  private String connectorFile, connectorFileKey;
  private int connectorFileInterval;
  private long connectorFileLastModified, connectorFileLastCheck;
  private CtrlCommand updateCommand;

  public CwsConnectorManager() {
    logger = ProxyLogger.getLabeledLogger(getClass().getName());
    config = ProxyConfig.getInstance();
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    reconnectInterval = xml.getTimeValue("reconnect-interval", 60, "s");

    maxCwWait = xml.getTimeValue("max-cw-wait", 9, "s");
    if(maxCwWait <= 100) throw new ConfigException(xml.getFullName(), "max-cw-wait must be > 100 ms");

    timeoutThreshold = xml.getIntValue("timeout-disconnect-threshold", 2);
    if(timeoutThreshold < 1) throw new ConfigException(xml.getFullName(), "timeout-disconnect-threshold must be >= 1");
    if(timeoutThreshold != 2)
      logger.warning("timeout-disconnect-threshold has been changed: " + timeoutThreshold + " (default 2)");

    cannotDecodeWait = xml.getTimeValue("cannot-decode-wait", 0, "s");
 
    if(cannotDecodeWait >= maxCwWait)
      throw new ConfigException(xml.getFullName(), "cannot-decode-wait must be smaller than max-cw-wait");
    if(cannotDecodeWait > 0) logger.warning("cannot-decode-wait period is enabled: " + cannotDecodeWait + " ms");

    congestionLimit = xml.getTimeValue("congestion-limit", (int)maxCwWait / 1000, "s");
    if(maxCwWait < 1000 && congestionLimit == 0) congestionLimit = maxCwWait;
    if(congestionLimit > maxCwWait || congestionLimit < maxCwWait / 2)
      throw new ConfigException(xml.getFullName(), "congestion-limit must be between max-cw-wait/2 and max-cw-wait");

    hardLimit = "true".equalsIgnoreCase(xml.getStringValue("hard-congestion-limit", "true"));
    logSidMismatch = "true".equalsIgnoreCase(xml.getStringValue("log-sid-mismatch", "true"));
    delayNoSid = xml.getTimeValue("delay-missing-sid", 100, "ms");
    if(delayNoSid >= congestionLimit)
      throw new ConfigException(xml.getFullName(), "delay-missing-sid must be < congestion-limit (max-cw-wait)");

    if(reconnectInterval < 3000) {
      this.reconnectInterval = DEFAULT_RECONNECT_INTERVAL;
      logger.warning("reconnect-interval must be at least 3 seconds, using default (60 s)");
    }

    updateServiceMappers(xml.getSubConfig("service-map"));

    ProxyXmlConfig connCfg = null;
    try {
      connCfg = xml.getSubConfig("cws-connectors");
    } catch (ConfigException e) {}
    updateConnectors(connCfg, connectors);

    ProxyXmlConfig extCfg = null;
    try {
      extCfg = xml.getSubConfig("external-connector-config");
    } catch (ConfigException e) {}

    if(extCfg == null && connCfg == null) xml.getSubConfig("cws-connectors"); // throw exception

    if(extCfg != null) updateExternalConfig(extCfg);
    else {
      connectorFileUri = null;
      connectorFileInterval = 0;

      if(updateCommand != null) updateCommand.unregister();
      updateAuUsers();
    }

    if(excludeCron == null) {
      excludeCron = new CronTimer("0 * * * *");
      excludeCron.addTimerListener(this);
      excludeCron.start();
    }

    if(rediscoverCron == null) {
      rediscoverCron = new CronTimer("* * * * *");
      rediscoverCron.addTimerListener(this);
      rediscoverCron.start();
    }

    logger.fine("Configuration updated");
  }

  public boolean setTempAuUser(String name, String user) {
    if(!config.getUserManager().exists(user)) return false;
    CwsConnector cws = this.getCwsConnectorByName(name);
    if(cws != null) {
      CardData card = cws.getRemoteCard();
      if(card == null && card.isAnonymous()) return false;
      CaProfile profile = cws.getProfile();
      if(profile != CaProfile.MULTIPLE && profile != null && cws.isEnabled()) {
        auUsers.put(user + ":" + profile.getName(), cws.getName());
        SessionManager sm = SessionManager.getInstance();
        List sessions = sm.getSessionsForUser(user);
        ProxySession session;
        if(sessions != null && !sessions.isEmpty()) {
          for(Iterator iter = sessions.iterator(); iter.hasNext();) {
            session = (ProxySession)iter.next();
            if(profile.getName().equals(session.getProfileName()) || session.getProfile() == CaProfile.MULTIPLE)
              if(session instanceof NewcamdSession && !session.getLastContext().equals(card.toString())) {
                  logger.info("AU changed for '" + user + ":" + profile.getName() +
                      "', kicking existing session to force reconnect and card-data update: " + session);
                  session.close();
              }
          }
        }
        return true;
      }
    }
    return false;
  }

  protected void updateAuUsers() throws ConfigException {
    auUsers.clear();
    CwsConnector cws; String[] names; String name;
    for(Iterator iter = getConnectors().values().iterator(); iter.hasNext(); ) {
      cws = (CwsConnector)iter.next();
      if(cws.getProfile() == CaProfile.MULTIPLE || cws.getProfile() == null || !cws.isEnabled()) continue;
      names = cws.getAuUsers();
      if(names != null) {
        for(int i = 0; i < names.length; i++) {
          name = (String)auUsers.put(names[i]+ ":" + cws.getProfileName(), cws.getName());
          if(name != null) throw new ConfigException("AU-user '" + names[i] + "' assigned for multiple connectors in " +
              "the same profile: " + cws.getName() + ", " + name);
        }
      }
    }
    if(!auUsers.isEmpty()) { // kick any sessions who received new au permissions as a result of this update
      SessionManager sm = SessionManager.getInstance();
      String key, card; String[] userProfile; List sessions; ProxySession session;
      for(Iterator iter = auUsers.keySet().iterator(); iter.hasNext(); ) {
        key = ((String)iter.next());
        userProfile = key.split(":");
        sessions = sm.getSessionsForUser(userProfile[0]);
        if(sessions != null && !sessions.isEmpty()) {
          for(Iterator i = sessions.iterator(); i.hasNext();) {
            session = (ProxySession)i.next();
            if(userProfile[1].equals(session.getProfileName())) {
              cws = getCwsConnectorByName((String)auUsers.get(key));
              if(cws != null) {
                card = "" + cws.getRemoteCard();
                if(session instanceof NewcamdSession && !session.getLastContext().equals(card)) {
                  logger.info("AU changed for '" + key + "', kicking existing session to force reconnect and card-data update: " + session);
                  session.close();
                }
              }
            }

          }
        }
      }

    }
  }

  private void updateServiceMappers(ProxyXmlConfig xml) throws ConfigException {
    Iterator iter = xml.getMultipleSubConfigs("mapper");
    Map mapperConfs = new HashMap();
    ProxyXmlConfig conf; String profileName;
    ProxyXmlConfig defaults = null;
    while(iter.hasNext()) {
      conf = (ProxyXmlConfig)iter.next();
      try {
        profileName = conf.getStringValue("profile");
        mapperConfs.put(profileName, conf);
      } catch (ConfigException e) {
        if(defaults != null) throw e; // must be only 1 set of defaults, the rest must have profile name
        defaults = conf;
      }
    }
    if(defaults == null)
      throw new ConfigException(xml.getFullName(), "No default mapper element found (omit profile attribute).");

    iter = config.getProfiles().values().iterator();
    CaProfile profile; CwsServiceMapper mapper;
    while(iter.hasNext()) {
      profile = (CaProfile)iter.next();
      mapper = getServiceMapper(profile.getName());
      if(mapper == null) mapper = new CwsServiceMapper(profile, this);
      conf = (ProxyXmlConfig)mapperConfs.remove(profile.getName());
      defaults.setOverrides(conf);
      mapper.configUpdated(defaults);
      serviceMappers.put(profile.getName(), mapper);
    }
    if(!mapperConfs.isEmpty()) logger.warning("Mapper configs for unknown profile(s) ignored: " + mapperConfs.keySet());
  }

  private void updateConnectors(ProxyXmlConfig xml, Map connectors) throws ConfigException {
    Iterator iter = xml == null?Collections.EMPTY_LIST.iterator():xml.getMultipleSubConfigs(null);
    Set names = new HashSet();
    ProxyXmlConfig conf;
    String name;
    CwsConnector conn;
    while(iter.hasNext()) {
      conf = (ProxyXmlConfig)iter.next();
      addConnector(conf, names, connectors);
    }
    iter = new ArrayList(connectors.keySet()).iterator();
    while(iter.hasNext()) { // remove connectors no longer in conf
      name = (String)iter.next();
      if(!names.contains(name)) {
        conn = (CwsConnector)connectors.get(name);
        removeConnector(conn);
        connectors.remove(name);
      }
    }
  }

  private void addConnector(ProxyXmlConfig conf, Set names, Map connectors) throws ConfigException {
    String name = conf.getStringValue("name");
    if(names.contains(name)) throw new ConfigException(conf.getFullName(), "Duplicate connector definition: " + name);
    names.add(name);

    CwsConnector conn;
    if(connectors.containsKey(name)) {
      conn = (CwsConnector)connectors.get(name); // reconfigure existing
    } else { // add new
      if(conf.getName().startsWith("newcamd")) conn = new NewcamdCwsConnector();
      else if(conf.getName().startsWith("radegast")) conn = new RadegastCwsConnector();
      else if(conf.getName().startsWith("chameleon")) conn = new ChameleonCwsConnector();
      else if(conf.getName().startsWith("csp")) conn = new CspCwsConnector();
      else {
        String className = conf.getStringValue("class", "");
        if(!"".equals(className)) conn = (CwsConnector)ProxyConfig.loadInstance(conf, null, CwsConnector.class);
        else throw new ConfigException(conf.getFullName(), "Unknown connector type (and no class/jar-file specified): "
            + conf.getFullName());
      }
      connectors.put(name, conn);
      logger.fine("Added connector: " + name);
    }

    conn.configUpdated(conf);
    if("true".equalsIgnoreCase(conf.getStringValue("enabled", "true"))) {
      updateDecodeMaps(conf, name, conn.getProfileName());
    }
    if(conn.getProfile() != null) getServiceMapper(conn.getProfileName()).addConnector(conn);

    if(conn instanceof CspCwsConnector) {
      ProxyXmlConfig backupConf = ((CspCwsConnector)conn).getBackupConfig();
      if(backupConf != null) addConnector(backupConf, names, connectors);
    }
  }

  void updateDecodeMaps(ProxyXmlConfig xml, String name, String profileName) throws ConfigException {
    CwsServiceMapper mapper;
    if(CaProfile.MULTIPLE.getName().equals(profileName)) {
      mapper = null;
      for(Iterator i = new ArrayList(serviceMappers.values()).iterator(); i.hasNext(); )
        ((CwsServiceMapper)i.next()).exclusiveConnectors.remove(name);
    } else {
      mapper = getServiceMapper(profileName);
      if(mapper == null) return;
      else mapper.exclusiveConnectors.remove(name);
    }

    boolean exclusive; String profile; ProxyXmlConfig cds; String canDecodeList, cannotDecodeList;
    Iterator iter = xml.getMultipleSubConfigs("can-decode-services");
    if(iter != null) {
      while(iter.hasNext()) {
        cds = (ProxyXmlConfig)iter.next();
        canDecodeList = cds.getContents();
        if(canDecodeList != null) {
          exclusive = "true".equalsIgnoreCase(cds.getStringValue("exclusive", "false"));
          if(mapper == null) { // multi context connector, profile must be specified per can-decode-services element
            profile = cds.getStringValue("profile");
            mapper = getServiceMapper(profile);
            if(mapper == null)
              throw new ConfigException(cds.getFullName(), "Unknown/disabled profile for can-decode-services: " + profile);
          } else {
            if(cds.getStringValue("profile", "").length() > 0)
              throw new ConfigException(cds.getFullName(), "Profile not allowed for can-decode-services for this connector type.");
          }
          storeDecodeState(name, ProxyConfig.getServiceTokens("can-decode-services", canDecodeList, false), mapper.overrideCanDecodeMap);
          if(exclusive) mapper.exclusiveConnectors.add(name);
          else mapper.exclusiveConnectors.remove(name);
        }
      }
    }

    iter = xml.getMultipleSubConfigs("cannot-decode-services");
    if(iter != null) {
      while(iter.hasNext()) {
        cds = (ProxyXmlConfig)iter.next();
        cannotDecodeList = cds.getContents();
        if(cannotDecodeList != null) {
          if(mapper == null) { // multi context connector, profile must be specified per cannot-decode-services element
            profile = cds.getStringValue("profile");
            mapper = getServiceMapper(profile);
            if(mapper == null)
              throw new ConfigException(cds.getFullName(), "Unknown/disabled profile for cannot-decode-services: " + profile);
          } else {
            if(cds.getStringValue("profile", "").length() > 0)
              throw new ConfigException(cds.getFullName(), "Profile not allowed for cannot-decode-services for this connector type.");            
          }
          storeDecodeState(name, ProxyConfig.getServiceTokens("cannot-decode-services", cannotDecodeList, false), mapper.overrideCannotDecodeMap);
        }
      }
    }

  }


  private void storeDecodeState(String name, Set sids, Map map) {
    ServiceMapping sm; List names;
    for(Iterator iter = map.keySet().iterator(); iter.hasNext(); ) {
      sm = (ServiceMapping)iter.next();
      names = (List)map.get(sm);
      if(names != null) names.remove(name);
    }
    for(Iterator iter = sids.iterator(); iter.hasNext(); ) {
      sm = (ServiceMapping)iter.next();
      names = (List)map.get(sm);
      if(names == null) {
        names = new ArrayList();
        map.put(sm, names);
      }
      if(!names.contains(name)) names.add(name);
    }
  }

  private void removeConnector(CwsConnector conn) {
    CwsServiceMapper mapper = getServiceMapper(conn.getProfileName());
    conn.setEnabled(false);
    conn.close();
    if(mapper != null) mapper.removeConnector(conn);
    logger.fine("Removed connector: " + conn.getName());
  }

  private void updateExternalConfig(ProxyXmlConfig xml) throws ConfigException {
    boolean enabled = true;
    try {
      enabled = "true".equalsIgnoreCase(xml.getStringValue("enabled"));
    } catch (ConfigException e) {
    }
    if(enabled) {
      String url = xml.getStringValue("connector-file-url");
      try {
        connectorFileUri = new URL(url);
      } catch(MalformedURLException e) {
        throw new ConfigException(xml.getFullName(), "connector-file-url", "Malformed URL: " + e.getMessage());
      }
      try {
        connectorFileKey = xml.getStringValue("connector-file-key");
      } catch (ConfigException e) {
        connectorFileKey = null;
      }
      connectorFileInterval = xml.getTimeValue("update-interval", "m");

      try {
        updateCommand = new CtrlCommand("update-connectors", "Run update", "Fetch/install external connector file now (" + url + ").");
        updateCommand.register(this);
      } catch (NoSuchMethodException e) {
      }

      if(!fetchConnectorFile()) {
        // file unchanged since last fetch, re-process existing anyway to update manual decode maps per connector and au-users
        if(connectorFile != null) {
          processConnectorFile(connectorFile);
          updateAuUsers();
        }
      }

    } else { // disabled

      if(!externalConnectors.isEmpty()) {
        logger.info("Closing external connectors...");
        CwsConnector conn;
        for(Iterator iter = externalConnectors.keySet().iterator(); iter.hasNext(); ) {
          conn = (CwsConnector)externalConnectors.get(iter.next());
          removeConnector(conn);
        }
      }
      externalConnectors.clear();
      connectorFileUri = null;
      connectorFileInterval = 0;

      if(updateCommand != null) {
        updateCommand.unregister();
        updateCommand = null;
      }
      updateAuUsers();
    }
  }

  public CtrlCommandResult runCtrlCmdUpdateConnectors() {
    try {
      fetchConnectorFile();
    } catch(ConfigException e) {
      return new CtrlCommandResult(false, e.getMessage());
    }
    return new CtrlCommandResult(true, "Updated executed.");
  }

  public void start() {
    Runtime.getRuntime().addShutdownHook(new ExitCacheSaveThread());
    connectorThread = new Thread(this, "CwsConnectionManagerThread");
    connectorThread.start();
    keepAliveThread = new Thread(this, "CwsKeepAliveThread");
    keepAliveThread.start();
  }

  public void run() {
    if(Thread.currentThread() == connectorThread) doConnectLoop();
    else if(Thread.currentThread() == keepAliveThread) doKeepAliveLoop();
  }

  private void doConnectLoop() {
    logger.info("Starting...");

    for(Iterator iter = serviceMappers.values().iterator(); iter.hasNext(); )
      ((CwsServiceMapper)iter.next()).loadServiceMaps();

    CwsConnector cws = null;
    boolean alive = true;

    int count;
    while(alive) {
      count = 0;
      for(Iterator iter = getConnectors().values().iterator(); iter.hasNext(); ) {
        try {
          cws = (CwsConnector)iter.next();
          if(!cws.isConnected()) {
            if(System.currentTimeMillis() - cws.getLastAttemptTimeStamp() > reconnectInterval)
              if(cws.connect(this)) count++;
          }
        } catch(IOException e) {
          logger.warning("Failed to connect to: " + cws + " (" + e + ")");
          if("Unrecognized option".equals(e.getMessage())) // jamvm hint
            logger.warning("Try adding this attribute to all newcamd connectors: qos-class=\"None\"");
          logger.throwing(e);
        }
      }
      if(count != 0) logger.info("Connected to [" + count + "] CWS/Cards");

      ready = true;

      synchronized(this) {
        try {
          wait(1000);
        } catch(InterruptedException e) {
          alive = false;
        }
      }
    }
  }


  public boolean isReady() {
    return ready;
  }

  public boolean isLogSidMismatch() {
    return logSidMismatch;
  }

  private void doKeepAliveLoop() {
    boolean alive = true;
    CwsConnector cws;

    while(alive) {
      for(Iterator iter = getConnectors().values().iterator(); iter.hasNext(); ) {
        cws = (CwsConnector)iter.next();
        if(cws.isConnected() && cws.getKeepAliveInterval() > 0) {
          if((System.currentTimeMillis() - cws.getLastTrafficTimeStamp()) > cws.getKeepAliveInterval()) {
            cws.reset();
            cws.sendMessage(new CamdNetMessage(CamdConstants.MSG_KEEPALIVE));
            logger.fine("Keep-alive sent to " + cws);
          }
        }
      }
      synchronized(this) {
        try {
          wait(1000);
        } catch(InterruptedException e) {
          alive = false;
        }
      }
    }
  }

  public long getNextConnectAttempt(String name) {
    long lastAttemptTimeStamp = getCwsConnectorByName(name).getLastAttemptTimeStamp();
    if(lastAttemptTimeStamp == 0) return -1;
    else return lastAttemptTimeStamp + reconnectInterval;
  }

  public CwsServiceMapper getServiceMapper(String profile) {
    return (CwsServiceMapper)serviceMappers.get(profile);
  }

  public CwsConnector getCwsConnectorByName(String name) {
    if(name == null) return null;
    CwsConnector conn = (CwsConnector)connectors.get(name);
    if(conn == null) conn = (CwsConnector)externalConnectors.get(name);
    return conn;
  }

  public CwsConnector getCwsConnector(String profile) {
    return getServiceMapper(profile).getCwsConnector();
  }

  public Map getConnectors() {
    Map allConnectors = new HashMap();
    allConnectors.putAll(connectors);
    allConnectors.putAll(externalConnectors);
    return allConnectors;
  }

  public Map getMultiConnectors(int networkId, int caId) {
    Map map = new HashMap(); CwsConnector cws;
    for(Iterator iter = multiConnectors.values().iterator(); iter.hasNext(); ) {
      cws = (CwsConnector)iter.next();
      if(cws.isReady())
        if(((MultiCwsConnector)cws).hasMatchingProfile(networkId, caId)) map.put(cws.getName(), cws);
    }
    return map;
  }

  public Map getConnectors(String profile) {
    return getServiceMapper(profile).getConnectors();
  }

  public Map getReadyConnectors(String profile) {
    Map map = new HashMap();
    CwsConnector cws;
    for(Iterator iter = getReadyConnectorList(profile).iterator(); iter.hasNext(); ) {
      cws = (CwsConnector)iter.next();
      map.put(cws.getName(), cws);
    }
    return map;
  }

  public List getReadyConnectorList(String profile) {
    CwsServiceMapper mapper = getServiceMapper(profile);
    if(mapper != null) return mapper.getReadyConnectors();
    else {
      logger.fine("Request for non-existing mapper: " + profile);
      logger.throwing(new Throwable());
      return new ArrayList();
    }
  }

  public Set getMergedProviders(String profile) {
    List connectors = getReadyConnectorList(profile);
    if(connectors == null) return null;
    else {
      Set providers = new TreeSet();
      for(Iterator iter = connectors.iterator(); iter.hasNext(); ) {
        providers.addAll(((CwsConnector)iter.next()).getProviderIdents());
      }
      return providers;
    }
  }

  public CwsConnector getConnectorForAU(String profile, String user) {    
    String conn = (String)auUsers.get(user + ":" + profile);
    if(conn == null) return null;
    CwsConnector cws = getCwsConnectorByName(conn);
    if(cws != null && cws.isReady()) return cws;
    else return null;
  }

  public CwsConnector getAUCardDataConnector(String profile, String user) {
    CwsConnector cws = getConnectorForAU(profile, user);
    if(cws != null && cws.getRemoteCard() != null) return cws;
    else return null;
  }

  public long getMaxCwWait(CaProfile profile) {
    if(profile == null || CaProfile.MULTIPLE == profile) return maxCwWait;
    else {
      long mcw = profile.getMaxCwWait();
      return mcw == -1 ? maxCwWait : mcw; 
    }
  }

  public long getTimeoutThreshold() {
    return timeoutThreshold;
  }

  public long getCannotDecodeWait() {
    return cannotDecodeWait;
  }

  public long getCongestionLimit(CaProfile profile) {
    if(profile == null || CaProfile.MULTIPLE == profile) return congestionLimit;
    else {
      long cl = profile.getCongestionLimit();
      return cl == -1 ? congestionLimit : cl;
    }
  }

  public boolean isHardLimit() {
    return hardLimit;
  }

  public int getFailureCount(String profile, ServiceMapping id) {
    return getServiceMapper(profile).getFailures(id);
  }

  public int getUnknownSid(String profile) {
    return getServiceMapper(profile).getUnknownSid();
  }

  public boolean isServiceUnknown(String profile, int sid) {
    return getServiceMapper(profile).isServiceUnknown(sid);
  }

  public long getDelayNoSid() {
    return delayNoSid;
  }

  public void reportChannelStatus(CwsConnector cws, CamdNetMessage msg, boolean decodeSuccess, ProxySession session) {
    int serviceId = msg.getServiceId();
    if(serviceId == 0 || isServiceUnknown(cws.getProfileName(), serviceId))
      if(msg.getCustomId() <= 0) return;
    CaProfile profile = config.getProfileById(msg.getNetworkId(), msg.getCaId());
    if(profile == null) {
      if(cws.getProfile() == CaProfile.MULTIPLE) profile = session.getProfile();
      else profile = cws.getProfile();
    }
    CwsServiceMapper mapper = getServiceMapper(profile.getName());
    if(decodeSuccess) {
      mapper.setCanDecode(msg, cws, session);
    } else mapper.setCannotDecode(msg, cws, session);
    mapper.saveServiceMaps();
  }

  public void reportMultiStatus(CwsConnector cws, CaProfile profile, ServiceMapping[] sids, boolean success, boolean merge) {
    CwsServiceMapper mapper = getServiceMapper(profile.getName());
    mapper.setMultiStatus(sids, cws, success, merge);
  }

  public List getServicesForConnector(String cwsName, boolean canDecode, boolean raw) {
    CwsConnector cws = getCwsConnectorByName(cwsName);
    if(cws == null || "?".equals(cws.getProfileName())) return new ArrayList();
    else if(cws.getProfile() == CaProfile.MULTIPLE) {
      if(raw) return new ArrayList(); // should not happen, no way to include profile context in plain sid list
      Set allServices = new TreeSet();
      CaProfile profile;
      for(Iterator iter = config.getRealProfiles().iterator(); iter.hasNext(); ) {
        profile = (CaProfile)iter.next();
        allServices.addAll(getServiceMapper(profile.getName()).getServicesForConnector(cws.getName(), canDecode, false));
      }
      return new ArrayList(allServices);
    } else return getServiceMapper(cws.getProfileName()).getServicesForConnector(cwsName, canDecode, raw);
  }

  public ServiceMapping[] getServicesForProfile(String profileName, boolean canDecode) {
    Set sids = new TreeSet();
    List readyConnectors = getReadyConnectorList(profileName);
    CwsConnector conn;
    for(Iterator iter = readyConnectors.iterator(); iter.hasNext(); ) {
      conn = (CwsConnector)iter.next();
      sids.addAll(getServicesForConnector(conn.getName(), canDecode, true));
    }
    // only return those known not to decode on _any_ card, if cannot decode requested
    if(!canDecode) sids.removeAll(Arrays.asList(getServicesForProfile(profileName, true)));
    // add those provided by sid cache linker
    if(config.getSidCacheLinker() != null) {
      if(canDecode) sids.addAll(config.getSidCacheLinker().getServicesForProfile(profileName));
      else sids.removeAll(config.getSidCacheLinker().getServicesForProfile(profileName));
    }

    return (ServiceMapping[])sids.toArray(new ServiceMapping[sids.size()]);
  }

  public Boolean canDecode(CwsConnector cws, ServiceMapping id) {
    return getServiceMapper(cws.getProfileName()).canDecode(cws, id);
  }

  public ConnectorSelection getConnectorsForService(String profile, ServiceMapping id, Set allowedConnectorNames) {
    return getServiceMapper(profile).getConnectorsForService(id, allowedConnectorNames);
  }

  public List getConnectorsForProvider(String profile, String provider) {
    return getServiceMapper(profile).getConnectorsForProvider(provider);
  }

  public void timeout(CronTimer cronTimer) {
    if(cronTimer == excludeCron) resetExcludedStatus();
    else if(cronTimer == rediscoverCron) {
      resetLostStatus();
      
      if(connectorFileInterval > 0)
        if(System.currentTimeMillis() - connectorFileLastCheck > connectorFileInterval) {
          new Thread("ConnectorFileFetchThread") {
            public void run() {
              try {
                fetchConnectorFile();
              } catch(ConfigException e) {
                ProxyConfig.logConfigException(logger, e);
              }
            }
          }.start();
        }
    }
  }

  private void resetLostStatus() {
    for(Iterator iter = serviceMappers.values().iterator(); iter.hasNext(); )
      ((CwsServiceMapper)iter.next()).resetLostStatus();
  }

  private void resetExcludedStatus() {
    for(Iterator iter = serviceMappers.values().iterator(); iter.hasNext(); )
      ((CwsServiceMapper)iter.next()).resetListedServices();
  }

  public void cwsConnected(CwsConnector cws) {
    getServiceMapper(cws.getProfileName()).addConnector(cws);
    if(cws instanceof MultiCwsConnector) multiConnectors.put(cws.getName(), cws);
    for(Iterator iter = new ArrayList(listeners).iterator(); iter.hasNext(); )
      ((CwsListener)iter.next()).cwsConnected(cws);
  }

  public void cwsDisconnected(CwsConnector cws) {
    if(cws instanceof MultiCwsConnector) multiConnectors.remove(cws.getName());
    for(Iterator iter = new ArrayList(listeners).iterator(); iter.hasNext(); )
      ((CwsListener)iter.next()).cwsDisconnected(cws);
  }

  public void cwsConnectionFailed(CwsConnector cws, String message) {
    for(Iterator iter = new ArrayList(listeners).iterator(); iter.hasNext(); )
      ((CwsListener)iter.next()).cwsConnectionFailed(cws, message);
  }

  public void cwsEcmTimeout(CwsConnector cws, String message, int failureCount) {
    for(Iterator iter = new ArrayList(listeners).iterator(); iter.hasNext(); )
      ((CwsListener)iter.next()).cwsEcmTimeout(cws, message, failureCount);
  }

  public void cwsLostService(CwsConnector cws, TvService service, boolean show) {
    for(Iterator iter = new ArrayList(listeners).iterator(); iter.hasNext(); )
      ((CwsListener)iter.next()).cwsLostService(cws, service, show);
  }

  public void cwsFoundService(CwsConnector cws, TvService service, boolean show) {
    for(Iterator iter = new ArrayList(listeners).iterator(); iter.hasNext(); )
      ((CwsListener)iter.next()).cwsFoundService(cws, service, show);
  }

  public void cwsInvalidCard(CwsConnector cws, String message) {
    for(Iterator iter = new ArrayList(listeners).iterator(); iter.hasNext(); )
      ((CwsListener)iter.next()).cwsInvalidCard(cws, message);    
  }

  public void cwsProfileChanged(CaProfile profile, boolean added) {
    for(Iterator iter = new ArrayList(listeners).iterator(); iter.hasNext(); )
      ((CwsListener)iter.next()).cwsProfileChanged(profile, added);
  }

  public void addCwsListener(CwsListener listener) {
    if(!listeners.contains(listener)) listeners.add(listener);
  }

  public void removeCwsListener(CwsListener listener) {
    listeners.remove(listener);
  }

  public int resetStatus(String cwsName, boolean full) {
    CwsConnector cws = getCwsConnectorByName(cwsName);
    if(cws == null) return -1;
    if(cws.getProfile() == CaProfile.MULTIPLE) {
      ((MultiCwsConnector)cws).clearRemoteState(full);
      return 1;
    } else return getServiceMapper(cws.getProfileName()).resetStatus(cwsName, full);
  }

  public boolean resetStatus(String profileName, ServiceMapping id) {
    CaProfile profile = config.getProfile(profileName);
    return profile != null && getServiceMapper(profileName).resetStatus(id);
  }

  public void saveServiceMaps() {
    for(Iterator iter = serviceMappers.values().iterator(); iter.hasNext(); )
      ((CwsServiceMapper)iter.next()).saveServiceMaps(true);
  }


  boolean fetchConnectorFile() throws ConfigException {
    try {
      connectorFileLastCheck = System.currentTimeMillis();
      logger.fine("Fetching '" + connectorFileUri + (connectorFileLastModified != -1?", lm: " +
          new Date(connectorFileLastModified):""));
      String newFile = FileFetcher.fetchFile(connectorFileUri, connectorFileKey, connectorFileLastModified);
      if(newFile != null) {
        if(connectorFile != null && connectorFile.hashCode() == newFile.hashCode()) {
          logger.fine("No changes found after fetch...");
        } else {
          processConnectorFile(newFile);
          updateAuUsers();
          return true;
        }
      } else logger.fine("Connector file unchanged...");
    } catch(IOException e) {
      logger.throwing(e);
      logger.warning("Failed to fetch connector file '" + connectorFileUri +"': " + e);
    }
    return false;
  }

  void processConnectorFile(String newFile) {
    try {
      ProxyXmlConfig xml = new ProxyXmlConfig(new XMLConfig(newFile, false));
      updateConnectors(xml, externalConnectors);
      logger.info(externalConnectors.size() + " connector definitions parsed/updated from '" + connectorFileUri +
          "', total: " + getConnectors().size());
      if(!externalConnectors.isEmpty()) {
        synchronized(this) {
          notifyAll(); // have connector thread check if any new connectors should be connected now
        }
      }
      if(!newFile.equals(connectorFile)) {
        connectorFile = newFile;
        connectorFileLastModified = System.currentTimeMillis();
      }
    } catch(XMLConfigException e) {
      logger.throwing(e);
      logger.warning("Unable to parse '" + connectorFileUri + "': " + e.getMessage());
    } catch(ConfigException e) {
      logger.throwing(e);
      logger.warning("Error in connector file '" + connectorFileUri + "': " + e.getMessage());
    }
  }


  private class ExitCacheSaveThread extends Thread {
    public void run() {
      logger.info("Saving channel maps and seen data on exit...");
      saveServiceMaps();
      SessionManager.getInstance().saveSeenData();
      config.stopPlugins();
    }
  }
}
