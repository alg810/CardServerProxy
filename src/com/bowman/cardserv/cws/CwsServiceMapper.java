package com.bowman.cardserv.cws;

import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.*;
import com.bowman.cardserv.session.CspSession;

import java.io.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2006-feb-26
 * Time: 12:58:06
 */
public class CwsServiceMapper implements XmlConfigurable {

  private static final String CACHE_FILESUFFIX = "_servicemap.dat";

  private ProxyLogger logger;
  private ProxyConfig config;

  private boolean enabled;

  private Map connectors = new HashMap();
  private Map canDecodeMap = new HashMap();
  private Map cannotDecodeMap = new HashMap();
  private Map failureCountMap = new HashMap();
  private Map failureMap = new HashMap();
  private Set rediscoverSet = new HashSet();

  Map overrideCanDecodeMap = new HashMap();
  Map overrideCannotDecodeMap = new HashMap();
  Set exclusiveConnectors = new HashSet();

  private long cacheTimeStamp;
  private boolean cacheUpdated;
  private long cacheSaveAge;

  private int autoExcludeThreshold;
  private boolean autoDiscoverAll, retryLostServices, hideUnknownServices, hideDisabledConnectors;
  private boolean logMissingSid, bcMissingSid, redundantForwarding;

  private File cacheDir;
  private Set resetServices = new HashSet();
  private Set blockedServices = new HashSet();
  private Set allowedServices = new HashSet();
  private Set unknownServices = new LinkedHashSet();
  private String resetSrvStr, blockedSrvStr, allowedSrvStr;

  private CaProfile profile;
  private CwsConnectorManager cm;

