package com.bowman.cardserv;

import com.bowman.cardserv.cws.*;
import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.rmi.RemoteHandler;
import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.util.*;
import com.bowman.xml.*;

import java.io.*;
import java.text.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * ser: bowman
 * Date: Oct 8, 2005
 * Time: 4:04:22 PM
 */
public class ProxyConfig implements FileChangeListener {

  public static final String DEFAULT_CONFIG = "config/proxy.xml";
  public static final long DEFAULT_INTERVAL = 3000;

  private static ProxyConfig instance = null;

  public static ProxyConfig getInstance() {
    if(instance == null) instance = new ProxyConfig();
    return instance;
  }

  private XmlConfigurable rootConfigurable;
  private File cfgFile;
  private FileWatchdog fw;

  private int proxyOriginId = (int)System.currentTimeMillis(); // use lower 4 bytes of sys clock as id for this run
  private int logRotateCount, logRotateLimit;
  private String logFile, logLevel, wtBadFlags;
  private int wtMaxDelay, etMinCount, maxThreads, sessionTimeout, newcamdMaxMsgSize, maxPending, maxConnectionsIP;
  private boolean silent, debug, userAllowOnFailure, logFailures, logEcm, logEmm, logZap, hideIPs, blockCaidMismatch;
  private boolean wtIncludeFile, userAllowDifferentIp;

  private boolean firstRead = true, started = false;
  private byte[] defaultProfileDesKey, defaultConnectorDesKey, defaultClientId;
  private int defaultConnectorKeepAlive, defaultConnectorMinDelay, defaultConnectorMaxQueue, sessionKeepAlive;
  private Set sessionKeepAliveClients = Collections.EMPTY_SET, defaultDenyList = new HashSet();

  private CacheHandler cacheHandler;
  private UserManager userManager;
  private CwsConnectorManager connManager;
  private RemoteHandler remoteHandler;
  private ProxyLogger logger;
  private CamdMessageListener defaultListener;

  private Map proxyPlugins = new HashMap();
  private Map pluginLoadData = new HashMap();
  private Map profiles = new HashMap();
  private Map profilesById = new HashMap();
  private Set disabledProfiles = new HashSet();

  private boolean sidLinkerEnabled;
  private SidCacheLinker sidLinker;

  private ProxyConfig() {}

  public Map getProfiles() {
    return profiles;
  }

  public Set getRealProfiles() {
    Set real = new HashSet(profiles.values());
    real.remove(CaProfile.MULTIPLE);
    return real;
  }
  
  public CaProfile getProfile(String name) {
    if(name == null) return null;
    else return (CaProfile)profiles.get(name);
  }

  public CaProfile getProfile() {
    return (CaProfile)profiles.values().iterator().next();
  }

  public CaProfile getProfileById(int networkId, int caId) {
    if(networkId == 0) return null;
    return (CaProfile)profilesById.get(CaProfile.getKeyStr(networkId, caId));
  }

  public String getProfileNameById(int networkId, int caId) {
    CaProfile profile = getProfileById(networkId, caId);
    if(profile == null) return null;
    else return profile.getName();
  }

  public boolean isLogEcm() {
    return logEcm;
  }

  public boolean isLogEmm() {
    return logEmm;
  }

  public boolean isLogZap() {
    return logZap;
  }

  public String getLogFile() {
    return logFile;
  }

  public int getProxyOriginId() {
    return proxyOriginId;
  }

  public int getLogRotateCount() {
    return logRotateCount;
  }

  public int getLogRotateLimit() {
    return logRotateLimit;
  }

  public String getLogLevel() {
    return logLevel;
  }

  public boolean isSilent() {
    return silent;
  }

  public boolean isDebug() {
    return debug;
  }

  public boolean isIncludeFileEvents() {
    return wtIncludeFile;
  }

  public boolean isHideIPs() {
    return hideIPs;
  }

  public boolean isUserAllowOnFailure() {
    return userAllowOnFailure;
  }

  public boolean isUserAllowDifferentIp() {
    return userAllowDifferentIp;
  }

