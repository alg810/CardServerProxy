package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.ProxyXmlConfig;
import com.bowman.cardserv.tv.*;
import com.bowman.cardserv.session.SessionManager;
import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.cws.*;
import com.bowman.util.*;

import java.util.*;
import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2006-feb-26
 * Time: 10:38:49
 */
public class CaProfile implements XmlConfigurable, FileChangeListener {

  public static final CaProfile MULTIPLE;

  static {
    MULTIPLE = new CaProfile("*");
    MULTIPLE.setEnabled(true);
    MULTIPLE.caId = -1;
    ListenPort lp = new ListenPort("Csp"); // dummy lp for display
    lp.setProfile(MULTIPLE);
    MULTIPLE.listenPorts.add(lp);
  }

  private String name;
  private boolean enabled, cacheOnly, debug, mismatchedCards;
  private Boolean requireProviderMatch;
  private int caId, networkId;
  private long maxCwWait, congestionLimit;
  private int serviceConflicts;
  private String servicesFile, providerFilter, fileCaId, servicesFileFormat;
  private FileWatchdog servicesFwd;
  private Properties previousPorts;

  private CamdMessageListener listener;
  private Map services = new HashMap();
  private List listenPorts = new ArrayList();
  private Set predefinedProviders = new HashSet();

  public CaProfile() {}