  public CwsServiceMapper(CaProfile profile, CwsConnectorManager cm) {
    this.profile = profile;
    this.cm = cm;

    logger = ProxyLogger.getLabeledLogger(getClass().getName(), "ServiceMapper[" + profile.getName() + "]");
    config = ProxyConfig.getInstance();
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {

    enabled = "true".equalsIgnoreCase(xml.getStringValue("enabled", "true"));
    if(!enabled) {
      logger.warning("Service mapping disabled for profile: " + profile.getName());
      return;
    }     

    cacheSaveAge = xml.getTimeValue("cache-save-age", 300, "s");
    cacheDir = new File(xml.getFileValue("cache-dir", "cache", true, true));
    autoDiscoverAll = "true".equalsIgnoreCase(xml.getStringValue("auto-map-services", "true"));
    logMissingSid = "true".equalsIgnoreCase(xml.getStringValue("log-missing-sid", "true"));
    bcMissingSid = "true".equalsIgnoreCase(xml.getStringValue("broadcast-missing-sid", "false"));
    redundantForwarding = "true".equalsIgnoreCase(xml.getStringValue("redundant-forwarding", "false"));

    String unknownList = null;
    try {
      unknownList = xml.getStringValue("dummy-services");
    } catch (ConfigException e) {
      unknownServices = new HashSet();
    }
    if(unknownList != null) unknownServices = ProxyXmlConfig.getIntTokens("dummy-services", unknownList);
    try {
      int unknownSid = Integer.parseInt(xml.getStringValue("unknown-sid"), 16);
      unknownServices.add(new Integer(unknownSid));
    } catch (ConfigException e) {
      // ignore
    } catch (NumberFormatException e) {
      throw new ConfigException(xml.getFullName(), "unknown-sid", "Bad hex integer value: " + e.getMessage());
    }

    String resetList = null;
    try {
      resetList = xml.getStringValue("reset-services");
    } catch (ConfigException e) {}
    if(resetList != null) resetServices = ProxyConfig.getServiceTokens("reset-services", resetList, false);
    else resetServices = Collections.EMPTY_SET;
    resetSrvStr = null;

    String blockedList = null;
    try {
      blockedList = xml.getStringValue("block-services");
    } catch (ConfigException e) {}
    if(blockedList != null) blockedServices = ProxyConfig.getServiceTokens("block-services", blockedList, false);
    else blockedServices = new HashSet();
    blockedSrvStr = null;

    String allowedList = null;
    try {
      allowedList = xml.getStringValue("allow-services");
    } catch (ConfigException e) {}
    if(allowedList != null) allowedServices = ProxyConfig.getServiceTokens("allow-services", allowedList, true);
    else allowedServices = Collections.EMPTY_SET;
    allowedSrvStr = null;

    autoExcludeThreshold = xml.getIntValue("auto-reset-threshold", -1);
    retryLostServices = "true".equalsIgnoreCase(xml.getStringValue("retry-lost-services", "true"));
    hideUnknownServices = "true".equalsIgnoreCase(xml.getStringValue("hide-unknown-services", "false"));
    hideDisabledConnectors = "true".equalsIgnoreCase(xml.getStringValue("hide-disabled-connectors", "false"));

    overrideCanDecodeMap.clear();
    overrideCannotDecodeMap.clear();
  }

  public String getResetServicesStr() {
    if(resetSrvStr != null) return resetSrvStr;
    else {
      if(!resetServices.isEmpty()) resetSrvStr = serviceTokensToStr(resetServices);
      return resetSrvStr;
    }
  }

  public String getBlockedServicesStr() {
    if(blockedSrvStr != null) return blockedSrvStr;
    else {
      if(!blockedServices.isEmpty()) blockedSrvStr = serviceTokensToStr(blockedServices);
      return blockedSrvStr;
    }
  }

  public String getAllowedServicesStr() {
    if(allowedSrvStr != null) return allowedSrvStr;
    else {
      if(!allowedServices.isEmpty()) allowedSrvStr = serviceTokensToStr(allowedServices);
      return allowedSrvStr;
    }
  }

  private String serviceTokensToStr(Set set) {
    if(set.size() > 25) return "[ ... " + set.size() + " ... ]";
    ServiceMapping sm; TvService ts;
    StringBuffer sb = new StringBuffer("[");
    ProxyConfig config = ProxyConfig.getInstance();
    for(Iterator iter = set.iterator(); iter.hasNext(); ) {
      sm = (ServiceMapping)iter.next();
      ts = config.getService(profile.getName(), sm);
      sb.append(ts.getName());
      if(!ts.isUnknown()) sb.append(" (").append(Integer.toHexString(ts.getId())).append(')');
      if(iter.hasNext()) sb.append(", ");
    }
    sb.append(']');
    return sb.toString();
  }

  synchronized void loadServiceMaps() {
    cacheUpdated = false;
    cacheTimeStamp = System.currentTimeMillis();
    try {
      File mapFile = new File(cacheDir, profile.getName() + CACHE_FILESUFFIX);
      ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(mapFile)));
      canDecodeMap = (Map)ois.readObject();
      cannotDecodeMap = (Map)ois.readObject();
      try {
        rediscoverSet = (Set)ois.readObject();
      } catch (Exception e) {
        rediscoverSet = new HashSet();
      }
      ois.close();

      convertMap(canDecodeMap);
      convertMap(cannotDecodeMap);

      logger.info("Loaded service maps. Can decode [" + canDecodeMap.size() + "] services, cannot decode [" +
          cannotDecodeMap.size() + "], rediscover tasks: [" + rediscoverSet.size() + "]");

    } catch (FileNotFoundException e) {
      logger.fine("No service map cache file found");
    } catch (Exception e) {
      logger.severe("Failed to load channel map cache: " + e, e);
    }
  }

  private void convertMap(Map map) {
    Object k, v;
    for(Iterator iter = new ArrayList(map.keySet()).iterator(); iter.hasNext(); ) {
      k = iter.next();
      if(k instanceof Integer) {
        v = map.remove(k);
        map.put(new ServiceMapping(((Integer)k).intValue(), 0), v);
      }
    }
  }

  synchronized void saveServiceMaps() {
    saveServiceMaps(false);
  }

  synchronized void saveServiceMaps(boolean force) {
    if(!enabled) return;
    if(force) cacheTimeStamp = 0;
    long now = System.currentTimeMillis();
    if(now - cacheTimeStamp < cacheSaveAge) return;
    logger.fine("Cache is older than " + cacheSaveAge / 1000 + " seconds, saving...");
    if(cacheUpdated) {
      cacheUpdated = false;

      try {
        File mapFile = new File(cacheDir, profile.getName() + CACHE_FILESUFFIX);
        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(mapFile)));
        oos.writeObject(canDecodeMap);
        oos.writeObject(cannotDecodeMap);
        oos.writeObject(rediscoverSet);
        oos.flush();
        oos.close();
      } catch (IOException e) {
        logger.severe("Failed to save service map cache: " + e);
        logger.throwing(e);
      } catch (Exception e) {
        logger.warning("Failed to save service map cache: " + e);
        logger.throwing(e);
      }

    } else logger.fine("Cache not changed, skipping...");
    cacheTimeStamp = now;
  }

  CwsConnector getCwsConnector() {
    // get least loaded ready connector, for testing
    List ready = getReadyConnectors();
    if(ready.isEmpty()) return null;
    else {
      CwsConnector[] conns = selectLeastLoaded(ready);
      if(conns == null || conns.length == 0) return null;
      else return conns[0]; // todo
    }
  }

  Map getConnectors() {
    Map tmp = new HashMap();
    CwsConnector conn;
    for(Iterator iter = connectors.values().iterator(); iter.hasNext(); ) {
      conn = (CwsConnector)iter.next();
      if(conn.isEnabled() || !hideDisabledConnectors) tmp.put(conn.getName(), conn);
    }
    return tmp;
  }

  List getConnectors(ServiceMapping id, boolean canDecode) {
    List names = (List)(canDecode?canDecodeMap.get(id):cannotDecodeMap.get(id));
    if(names == null) names = new ArrayList();
    else names = new ArrayList(names); // ensure copy

    // add manual overrides
    names.addAll(getOverrideConnectors(id, canDecode));
    if(names.isEmpty()) return Collections.EMPTY_LIST;

    // if(canDecode) names.removeAll(getOverrideConnectors(id, false));
    names.removeAll(getOverrideConnectors(id, !canDecode));

    List tmp = new ArrayList();
    String name; CwsConnector conn;
    for(Iterator iter = names.iterator(); iter.hasNext(); ) {
      name = (String)iter.next();
      conn = (CwsConnector)connectors.get(name);
       // check for csp-connectors not registered with this profile
      if(conn == null) conn = (CwsConnector)cm.getMultiConnectors(profile.getNetworkId(), profile.getCaId()).get(name);
      if(conn == null) {
        List l = (List)(canDecode?canDecodeMap.get(id):cannotDecodeMap.get(id));
        if(l != null) {
          if(l.remove(name)) logger.info("Removing unknown connector name '" + name +
              "' from service maps for service [" + config.getServiceName(profile.getName(), id.serviceId) + "]");
        }
      } else if(conn.isReady()) tmp.add(conn);
    }
    return tmp;
  }

  Collection getOverrideConnectors(ServiceMapping id, boolean canDecode) {
    List names = (List)(canDecode?overrideCanDecodeMap.get(id):overrideCannotDecodeMap.get(id));
    if(names == null) return Collections.EMPTY_LIST;
    else return new ArrayList(names);
  }

  List getServicesForConnector(String cwsName, boolean canDecode, boolean raw) {
    List services = new ArrayList();
    if(!enabled) return services;
    ServiceMapping id; TvService service;
    Map map = canDecode?canDecodeMap:cannotDecodeMap;
    Map overrideMap1 = canDecode?overrideCanDecodeMap:overrideCannotDecodeMap;
    Map overrideMap2 = !canDecode?overrideCanDecodeMap:overrideCannotDecodeMap;

    Set sids = new HashSet(map.keySet());
    sids.addAll(overrideMap1.keySet()); // add manual overrides

    for(Iterator iter = sids.iterator(); iter.hasNext(); ) {
      id = (ServiceMapping)iter.next();
      if(id.getCustomData() == -1) continue;  // hide entries with customId -1
      Set tmp = new HashSet();
      if(map.containsKey(id)) try {
        tmp.addAll((List)map.get(id));
      } catch (Exception e) {
        tmp.addAll((List)map.get(id));
      }
      if(overrideMap1.containsKey(id)) tmp.addAll((List)overrideMap1.get(id)); // add manual overrides
      if(overrideMap2.containsKey(id)) tmp.removeAll((List)overrideMap2.get(id)); // remove opposing

      if(tmp.contains(cwsName)) {
        if(raw) { // just the sids
          services.add(id);
        } else { // formatted for display
          service = config.getService(profile.getName(), id); // todo?
          if(service == null) {
            if(!hideUnknownServices) services.add(TvService.getUnknownService(profile.getName(), id.serviceId));
          } else if(service.isTv())
            services.add(service);
          }
      }
    }
    if(!raw) Collections.sort(services);
    return services;
  }
  
  List getReadyConnectors() {
    List tmp = new ArrayList();
    CwsConnector conn;
    for(Iterator iter = connectors.values().iterator(); iter.hasNext(); ) {
      conn = (CwsConnector)iter.next();
      if(conn.isReady()) tmp.add(conn);
    }
    return tmp;
  }

  private List getConnectorsUnknownChannelStatus(ServiceMapping id) {
    List tmp = new ArrayList();
    if(!enabled) return tmp;
    CwsConnector conn;
    for(Iterator iter = getReadyConnectors().iterator(); iter.hasNext(); ) {
      conn = (CwsConnector)iter.next();
      if(canDecode(conn, id) == null) tmp.add(conn);
    }
    // add any multi connectors with this network as well
    for(Iterator iter = cm.getMultiConnectors(profile.getNetworkId(), profile.getCaId()).values().iterator(); iter.hasNext(); ) {
      conn = (CwsConnector)iter.next();
      if(canDecode(conn, id) == null) tmp.add(conn);
    }
    return tmp;
  }


  List getConnectorsForProvider(String provider) {
    List tmp = new ArrayList();
    CwsConnector conn; Set providers;
    for(Iterator iter = getReadyConnectors().iterator(); iter.hasNext(); ) {
      conn = (CwsConnector)iter.next();
      providers = new HashSet(Arrays.asList(conn.getRemoteCard().getProviders()));
      if(providers.contains(provider)) tmp.add(conn);
    }
    return tmp;
  }

  ConnectorSelection getConnectorsForService(ServiceMapping id, Set allowedConnectorNames) {
    if(id.serviceId > 0) {

      if(id.getCustomId() == 0 && id.getProviderIdent() == ServiceMapping.NO_PROVIDER) { // match using existing id
        if(!allowedServices.isEmpty())
          if(!allowedServices.contains(id)) return ConnectorSelection.EMPTY;
        if(blockedServices.contains(id)) return ConnectorSelection.EMPTY;
      } else {
        ServiceMapping sm = new ServiceMapping(id.serviceId, 0); // remove customdata before match (use sid only)
        sm.setProviderIdent(ServiceMapping.NO_PROVIDER);

        if(!allowedServices.isEmpty())
          if(!allowedServices.contains(sm)) return ConnectorSelection.EMPTY; // check with sid only against allow list
        if(blockedServices.contains(sm) || blockedServices.contains(id)) return ConnectorSelection.EMPTY; // check with both against block
      }
    }

    if(!enabled) id.serviceId = -1; // service mapping is off, behave like sid = 0
    ConnectorSelection result = getConnectorsForServiceInternal(id, allowedConnectorNames);
    if(isServiceUnknown(id.serviceId)) return result;

    if(result == null || result.isEmpty()) {
      // don't count failures when customId is set
      if(id.getCustomId() == 0) incFailures(id);
    }
    if(autoExcludeThreshold > 0) {
      if(getFailures(id) >= autoExcludeThreshold) {
        logger.info(autoExcludeThreshold + " failure(s) for service [" +
            config.getServiceName(profile.getName(), id.serviceId) + "], resetting status...");
        cannotDecodeMap.remove(id);
        failureCountMap.remove(id);
      }
    }
    return result;
  }

  protected boolean isServiceUnknown(int sid) {
    return sid <= 0 || unknownServices.contains(new Integer(sid));
  }

  protected int getUnknownSid() {
    if(unknownServices.isEmpty()) return 0;
    else return ((Integer)unknownServices.iterator().next()).intValue();
  }

  private ConnectorSelection getConnectorsForServiceInternal(ServiceMapping id, Set allowedConnectorNames) {

    String serviceName = config.getServiceName(profile.getName(), id);
    boolean serviceUnknown = false;
    if(isServiceUnknown(id.serviceId)) {
      if(logMissingSid && enabled) {
        if(id.serviceId == 0) logger.warning("Connector for service id [0] requested, no sid in ecm request?");
        else logger.warning("Connector for unknown sid [" + id + "] requested.");
      }
      serviceUnknown = true;
    }

    List canDecode = serviceUnknown?getReadyConnectors():getConnectors(id, true);
    if(serviceUnknown) canDecode.addAll(cm.getMultiConnectors(profile.getNetworkId(), profile.getCaId()).values());
    List unknown = null, ready, secondary = null;

    if(!serviceUnknown) {

      if(canDecode.isEmpty()) {
        // no connectors known to handle this channel, get candidates
        ready = getReadyConnectors();
        if(!ready.isEmpty()) {
          // ready connectors exist, filter out those known _not_ to decode this channel
          List cannotDecode = getConnectors(id, false);
          if(!cannotDecode.isEmpty()) ready.removeAll(cannotDecode);

          if(!ready.isEmpty() && !exclusiveConnectors.isEmpty()) { // or those that have exclusive=true
            CwsConnector cws;
            for(Iterator iter = ready.iterator(); iter.hasNext(); ) {
              cws = (CwsConnector)iter.next();
              if(exclusiveConnectors.contains(cws.getName())) iter.remove();
            }
          }
          
          canDecode = ready;
        }
      }

      if(autoDiscoverAll) {
        // list the cards with status unknown for this channel
        unknown = getConnectorsUnknownChannelStatus(id);
        if(unknown.isEmpty()) unknown = null;
      }
    } else if(bcMissingSid) {
      logger.fine("Scheduling all connectors for probe broadcast. Sid: " + id);
      secondary = getReadyConnectors();
    }

    if(allowedConnectorNames != null) {
      if(!canDecode.isEmpty()) {
        CwsConnector cws; // check user allowed connectors, remove any not allowed
        for(Iterator iter = canDecode.iterator(); iter.hasNext(); ) {
          cws = (CwsConnector)iter.next();
          if(!allowedConnectorNames.contains(cws.getName())) iter.remove();
        }
      }
      if(secondary != null) {
        CwsConnector cws; // repeat for broadcast connectors
        for(Iterator iter = secondary.iterator(); iter.hasNext(); ) {
          cws = (CwsConnector)iter.next();
          if(!allowedConnectorNames.contains(cws.getName())) iter.remove();
        }
      }
    }

    if(canDecode.size() > 1) {
      CwsConnector cws; // remove connectors currently suffering timeouts or congestion, if alternatives exist :)
      for(Iterator iter = new ArrayList(canDecode).iterator(); iter.hasNext(); ) {
        cws = (CwsConnector)iter.next();
        if(cws.getTimeoutCount() > 0) {
          logger.fine(cws + " in timeout-state (" + cws.getTimeoutCount() + "), excluding and sending keep-alive if needed.");
          cws.sendKeepAlive();
          if(canDecode.size() > 1) canDecode.remove(cws);
          else {
            if(config.getConnManager().isHardLimit()) {
              canDecode.remove(cws);
              logger.warning("No candidates remain for service [" + serviceName + "] (timeouts or congestion), " +
                  "returning empty");
            }
          }
        }
        int qTime = cws.getEstimatedQueueTime();
        int qSize = cws.getQueueSize();
        int utilization = cws.getUtilization(false);
        boolean congested = qSize > 0 && qTime > (config.getConnManager().getCongestionLimit(profile) - cws.getAverageEcmTime());
        if(utilization > 100 || congested) {
          if(canDecode.size() > 1) canDecode.remove(cws);
          else {
            if(congested && config.getConnManager().isHardLimit()) {
              canDecode.remove(cws);
            }
            String warn = cws + " congested and no alternatives exists for service [" + serviceName + "], queue " +
                "estimate is: " + qTime + " ms (" + qSize + " requests pending, " + utilization + "% utilization)" +
                ((congested && config.getConnManager().isHardLimit())?", returning empty...":"");
            if(config.isDebug()) logger.warning(warn);
            else logger.fine(warn);
          }

        }
      }
    }

    if(!serviceUnknown) canDecode = selectByCwsMetric(canDecode); // determine which metric level connectors to include

    CwsConnector[] twoLeastLoaded = selectLeastLoaded(canDecode);
    CwsConnector leastLoaded;
    if(twoLeastLoaded == null || twoLeastLoaded.length == 0) {
      leastLoaded = null;
    } else {
      leastLoaded = twoLeastLoaded[0];
      if(redundantForwarding && twoLeastLoaded.length > 1) {
        if(secondary == null) secondary = new ArrayList();
        if(!secondary.contains(twoLeastLoaded[1])) secondary.add(twoLeastLoaded[1]);
        secondary.remove(leastLoaded);
        if(secondary.isEmpty()) secondary = null;
      }
    }

    if(unknown != null) {
      if(!serviceUnknown) logger.fine("Unknown status for service [" + serviceName + "] on connectors: " + unknown);
      if(leastLoaded == null) {
        leastLoaded = selectLeastLoaded(unknown)[0];
        unknown.remove(leastLoaded);
      } else unknown.remove(leastLoaded);
      if(unknown.isEmpty()) unknown = null;
    } else {
      if(leastLoaded != null)
        if(!serviceUnknown && leastLoaded.getTimeoutCount() > 0)
          logger.info(leastLoaded + " in timeout-state, but no alternatives exist for service [" + serviceName + "].");
    }

    if(secondary != null) {
      if(leastLoaded != null) {
        secondary.remove(leastLoaded);
        if(secondary.isEmpty()) secondary = null;
      }
    }

    return new ConnectorSelection(leastLoaded, secondary, unknown);
  }

  private List selectByCwsMetric(List l) {
    if(l == null || l.size() <= 1) return l;

    // sort connectors by metric, return lowest list
    SortedMap cwsByMetric = new TreeMap();
    CwsConnector cws; Integer key; List tmp;
    for(Iterator iter = l.iterator(); iter.hasNext(); ) {
      cws = (CwsConnector)iter.next();
      key = new Integer(cws.getMetric());
      if(cwsByMetric.containsKey(key)) {
        tmp = (List)cwsByMetric.get(key);
      } else {
        tmp = new ArrayList();
        cwsByMetric.put(key, tmp);
      }
      tmp.add(cws);
    }

    return (List)cwsByMetric.get(cwsByMetric.firstKey());
  }

  private static CwsConnector[] selectLeastLoaded(List l) {
    if(l == null || l.isEmpty()) return null;
    if(l.size() == 1) return new CwsConnector[] {(CwsConnector)l.get(0)};
    Collections.sort(l);
    return new CwsConnector[] {(CwsConnector)l.get(l.size() - 1), (CwsConnector)l.get(l.size() - 2)}; // first one = most recently used, get last two
  }

  void setCanDecode(CamdNetMessage msg, CwsConnector conn, ProxySession session) {
    if(!enabled) return;
    ServiceMapping id = new ServiceMapping(msg);
    setLastFailed(id, conn.getName(), false, -1);
    failureCountMap.remove(id);
    List connectors = (List)canDecodeMap.get(id);
    if(connectors == null) {
      connectors = new ArrayList();
      canDecodeMap.put(id, connectors);
      if(id.getCustomId() != 0)
        if(id.serviceId != 0 && !unknownServices.contains(new Integer(id.serviceId)))
          canDecodeMap.put(new ServiceMapping(id.serviceId), connectors); // also store a sid-only placeholder

    }
    if(!connectors.contains(conn.getName())) {
      connectors.add(conn.getName());
      String source = (session==null?msg.getOriginAddress():session.toString()); // contains origin-session in case of probes
      logger.info("Discovered service [" + config.getServiceName(msg) + "] on CWS: " + conn +
          (source==null?"":" - Ecm source was: " + source));
      if(session != null) session.setFlag(msg, '+');
      cacheUpdated = true;
      config.getConnManager().cwsFoundService(conn, config.getService(msg), !(resetServices.contains(id) || overrideCanDecodeMap.containsKey(id)));
    }
    connectors = (List)cannotDecodeMap.get(id);
    if(connectors != null) {
      if(connectors.remove(conn.getName())) cacheUpdated = true;
    }
  }

  void setCannotDecode(CamdNetMessage msg, CwsConnector conn, ProxySession session) {
    if(!enabled) return;

    if(exclusiveConnectors.contains(conn.getName())) return; // exclusive can-decode connector, we dont want to know what doesnt decode

    ServiceMapping id = new ServiceMapping(msg);

    List connectors = (List)canDecodeMap.get(id);
    if(connectors != null) {

      if(connectors.contains(conn.getName())) { // service was available
        if(isLastFailed(id, conn.getName(), session==null?-1:session.getId())) {
          // only remove for 2 consecutive failures from different sessions
          connectors.remove(conn.getName());
          setLastFailed(id, conn.getName(), false, -1);
          if(session != null) {
            String s = "Service [" + config.getServiceName(msg) + "] no longer available on CWS: " + conn.getName() +
                " - Ecm source was: " + session;
            if(resetServices.contains(id)) logger.info(s);
            else logger.warning(s);
          }
          resetSingle(new ServiceMapping(id.serviceId, -1), conn.getName());
          if(session != null) session.setFlag(msg, '-');
          cacheUpdated = true;
          if(!overrideCanDecodeMap.containsKey(id)) {
            config.getConnManager().cwsLostService(conn, config.getService(msg), !resetServices.contains(id));
          }
          registerRediscovery(conn.getName(), id);
        } else {
          if(overrideCanDecodeMap.containsKey(id)) {
            if(session != null)
              logger.info("Service [" + config.getServiceName(msg) + "] failed to decode on CWS: " + conn.getName() +
                  " (according to the manual can-decode list it should always decode). Ecm source was: " + session);
            return;
          } else {
            if(session != null) {
              String prevFlags = (session instanceof CspSession)?null:session.getLastTransactionFlags();
              String s = "Service [" + config.getServiceName(msg) + "] failed to decode on CWS: "  + conn.getName() +
                  " (two consecutive failures removes mapping). Ecm source was: " + session +
                  (prevFlags==null?"":" [prev: " + prevFlags + "]");
              if(resetServices.contains(id)) logger.info(s);
              else logger.warning(s);
            }
            setLastFailed(id, conn.getName(), true, session==null?-1:session.getId());
            return;
          }
        }
      }

    }
    connectors = (List)cannotDecodeMap.get(id);
    if(connectors == null) {
      connectors = new ArrayList();
      cannotDecodeMap.put(id, connectors);
    }
    if(!connectors.contains(conn.getName())) { // service status was unknown
      connectors.add(conn.getName());
      logger.info("Service [" + config.getServiceName(msg) + "] cannot be decoded by CWS: " + conn.getName());
      cacheUpdated = true;
    }
  }

  void setMultiStatus(ServiceMapping[] sids, CwsConnector conn, boolean canDecode, boolean merge) {
    Map map = canDecode?canDecodeMap:cannotDecodeMap;
    List connectors;
    if(!merge) {
      // remove this connector from all known sids before setting the new state, probably inefficient with this data layuout      
      for(Iterator iter = map.values().iterator(); iter.hasNext(); ) {
        connectors = (List)iter.next();
        if(connectors != null) connectors.remove(conn.getName());
      }
    }

    for(int i = 0; i < sids.length; i++) {
      connectors = (List)map.get(sids[i]);
      if(connectors == null) {
        connectors = new ArrayList();
        map.put(sids[i], connectors);
      }
      if(!connectors.contains(conn.getName())) connectors.add(conn.getName());
    }
  }

  private void registerRediscovery(String cwsName, ServiceMapping id) {
    if(retryLostServices && id.getCustomData() != -1) rediscoverSet.add(new RediscoverTask(cwsName, id));
  }

  private boolean isLastFailed(ServiceMapping id, String connectorName, int sessionId) {
    String key = connectorName + ":" + id;
    return failureMap.containsKey(key) && !(new Integer(sessionId).equals(failureMap.get(key)));
    // lastFailed only if previous failure was from a different session
  }

  private void setLastFailed(ServiceMapping id, String connectorName, boolean failed, int sessionId) {
    String key = connectorName + ":" + id;
    if(failed) failureMap.put(key, new Integer(sessionId));
    else failureMap.remove(key);
  }

  Boolean canDecode(CwsConnector conn, ServiceMapping id) {
    if(isServiceUnknown(id.serviceId)) // no valid sid?
      if(id.getCustomId() <= 0) return null; // return unknown unless there is customdata to go by instead

    List canDecode = (List)canDecodeMap.get(id);
    if(canDecode == null) canDecode = new ArrayList();
    else canDecode = new ArrayList(canDecode);
    List cannotDecode = (List)cannotDecodeMap.get(id);
    if(cannotDecode == null) cannotDecode = new ArrayList();
    else cannotDecode = new ArrayList(cannotDecode);

    // add manual overrides
    canDecode.addAll(getOverrideConnectors(id, true));
    cannotDecode.addAll(getOverrideConnectors(id, false));

    if(canDecode.contains(conn.getName())) return Boolean.TRUE;
    if(cannotDecode.contains(conn.getName())) return Boolean.FALSE;

    // check sid placeholder canDecode list, if found, it indicates the same sid already decodes but with another cid = do not consider this sid + cid combo unknown
    if(id.serviceId > 0 && !unknownServices.contains(new Integer(id.serviceId))) {
      canDecode = (List)canDecodeMap.get(new ServiceMapping(id.serviceId));
      if(canDecode != null && canDecode.contains(conn.getName())) return Boolean.FALSE;
    }

    if(exclusiveConnectors.contains(conn.getName())) return Boolean.FALSE; // no probing for connectors marked exclusive

    return null; // state is unknown
  }

  private void incFailures(ServiceMapping id) {
    Integer count = (Integer)failureCountMap.get(id);
    if(count == null) count = new Integer(1);
    else count = new Integer(count.intValue() + 1);
    failureCountMap.put(id, count);
  }

  int getFailures(ServiceMapping id) {
    Integer count = (Integer)failureCountMap.get(id);
    if(count == null) return 0;
    else return count.intValue();
  }

  void resetListedServices() {
    if(!enabled) return;
    if(!resetServices.isEmpty()) {
      logger.info("Resetting services: " + getHexStr(resetServices));
      for(Iterator iter = resetServices.iterator(); iter.hasNext(); ) {
        resetStatus((ServiceMapping)iter.next());
      }
    }
  }

  private static String getHexStr(Collection c) {
    StringBuffer sb = new StringBuffer();
    for(Iterator iter = c.iterator(); iter.hasNext(); ) {
      sb.append(iter.next()).append(" ");
    }
    return sb.toString().trim();
  }

  boolean resetStatus(ServiceMapping id) {
    boolean removed = canDecodeMap.remove(id) != null;
    if(cannotDecodeMap.remove(id) != null) removed = true;
    if(removed) {
      if(id.getCustomId() > 0) canDecodeMap.remove(new ServiceMapping(id.serviceId)); // remove any placeholder
      logger.info("Status reset for service [" + config.getServiceName(profile.getName(), id.serviceId) + "]");
      failureCountMap.remove(id);
      return true;
    }
    return false;
  }

  int resetStatus(String cwsName, boolean full) {
    List connectors;
    int count = 0;
    for(Iterator iter = new ArrayList(cannotDecodeMap.values()).iterator(); iter.hasNext(); ) {
      connectors = (List)iter.next();
      if(connectors.remove(cwsName)) count++;
    }
    if(full) for(Iterator iter = new ArrayList(canDecodeMap.values()).iterator(); iter.hasNext(); ) {
      connectors = (List)iter.next();
      if(connectors.remove(cwsName)) count++;
    }
    logger.info("Status reset for CWS[" + cwsName +"], " + count + " service entries cleared.");
    failureCountMap.clear();
    return count;
  }

  boolean resetSingle(ServiceMapping id, String cwsName) {
    List cannotDecode = (List)cannotDecodeMap.get(id);
    if(cannotDecode != null) {
      if(cannotDecode.remove(cwsName)) {
        if(id.getCustomData() != -1)
          logger.info("Status reset for service [" + config.getServiceName(profile.getName(), id.serviceId) + "] on CWS[" +
            cwsName +"]");
        if(id.getCustomId() > 0) resetSingle(new ServiceMapping(id.serviceId), cwsName); // remove place holder
        return true;
      }
    }
    return false;
  }

  void addConnector(CwsConnector cws) {
    if(cws.getProfile().getCaId() != profile.getCaId())
      throw new IllegalStateException("Connector CaID doesn't match Mapper!");
    if(!connectors.containsKey(cws.getName())) connectors.put(cws.getName(), cws);
  }

  void removeConnector(CwsConnector cws) {
    connectors.remove(cws.getName());
  }

  public File getCacheDir() {
    return cacheDir;
  }

  public void blockService(int sid) {
    ServiceMapping sm = new ServiceMapping(sid, 0);
    sm.setProviderIdent(ServiceMapping.NO_PROVIDER);
    blockedServices.add(sm);
    logger.fine("Blocked services: " + blockedServices);
    blockedSrvStr = serviceTokensToStr(blockedServices);
  }

  public void unblockService(int sid) {
    ServiceMapping sm = new ServiceMapping(sid, 0);
    sm.setProviderIdent(ServiceMapping.NO_PROVIDER);
    blockedServices.remove(sm);
    logger.fine("Blocked services: " + blockedServices);
    if(blockedServices.isEmpty()) blockedSrvStr = null;
    else blockedSrvStr = serviceTokensToStr(blockedServices);
  }

  public void resetLostStatus() {
    if(!enabled || !retryLostServices) {
      rediscoverSet.clear();
      return;
    }

    RediscoverTask task;
    List canDecode;

    for(Iterator iter = new ArrayList(rediscoverSet).iterator(); iter.hasNext(); ) {
      task = (RediscoverTask)iter.next();
      canDecode = (List)canDecodeMap.get(task.getId());
      if(canDecode != null && canDecode.contains(task.getCwsName())) rediscoverSet.remove(task); // already found
      else {
        if(task.isDue()) {
          resetSingle(task.getId(), task.getCwsName());
          if(task.setChecked()) rediscoverSet.remove(task); // last reset, max interval reached
        }
      }
    }
  }

  static class RediscoverTask implements Serializable {

    static final long MAX_INTERVAL = 48 * 3600 * 1000; // 48h max interval

    private String cwsName;
    private ServiceMapping id;

    private long checkInterval;
    private long nextCheck;

    public RediscoverTask(String cwsName, ServiceMapping id) {
      this.cwsName = cwsName;
      this.id = id;

      this.checkInterval = 5 * 60 * 1000; // 5 mins starting interval
      setChecked();
    }

    public boolean setChecked() {
      nextCheck = System.currentTimeMillis() + checkInterval;
      checkInterval = checkInterval * 2;
      return checkInterval > MAX_INTERVAL;
    }

    public boolean isDue() {
      return System.currentTimeMillis() > nextCheck;
    }

    public String getCwsName() {
      return cwsName;
    }

    public ServiceMapping getId() {
      return id;
    }

    public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;
      RediscoverTask that = (RediscoverTask)o;
      if(!cwsName.equals(that.cwsName)) return false;
      if(!id.equals(that.id)) return false;
      return true;
    }

    public int hashCode() {
      int result = cwsName.hashCode();
      result = 31 * result + id.hashCode();
      return result;
    }

    public String toString() {
      return "[" + cwsName + ":" + id + "] due: " + new Date(nextCheck) + " | interval: " +
          checkInterval / 1000 / 60;
    }
  }

}