  public boolean isLogFailures() {
    return logFailures;
  }

  public boolean isStarted() {
    return started;
  }

  public boolean isBlockCaidMismatch() {
    return blockCaidMismatch;
  }

  public byte[] getDefaultProfileDesKey() {
    return defaultProfileDesKey;
  }

  public Set getDefaultDenyList() {
    return defaultDenyList;
  }

  public byte[] getDefaultConnectorDesKey() {
    return defaultConnectorDesKey;
  }

  public byte[] getDefaultClientId() {
    return defaultClientId;
  }

  public int getDefaultConnectorKeepAlive() {
    return defaultConnectorKeepAlive;
  }

  public int getSessionKeepAlive() {
    return sessionKeepAlive;
  }

  public Set getKeepAliveExcludedClients() {
    return sessionKeepAliveClients;
  }

  public int getDefaultConnectorMinDelay() {
    return defaultConnectorMinDelay;
  }

  public int getDefaultConnectorMaxQueue() {
    return defaultConnectorMaxQueue;
  }

  public int getSessionTimeout() {
    return sessionTimeout;
  }

  public int getMaxThreads() {
    return maxThreads;
  }

  public int getNewcamdMaxMsgSize() {
    return newcamdMaxMsgSize;
  }

  public CwsConnectorManager getConnManager() {
    return connManager;
  }

  public CacheHandler getCacheHandler() {
    return cacheHandler;
  }

  public SidCacheLinker getSidCacheLinker() {
    if(!sidLinkerEnabled) return null;
    else return sidLinker;
  }

  public UserManager getUserManager() {
    return userManager;
  }

  public RemoteHandler getRemoteHandler() {
    return remoteHandler;
  }

  public void setRemoteHandler(RemoteHandler remoteHandler) {
    this.remoteHandler = remoteHandler;
  }

  public Map getProxyPlugins() {
    return proxyPlugins;
  }

  public int getServiceCount() {
    int count = 0;
    Map services;
    for(Iterator iter = profiles.values().iterator(); iter.hasNext(); ) {
      services = ((CaProfile)iter.next()).getServices();
      if(services != null) count += services.size();
    }
    return count;
  }

  public TvService getService(CamdNetMessage msg) {
    CaProfile profile;
    profile = getProfile(msg.getProfileName());
    if(profile == null) profile = getProfileById(msg.getNetworkId(), msg.getCaId());    
    String profileName = profile==null?"*":profile.getName();
    return getService(profileName, new ServiceMapping(msg));
  }

  public TvService getService(String profileName, ServiceMapping id) {
    TvService service = getService(profileName, id.serviceId);
    return new TvService(service, id.getCustomData());
  }

  public TvService getService(String profileName, int serviceId) {
    if(serviceId == 0 || connManager.isServiceUnknown(profileName, serviceId))
      return TvService.getUnknownService(profileName, serviceId);

    Integer id = new Integer(serviceId);
    CaProfile profile = (CaProfile)profiles.get(profileName);
    TvService service = (TvService)profile.getServices().get(id);
    if(service == null) return TvService.getUnknownService(profileName, serviceId);
    else return service;
  }

  public String getServiceName(String profileName, ServiceMapping id) {
    TvService service = getService(profileName, id.serviceId);
    if(service == null) return id.toString();
    else return id + "/" + service.getName();
  }

  public String getServiceName(CamdNetMessage msg) {
    TvService service = getService(msg);
    if(service == null) return new ServiceMapping(msg).toString();
    else return new ServiceMapping(service) + "/" + service.getName();
  }

  public String getServiceName(String profileName, int serviceId) {
    TvService service = getService(profileName, serviceId);
    if(service == null) return Integer.toHexString(serviceId);
    else return Integer.toHexString(serviceId) + "/" + service.getName();
  }

  public int getServiceType(String profileName, int serviceId) {
    TvService service = getService(profileName, serviceId);
    if(service == null) return -1;
    else return service.getType();
  }