  public CaProfile(String name) {
    this.name = name;
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    name = xml.getStringValue("name");
    if(CaProfile.MULTIPLE.getName().equals(name))
      throw new ConfigException(xml.getFullName(), "Invalid profile name: " + CaProfile.MULTIPLE.getName());

    enabled = "true".equalsIgnoreCase(xml.getStringValue("enabled", "true"));
    debug = "true".equalsIgnoreCase(xml.getStringValue("debug", "true"));
    cacheOnly = "true".equalsIgnoreCase(xml.getStringValue("cache-only", "false"));
    requireProviderMatch = null;
    try {
      if("true".equalsIgnoreCase(xml.getStringValue("require-provider-match"))) requireProviderMatch = Boolean.TRUE;
      else requireProviderMatch = Boolean.FALSE;
    } catch (ConfigException e) {}

    try {
      maxCwWait = xml.getTimeValue("max-cw-wait", "s");
    } catch (ConfigException e) {
      maxCwWait = -1;
    }

    if(maxCwWait != -1) {
      if(maxCwWait <= 1000) throw new ConfigException(xml.getFullName(), "max-cw-wait must be > 1");
      try {
        congestionLimit = xml.getTimeValue("congestion-limit", "s");
        if(congestionLimit >= maxCwWait || congestionLimit < maxCwWait / 2)
          throw new ConfigException(xml.getFullName(), "congestion-limit must be between max-cw-wait/2 and max-cw-wait");
      } catch (ConfigException e) {
        congestionLimit = maxCwWait;
      }
    } else congestionLimit = -1;

    if(enabled) { // dont allow changes to these unless enabled
      try {
        caId = Integer.parseInt(xml.getStringValue("ca-id", "0000"), 16);
      } catch (NumberFormatException e) {
        throw new ConfigException(xml.getFullName(), "ca-id", "Bad hex integer value: " + e.getMessage());
      }

      try {
        networkId = Integer.parseInt(xml.getStringValue("network-id", "0000"), 16);
      } catch (NumberFormatException e) {
        throw new ConfigException(xml.getFullName(), "network-id", "Bad hex integer value: " + e.getMessage());
      }

      if(networkId == 0)
        if(CardServProxy.logger != null) CardServProxy.logger.warning("No original network-id set for profile: " + name);

      predefinedProviders.clear();
      String[] providerIdents = xml.getStringValue("provider-idents", "").split(",");
      for(int i = 0; i < providerIdents.length; i++) {
        providerIdents[i] = providerIdents[i].trim();
        try {
          if(providerIdents[i].length() > 0) {
            predefinedProviders.add(new Integer(DESUtil.byteStringToInt(providerIdents[i])));
          }
        } catch (NumberFormatException e) {
          throw new ConfigException(xml.getFullName(), "provider-idents", "Bad provider value: " + e.getMessage());
        }
      }
      Integer defaultPi = new Integer(0);
      if(predefinedProviders.size() > 1)
        if(requireProviderMatch == null) requireProviderMatch = Boolean.TRUE;
      if(predefinedProviders.size() == 1 && predefinedProviders.contains(defaultPi))
        if(requireProviderMatch == null) requireProviderMatch = Boolean.FALSE;

      if(requireProviderMatch != null) {
        if(!requireProviderMatch.booleanValue()) predefinedProviders.add(defaultPi); // make sure pi 0 is included when it doesnt matter
      }
      
    }

    if(servicesFwd != null) { // kill any previously registered filewatchdog
      servicesFwd.addFileChangeListener(null);
      servicesFwd.removeAllFiles();
      servicesFwd.interrupt();
      servicesFwd = null;
    }

    if(enabled) {

      updateListenPorts(xml);

      servicesFile = null;
      try {
        servicesFile = xml.getStringValue("services-file");
      } catch (ConfigException e) {
        // optional
      }
      if(servicesFile != null) {
        try {
          providerFilter = null;
          try {
            providerFilter = xml.getSubConfig("services-file").getStringValue("filter");
          } catch (ConfigException e) {}
          /*
          if(providerFilter == null) try {
            providerFilter = xml.getSubConfig("services-file").getStringValue("provider");
          } catch (ConfigException e) {}
          */
          fileCaId = null;
          try {
            fileCaId = xml.getSubConfig("services-file").getStringValue("ca-id");
          } catch (ConfigException e) {}

          servicesFileFormat = xml.getSubConfig("services-file").getStringValue("format", "enigma");
          parseServicesFile();

          servicesFwd = new FileWatchdog(servicesFile, ProxyConfig.DEFAULT_INTERVAL);
          servicesFwd.addFileChangeListener(this);
          servicesFwd.start();
        } catch (FileNotFoundException e) {
          if(CardServProxy.logger != null) CardServProxy.logger.warning("Services file not found: " + servicesFile);
        } catch (Exception e) {
          String path = servicesFile;
          try {
            path = new File(servicesFile).getCanonicalPath();
          } catch (IOException ioe) {}
          if(CardServProxy.logger != null) CardServProxy.logger.throwing(e);
          else e.printStackTrace();
          throw new ConfigException(xml.getSubConfig("services-file").getFullName(), "Unable to parse '" + path + "' : " + e);
        }
      } else {
        services = Collections.EMPTY_MAP;
        serviceConflicts = 0;
      }

    } else removeAllPorts(null);
  }

  private int removeAllPorts(String protocol) {
    ListenPort lp; int count = 0;
    for(Iterator iter = new ArrayList(listenPorts).iterator(); iter.hasNext(); ) {
      lp = (ListenPort)iter.next();
      if(protocol == null || protocol.equals(lp.getProtocol())) {
        listenPorts.remove(lp);
        lp.destroy();
        count++;
      }
    }
    return count;
  }

  private void updateListenPorts(ProxyXmlConfig xml) throws ConfigException {

    Properties newPorts = xml.toProperties();
    if(newPorts.equals(previousPorts)) return; // no changes, avoid closing and reopening ports for this profile
    else previousPorts = newPorts;

    if(CardServProxy.logger != null) CardServProxy.logger.fine("Listen port changes detected for: " + this);

    if(removeAllPorts(null) > 0)  try {
      Thread.sleep(500); // give ports a chance to close
    } catch(InterruptedException e) {
      e.printStackTrace();
    }

    ListenPort lp;

    Iterator iter = xml.getMultipleSubConfigs("newcamd");
    if(iter != null) {
      while(iter.hasNext()) {
        lp = new ListenPort("Newcamd");
        listenPorts.add(lp);
        lp.configUpdated((ProxyXmlConfig)iter.next());
        if(listener != null) lp.start(listener, this); // proxy already running, open this port now
      }
    }

    iter = xml.getMultipleSubConfigs("radegast");
    if(iter != null) {
      while(iter.hasNext()) {
        lp = new ListenPort("Radegast");
        listenPorts.add(lp);
        lp.configUpdated((ProxyXmlConfig)iter.next());
        if(listener != null) lp.start(listener, this); // proxy already running, open this port now
      }
    }
  }