  public boolean isTransactionWarning(String flags, int duration) {
    boolean warning = false;
    if(duration > wtMaxDelay) {
      // dont consider long timeouts towards disconnected clients to be a duration warning
      if(flags.indexOf('D') > -1) {
        if(duration < connManager.getMaxCwWait(null)) warning = true;
      } else warning = true;
    } else {
      for(int i = 0; i < flags.length(); i++)
        if(wtBadFlags.indexOf(flags.charAt(i)) > -1) {
          warning = true;
          break;
        }
    }    
    return warning;
  }

  public synchronized void parseConfig(ProxyXmlConfig currentConfig) throws ConfigException {

    String version = currentConfig.getStringValue("ver");
    if(!version.equals(CardServProxy.APP_VERSION))
      throw new ConfigException("Config file version '" + version + " does not match application version '" +
          CardServProxy.APP_VERSION + "'.");

    ProxyXmlConfig logConfig = currentConfig.getSubConfig("logging");

    logEcm = "true".equalsIgnoreCase(logConfig.getStringValue("log-ecm", "true"));
    logEmm = "true".equalsIgnoreCase(logConfig.getStringValue("log-emm", "true"));
    logZap = "true".equalsIgnoreCase(logConfig.getStringValue("log-zapping", "true"));

    logRotateLimit = 0; logRotateCount = 0;
    try {
      ProxyXmlConfig logFileConfig = logConfig.getSubConfig("log-file");
      logRotateCount = logFileConfig.getIntValue("rotate-count");
      if(logRotateCount < 1) logRotateCount = 0;
      logRotateLimit = logFileConfig.getIntValue("rotate-max-size");
      if(logRotateLimit < 1) logRotateLimit = 0;
    } catch (ConfigException e) {}

    logFile = logConfig.getFileValue("log-file", true);
    if(logRotateCount > 0 && logRotateLimit > 0)
      if(!new File(logFile).delete()) { /* ignore */ }

    logLevel = logConfig.getStringValue("log-level");
    silent = "true".equalsIgnoreCase(logConfig.getStringValue("silent", "false"));
    debug = "true".equalsIgnoreCase(logConfig.getStringValue("debug", "false"));
    hideIPs = "true".equalsIgnoreCase(logConfig.getStringValue("hide-ip-addresses", "false"));
    try {
      CustomFormatter.setDateFormat(new SimpleDateFormat(logConfig.getStringValue("log-dateformat", "yyMMdd HH:mm:ss.SSS")));
    } catch (IllegalArgumentException e) {
      throw new ConfigException(logConfig.getFullName(), "Illegal date format: " + e.getMessage());
    }

    try {
      ProxyXmlConfig wtConfig = logConfig.getSubConfig("warning-threshold");
      wtBadFlags = wtConfig.getStringValue("bad-flags");
      wtMaxDelay = wtConfig.getTimeValue("max-delay", "ms");
      wtIncludeFile = "true".equalsIgnoreCase(wtConfig.getStringValue("include-file-events", "true"));
    } catch (ConfigException e) {
      wtBadFlags = "YMNTSAOQGXWDHU-";
      wtMaxDelay = 5000;
      wtIncludeFile = true;
    }

    try {
      ProxyXmlConfig etConfig = logConfig.getSubConfig("event-threshold");
      etMinCount = etConfig.getIntValue("min-count", 1);
    } catch (ConfigException e) {
      etMinCount = 1;
    }

    ProxyXmlConfig umConfig = currentConfig.getSubConfig("user-manager");
    userAllowOnFailure = "true".equalsIgnoreCase(umConfig.getStringValue("allow-on-failure", "false"));
    userAllowDifferentIp = "true".equalsIgnoreCase(umConfig.getStringValue("allow-different-ip", "false"));
    logFailures = "true".equalsIgnoreCase(umConfig.getStringValue("log-failures", "true"));

    ProxyXmlConfig profileConf = currentConfig.getSubConfig("ca-profiles");
    try {
      defaultProfileDesKey = profileConf.getBytesValue("default-des-key");
    } catch (ConfigException e) {}
    defaultDenyList.clear();
    try {
      String denyList = profileConf.getStringValue("default-deny-list");
      defaultDenyList.addAll(Arrays.asList(denyList.split(" ")));
    } catch (ConfigException e) {}

    maxThreads = profileConf.getIntValue("max-threads", 1000);
    maxPending = profileConf.getIntValue("max-pending", 3);
    sessionTimeout = profileConf.getTimeValue("session-timeout", 240, "m");
    newcamdMaxMsgSize = profileConf.getIntValue("newcamd-maxmsgsize", 400);
    blockCaidMismatch = "true".equalsIgnoreCase(profileConf.getStringValue("block-caid-mismatch", "true"));
    maxConnectionsIP = profileConf.getIntValue("max-connections-ip", 0);

    ProxyXmlConfig keepAliveConf = null;
    try {
      keepAliveConf = profileConf.getSubConfig("session-keepalive");
    } catch (ConfigException e) {}
    if(keepAliveConf != null) {
      sessionKeepAlive = profileConf.getTimeValue("session-keepalive", 0, "m");
      String skacStr = keepAliveConf.getStringValue("exclude-clients", "").toLowerCase();
      if(skacStr.length() > 0) sessionKeepAliveClients = new HashSet(Arrays.asList(skacStr.split(" ")));
      else sessionKeepAliveClients = Collections.EMPTY_SET;
    } else {
      sessionKeepAliveClients = Collections.EMPTY_SET;
      sessionKeepAlive = 0;
    }

    ProxyXmlConfig connManConf = currentConfig.getSubConfig("connection-manager");
    try {
      defaultConnectorDesKey = connManConf.getBytesValue("default-des-key");
    } catch (ConfigException e) {}

    try {
      defaultClientId = connManConf.getBytesValue("default-client-id");
    } catch (ConfigException e) {
      defaultClientId = new byte[] {0x00, 0x00};
    }

    defaultConnectorKeepAlive = connManConf.getTimeValue("default-keepalive-interval", 0, "s");
    defaultConnectorMinDelay = connManConf.getTimeValue("default-min-delay", 20, "ms");
    defaultConnectorMaxQueue = connManConf.getIntValue("default-max-queue", 50);

    ProxyXmlConfig cacheConf = currentConfig.getSubConfig("cache-handler");
    sidLinkerEnabled = "true".equalsIgnoreCase(cacheConf.getStringValue("enable-service-linking", "false"));

    if(logger == null) logger = ProxyLogger.getLabeledLogger(getClass().getName());
    loadProfiles(currentConfig);
    loadPlugins(currentConfig);

    if(rootConfigurable != null) rootConfigurable.configUpdated(currentConfig);

    if(!firstRead) {
      updateModules(currentConfig);
      loadCacheLinker();
    }
  }