  private void parseServicesFile() throws IOException {
    if("enigma".equalsIgnoreCase(servicesFileFormat)) {
      ServicesParser parser = new ServicesParser(servicesFile);
      services = parser.parse(providerFilter, name, networkId);
      serviceConflicts = parser.getConflicts();
    } else if("cccam".equalsIgnoreCase(servicesFileFormat)) {
      CccamParser parser = new CccamParser(servicesFile);
      String caStr = getCaName(caId, false);
      if(fileCaId != null) caStr = fileCaId;
      if(providerFilter == null || providerFilter.length() == 0) {
        if(requireProviderMatch != null && requireProviderMatch.booleanValue()) {
          Integer pi; StringBuffer sb = new StringBuffer();
          for(Iterator iter = getProviderSet().iterator(); iter.hasNext(); ) {
            pi = (Integer)iter.next();
            sb.append(DESUtil.intToByteString(pi.intValue(), 3).replaceAll(" ", ""));
            if(iter.hasNext()) sb.append(' ');
          }
          providerFilter = sb.toString();
        } else providerFilter = "000000";
      }
      if(CardServProxy.logger != null) CardServProxy.logger.fine("Profile '" + name + "' parsing '" + servicesFile +
          "' for [" + caStr + "], filter: " + providerFilter);
      services = parser.parse(caStr, providerFilter, name, getNetworkIdStr());
      serviceConflicts = parser.getConflicts();
      if(CardServProxy.logger != null) CardServProxy.logger.fine("Profile '" + name + "' parsing '" + servicesFile +
          "' found " + services.size() + " services, " + serviceConflicts + " conflicts.");
    } else if("dvbviewer".equalsIgnoreCase(servicesFileFormat)) {
      DvbviewerParser parser = new DvbviewerParser(servicesFile);
      services = parser.parse(providerFilter, name, networkId);
      serviceConflicts = parser.getConflicts();
    } else if("simple".equalsIgnoreCase(servicesFileFormat)) {
      Properties p = new Properties();
      p.load(new BufferedInputStream(new FileInputStream(servicesFile)));
      services = new HashMap();
      TvService service; String id; String networkIdStr = getNetworkIdStr();
      for(Enumeration e = p.keys(); e.hasMoreElements();) {
        id = (String)e.nextElement();
        service = new TvService(new String[] {id, "00000000", "0000", networkIdStr, "1", "0"}, name);
        service.setName(p.getProperty(id));
        services.put(new Integer(service.getId()), service);
      }
      serviceConflicts = 0;
    } else throw new IOException("Unknown format name: " + servicesFileFormat);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    if(!enabled) {
      removeAllPorts(null);
      listener = null;
    }
    this.enabled = enabled;
  }

  public boolean isDebug() {
    return debug;
  }

  public void setDebug(boolean debug) {
    this.debug = debug;
  }

  public boolean isCacheOnly() {
    return cacheOnly;
  }

  public boolean isMismatchedCards() {
    return mismatchedCards;
  }

  public void setMismatchedCards(boolean mismatchedCards) {
    this.mismatchedCards = mismatchedCards;
  }

  public boolean isRequireProviderMatch() {
    if(requireProviderMatch == null) {
      Set set = getProviderSet();
      if(set.isEmpty()) return false;
      if(set.size() == 1 && set.contains(new Integer(0))) return false;
      else return true;
    } else return requireProviderMatch.booleanValue();
  }

  public int getCaId() {
    return caId;
  }

  public int getNetworkId() {
    return networkId;
  }