  public synchronized void readConfig(XmlConfigurable root, File cfgFile) throws FileNotFoundException, ConfigException {
    if(cfgFile == null) {
      this.cfgFile = new File(DEFAULT_CONFIG);

      if(!this.cfgFile.exists()) {
        System.err.println("Config file '" + this.cfgFile.getPath() + "' not found, generating template...");
        try {
          BufferedReader br = new BufferedReader(new InputStreamReader(CardServProxy.class.getResourceAsStream("proxy.xml"), "UTF-8"));
          String line; StringBuffer sb = new StringBuffer();
          while((line = br.readLine()) != null) sb.append(line).append('\n');
          String xml = sb.toString();
          xml = MessageFormat.format(xml, new Object[] {CardServProxy.APP_VERSION});
          saveCfgFile(xml.getBytes("UTF-8"));
          System.err.println("Default su credentials: admin/secret (statusweb @ port 8082)");
        } catch (IOException e) {
          throw new ConfigException("Failed to generate default config: " + e, e);
        }
      }
    } else this.cfgFile = cfgFile;

    this.rootConfigurable = root;

    if(fw == null) {
      fw = new FileWatchdog(this.cfgFile.getPath(), DEFAULT_INTERVAL);
      fw.addFileChangeListener(this);
      fw.start();
    }

    ProxyXmlConfig currentConfig;
    try {
      currentConfig = new ProxyXmlConfig(new XMLConfig(new FileInputStream(this.cfgFile), false, "UTF-8"));
    } catch(XMLConfigException e) {
      throw new ConfigException("Unable to parse " + this.cfgFile + ": " + e.getMessage(), e);
    }

    parseConfig(currentConfig);

    if(firstRead) {
      firstRead = false;
      loadModules(currentConfig);
      loadCacheLinker();
    }
  }

  private void loadCacheLinker() {
    if(sidLinker == null) sidLinker = new SidCacheLinker(); // always load
    if(sidLinkerEnabled) cacheHandler.setListener(sidLinker);
    else cacheHandler.setListener(null);
  }

  private void loadProfiles(ProxyXmlConfig currentConfig) throws ConfigException {
    disabledProfiles.clear();
    ProxyXmlConfig profileConf = currentConfig.getSubConfig("ca-profiles");
    Iterator iter = profileConf.getMultipleSubConfigs("profile");
    Set newProfiles = new HashSet();
    CaProfile profile;
    ProxyXmlConfig profileConfig;
    while(iter.hasNext()) {
      profileConfig = (ProxyXmlConfig)iter.next();
      profile = (CaProfile)profiles.get(profileConfig.getStringValue("name"));
      if(profile == null) {
        profile = new CaProfile();
        profile.configUpdated(profileConfig);
        addProfile(profile);
        profileChanged(profile, true);
      } else {
        profilesById.remove(profile.getKeyStr());
        profile.configUpdated(profileConfig);
        if(!profile.isEnabled()) {
          profiles.remove(profile.getName());
          disabledProfiles.add(profile.getName());
        } else profilesById.put(profile.getKeyStr(), profile);
        profileChanged(profile, false);
      }
      newProfiles.add(profile.getName());
    }
    String name;
    for(iter = new ArrayList(profiles.keySet()).iterator(); iter.hasNext(); ) {
      name = (String)iter.next();
      if(CaProfile.MULTIPLE.getName().equals(name)) continue;
      if(!newProfiles.contains(name)) {
        profile = (CaProfile)profiles.remove(name);
        profilesById.remove(profile.getKeyStr());
        profile.setEnabled(false);
        disabledProfiles.remove(profile.getName());

        profileChanged(profile, false);
      }
    }
    if(!profiles.containsKey(CaProfile.MULTIPLE.getName())) addProfile(CaProfile.MULTIPLE);
    loadExtendedPort(profileConf);
  }

  public void addProfile(CaProfile profile) throws ConfigException {
    if(profile.isEnabled()) {
      if(profiles.containsKey(profile.getName()))
        throw new ConfigException("ca-profiles", "Duplicate profile definition: " + profile.getName());
      if(getProfileById(profile.getNetworkId(), profile.getCaId()) != null)
        throw new ConfigException("ca-profiles", "Duplicate profile definition for network-id '" +
            profile.getNetworkIdStr() + "' and ca-id '" + profile.getCaIdStr() + "'.");
      profiles.put(profile.getName(), profile);
      profilesById.put(profile.getKeyStr(), profile);
      // if the proxy is already running, start any new added profiles immediately
      if(defaultListener != null) profile.startListening(defaultListener);
    } else {
      disabledProfiles.add(profile.getName());
    }
  }

  private Properties previousPortCfg;
  private ListenPort extendedPort;