  public String getCaIdStr() {
    if(caId < 0) return "undefined";
    else return getCaName(getCaId());
  }

  public String getNetworkIdStr() {
    String s = Integer.toHexString(networkId);
    while(s.length() < 4) s = "0" + s;
    return s;
  }

  public Map getServices() {
    return services;
  }

  public int getServiceConflicts() {
    return serviceConflicts;
  }

  public boolean equals(Object o) {
    if(this == o) return true;
    if(o == null || getClass() != o.getClass()) return false;
    final CaProfile caProfile = (CaProfile)o;
    return name.equals(caProfile.name);
  }

  public int hashCode() {
    return name.hashCode();
  }

  public void fileChanged(String name) {
    try {
      CardServProxy.logger.info("Services file '" + name + "' updated, parsing...");
      parseServicesFile();
      CardServProxy.logger.info("Services: " + services.size() + " Conflicts: " + serviceConflicts);
    } catch(Exception e) {
      CardServProxy.logger.severe("Unable to parse services file '" + name + "': " + e, e);
    }
  }

  public void startListening(CamdMessageListener listener) {
    this.listener = listener;
    for(Iterator iter = listenPorts.iterator(); iter.hasNext(); ) {
      ((ListenPort)iter.next()).start(listener, this);
    }
  }

  public List getListenPorts() {
    return new ArrayList(listenPorts);
  }

  protected void addListenPort(ListenPort lp) {
    if(!listenPorts.contains(lp)) listenPorts.add(lp);
  }

  protected void removeListenPort(ListenPort lp) {
    if(lp != null) listenPorts.remove(lp);    
  }

  public int getNewcamdPortCount() {
    if(this == CaProfile.MULTIPLE) return 1;
    int count = 0;
    for(Iterator iter = listenPorts.iterator(); iter.hasNext(); ) {
      if("Newcamd".equalsIgnoreCase(((ListenPort)iter.next()).getProtocol())) count++;
    }
    return count;
  }

  public int getSessionCount() {
    return SessionManager.getInstance().getSessionCount(name);    
  }

  public long getMaxCwWait() {
    return maxCwWait;
  }

  public long getCongestionLimit() {
    return congestionLimit;
  }

  public String toString() {
    if(this == MULTIPLE) return "Ca[" + name + "]";
    else return "Ca[" + name + ":" + getNetworkIdStr() + ":" + getCaName(caId, false) + "]";
  }

  private static String getCaName(int id) {
    return getCaName(id, true);
  }

  public Set getProviderSet() {
    CwsConnectorManager cm = ProxyConfig.getInstance().getConnManager();
    Set set = cm==null?new TreeSet():cm.getMergedProviders(name);
    set.addAll(predefinedProviders);
    return set;
  }

  public Integer[] getProviderIdents() {
    Set set = getProviderSet();
    return (Integer[])set.toArray(new Integer[set.size()]);
  }

  public String getProviderIdentsStr() {
    return ProxyConfig.providerIdentsToString(getProviderSet());
  }

  public ServiceMapping[] getServices(boolean canDecode) {
    if(this == MULTIPLE) throw new IllegalStateException("getServices(" + canDecode +") called on profile *.");
    return ProxyConfig.getInstance().getConnManager().getServicesForProfile(name, canDecode);
  }

  private static String getCaName(int id, boolean includeName) {
    String caId = Integer.toHexString(id);
    while(caId.length() < 4) caId = "0" + caId;
    if(!includeName) return caId;
    String caName = caNames.getProperty(caId.toUpperCase());
    if(caName == null) caName = caNames.getProperty(caId.substring(0, 2).toUpperCase() + "00");
    if(caName == null) return "Unknown:" + caId;
    else return caName + ":" + caId;
  }

  private static Properties caNames;

  static {
    caNames = new Properties();
    try {
      caNames.load(CaProfile.class.getResourceAsStream("ca.properties"));
    } catch(IOException e) {
      e.printStackTrace();
    }
  }


}