  private void loadExtendedPort(ProxyXmlConfig profileConfig) throws ConfigException {
    ProxyXmlConfig port = null;
    try {
      port = profileConfig.getSubConfig("extended-newcamd");
    } catch (ConfigException e) {}
    if(port != null && "true".equals(port.getStringValue("enabled", "true"))) {
      Properties newPortCfg = port.toProperties();

      if(newPortCfg.equals(previousPortCfg)) return; // no changes, avoid closing and reopening port
      else previousPortCfg = newPortCfg;

      if(extendedPort != null) {
        extendedPort.configUpdated(port); // update the instance used by existing sessions
        extendedPort.destroy(); // but destroy/reopen in case portnr changed
        CaProfile.MULTIPLE.removeListenPort(extendedPort);
        try {
          Thread.sleep(500); // give port a chance to close
        } catch(InterruptedException e) {
          e.printStackTrace();
        }
      }
      extendedPort = new ListenPort("ExtNewcamd");
      extendedPort.configUpdated(port);
      if(defaultListener != null) extendedPort.start(defaultListener, CaProfile.MULTIPLE);
      CaProfile.MULTIPLE.addListenPort(extendedPort);
      profileChanged(CaProfile.MULTIPLE, false);
    } else {
      if(extendedPort != null) {
        CaProfile.MULTIPLE.removeListenPort(extendedPort);
        extendedPort.destroy();
        extendedPort = null;
      }
      previousPortCfg = null;
      profileChanged(CaProfile.MULTIPLE, false);
    }
  }

  private void profileChanged(CaProfile profile, boolean added) {
    if(connManager != null) connManager.cwsProfileChanged(profile, added);
  }

  private void loadModules(ProxyXmlConfig currentConfig) throws ConfigException {
    cacheHandler = (CacheHandler)loadInstance(currentConfig.getSubConfig("cache-handler"),
      "cache-config", CacheHandler.class);
    userManager = (UserManager)loadInstance(currentConfig.getSubConfig("user-manager"),
        "auth-config", UserManager.class);
    connManager = new CwsConnectorManager();
    connManager.configUpdated(currentConfig.getSubConfig("connection-manager"));
  }

  private void loadPlugins(ProxyXmlConfig currentConfig) throws ConfigException {
    stopPlugins();
    ProxyXmlConfig pluginConf;
    try {
      pluginConf = currentConfig.getSubConfig("proxy-plugins");
    } catch (ConfigException e) {
      return;
    }
    Iterator iter = pluginConf.getMultipleSubConfigs("plugin");
    if(iter == null) return;
    ProxyXmlConfig tmp;
    while(iter.hasNext()) {
      tmp = (ProxyXmlConfig)iter.next();
      if("true".equalsIgnoreCase(tmp.getStringValue("enabled", "true"))) {
        loadPlugin(tmp);
      }
    }
  }

  private ProxyPlugin loadPlugin(ProxyXmlConfig tmp) throws ConfigException {
    ProxyPlugin plugin;
    plugin = (ProxyPlugin)loadInstance(tmp, "plugin-config", ProxyPlugin.class);
    proxyPlugins.put(plugin.getName().toLowerCase(), plugin);
    if(fw != null) {
      String jarName = tmp.getStringValue("jar-file", "");
      if(jarName.length() > 0) {
        String jarFile = new File("plugins", jarName).getPath();
        pluginLoadData.put(plugin.getName().toLowerCase(), new PluginMetaData(tmp, jarFile));
        fw.addFile(jarFile);
      }
    }
    return plugin;
  }
  
  public void stopPlugins() {
    if(!proxyPlugins.isEmpty() && logger != null) logger.info("Stopping " + proxyPlugins.size() + " loaded plugins...");
    ProxyPlugin plugin;
    for(Iterator iter = proxyPlugins.values().iterator(); iter.hasNext(); ) {
      plugin = (ProxyPlugin)iter.next();
      stopPlugin(plugin);
      iter.remove();
    }
    proxyPlugins.clear();
    pluginLoadData.clear();
    System.gc();
    System.runFinalization();
  }

  private void stopPlugin(ProxyPlugin plugin) {
    try {
      plugin.stop();
    } catch (Throwable t) {
      if(logger != null) logger.warning("Uncaught exception in plugin stop() for '" + plugin.getName() + "': " + t);
      if(logger != null) logger.throwing(t);
    }
    ClassLoader cl = plugin.getClass().getClassLoader();
    if(cl instanceof PluginClassLoader) ((PluginClassLoader)cl).flush();

    if(fw != null && pluginLoadData.containsKey(plugin.getName())) {
      String jarFile = ((PluginMetaData)pluginLoadData.get(plugin.getName().toLowerCase())).jarFile;
      if(jarFile != null) fw.removeFile(jarFile);
    }
  }

  private void updateModules(ProxyXmlConfig currentConfig) throws ConfigException {
    cacheHandler.configUpdated(currentConfig.getSubConfig("cache-handler").getSubConfig("cache-config"));
    userManager.configUpdated(currentConfig.getSubConfig("user-manager").getSubConfig("auth-config"));
    connManager.configUpdated(currentConfig.getSubConfig("connection-manager"));
  }

  public static Object loadInstance(ProxyXmlConfig subConf, String confName, Class intf) throws ConfigException {
    return loadInstance(subConf, confName, intf, null);
  }

  private static Object loadInstance(ProxyXmlConfig subConf, String confName, Class intf, ClassLoader cl)
      throws ConfigException
  {
    String className = subConf.getStringValue("class");

    if(cl == null) {
      String jarName = subConf.getStringValue("jar-file", "");
      if(!"".equals(jarName)) { // jar-file specified, load using disposable classloader
        File jarFile = null;
        try { 
          jarFile = new File("plugins", jarName);
          if(!jarFile.exists()) throw new ConfigException(subConf.getFullName(), "File not found: " + jarFile.getAbsolutePath());
          cl = new PluginClassLoader(jarFile, ProxyConfig.class.getClassLoader());
        } catch(IOException e) {
          throw new ConfigException(subConf.getFullName(), "Bad jar-file: " + jarFile.getAbsolutePath(), e);
        }
      }
    }

    Class c;
    try {
      if(cl == null) c = Class.forName(className);
      else c = Class.forName(className, true, cl);
      if(!intf.isAssignableFrom(c))
        throw new ConfigException(subConf.getFullName(), "Class '" + className + "' does not implement interface: " +
            intf.getName());
    } catch(ClassNotFoundException e) {
      throw new ConfigException(subConf.getFullName(), "Class not found: " + className);
    } catch(NoClassDefFoundError e) {
      throw new ConfigException(subConf.getFullName(), "Class defs not found: " + e.getMessage());
    }

    XmlConfigurable xc;
    try {
      xc = (XmlConfigurable)c.newInstance();
    } catch(Exception e) {
      throw new ConfigException(subConf.getFullName(), "Unable to instantiate class '" + className + "': " + e, e);
    }
    if(confName != null) {
      ProxyXmlConfig pluginConfig = null;
      try {
        pluginConfig = subConf.getSubConfig(confName);
      } catch(ConfigException e) {}

      if(pluginConfig == null) xc.configUpdated(new ProxyXmlConfig("plugin-config", "/cardserv-proxy/proxy-plugins/plugin")); // empty dummy
      else xc.configUpdated(pluginConfig);
    }
    return xc;
  }

  public void fileChanged(String fileName) {
    if(logger == null) logger = ProxyLogger.getLabeledLogger(getClass().getName());
    logger.info("File changes detected: " + fileName);

    if(cfgFile.getPath().equals(fileName)) {
      try {
        readConfig(rootConfigurable, new File(fileName));
      } catch(FileNotFoundException e) {
        logger.severe("Configuration file not found: " + fileName);
      } catch(ConfigException e) {
        logConfigException(logger, e);
      } catch(Exception e) {
        logger.severe("Internal error updating configuration: " + e, e);
      }
    } else if(fileName.endsWith(".jar")) {

      for(Iterator iter = new ArrayList(proxyPlugins.keySet()).iterator(); iter.hasNext(); ) {
        String name = (String)iter.next();
        if(name != null) {
          PluginMetaData pm = (PluginMetaData)pluginLoadData.get(name);
          if(pm != null && fileName.equals(pm.jarFile)) {

            logger.info("Stopping plugin '" + proxyPlugins.get(name).getClass() + "'...");

            stopPlugin((ProxyPlugin)proxyPlugins.get(name));
            proxyPlugins.remove(name);
            pluginLoadData.remove(name);

            try {
              Thread.sleep(100);
            } catch(InterruptedException e) {
              e.printStackTrace();
            }

            System.gc();
            System.runFinalization();

            ProxyPlugin plugin = null;
            try {
              plugin = loadPlugin(pm.config);

              logger.info("Starting plugin '" + proxyPlugins.get(name).getClass() + "'...");

              plugin.start((CardServProxy)rootConfigurable);
            } catch(ConfigException e) {
              logConfigException(logger, e);
              if(plugin != null) proxyPlugins.remove(plugin.getName().toLowerCase());
            }

          }
        }
      }

    }

  }

  public void setDefaultMsgListener(CamdMessageListener listener) {
    defaultListener = listener;
    started = true;
    if(extendedPort != null) extendedPort.start(listener, CaProfile.MULTIPLE);
  }

  public CamdMessageListener getDefaultMsgListener() {
    return defaultListener;
  }

  public File getCfgFile() {
    return cfgFile;
  }

  public void saveCfgFile(byte[] newFile) throws IOException {
    if(fw != null) fw.removeFile(cfgFile.getPath()); // disable watchdog before rewriting file
    if(cfgFile.exists())
      if(!cfgFile.renameTo(new File(cfgFile.getPath() + ".bak"))) { /* ignore */ }
    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(cfgFile));
    bos.write(newFile);
    bos.close();
    if(fw != null) fw.addFile(cfgFile.getPath());
  }

  public int getEtMinCount() {
    return etMinCount;
  }
  
  public int getMaxPending() {
    return maxPending;
  }

  public int getMaxConnectionsIP() {
    return maxConnectionsIP;
  }

  public boolean isProfileDisabled(String profileName) {
    return disabledProfiles.contains(profileName);
  }

  public static void logConfigException(ProxyLogger logger, ConfigException e) {
    logger.severe("Configuration error:");
    if(e.getLabel() != null) {
      if(e.getLabel().indexOf(' ') == -1) logger.severe("- Element: <" + e.getLabel() + ">");
      else {
        logger.severe("- Element: <" + e.getLabel());
        if(e.getSubLabel() != null) logger.severe("- Attribute: " + e.getSubLabel());
      }
    }
    logger.severe("- Message: " + e.getMessage());
  }

  public static String providerIdentsToString(Set providers) {
    Set set = new TreeSet();
    for(Iterator iter = providers.iterator(); iter.hasNext(); ) {
      set.add(DESUtil.intToByteString(((Integer)iter.next()).intValue(), 3));
    }
    return set.toString();
  }

  public static String providerIdentsToString(Integer[] providers) {
    Set set = new TreeSet();
    for(int i = 0; i < providers.length; i++) {
      set.add(DESUtil.intToByteString(providers[i].intValue(), 3));
    }
    return set.toString();
  }

  public static Set getServiceTokens(String param, String list, boolean excludeCdata) throws ConfigException {
    Set result = new HashSet();
    String[] tokens = list.split(" ");
    String[] service; ServiceMapping sm;
    for(int i = 0; i < tokens.length; i++) {
      service = tokens[i].split(":");
      try {

        sm = new ServiceMapping(Integer.parseInt(service[0], 16), 0);
        boolean identSet = false;
        if(!excludeCdata && service.length >= 2) {
          if(service[1].length() > 4) {
            sm.setProviderIdent(Integer.parseInt(service[1], 16));
            identSet = true;
          } else sm.setCustomId(Integer.parseInt(service[1], 16));
          if(service.length == 3) {
            if(service[2].length() > 4) {
              sm.setProviderIdent(Integer.parseInt(service[1], 16));
              identSet = true;
            } else sm.setCustomId(Integer.parseInt(service[2], 16));
          }         
        }
        if(!identSet) sm.setProviderIdent(ServiceMapping.NO_PROVIDER);

        result.add(sm);
      } catch (NumberFormatException e) {
        throw new ConfigException(param, "Bad hex integer value: " + e.getMessage());
      }
    }
    return result;
  }

  private static class PluginMetaData {

    ProxyXmlConfig config;
    String jarFile;

    private PluginMetaData(ProxyXmlConfig config, String jarFile) {
      this.config = config;
      this.jarFile = jarFile;
    }
  }
}
