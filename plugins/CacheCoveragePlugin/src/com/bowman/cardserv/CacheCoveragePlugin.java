package com.bowman.cardserv;

import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.session.SessionManager;
import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.web.*;

import java.io.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2011-07-01
 * Time: 21:24
 */
public class CacheCoveragePlugin implements ProxyPlugin, CacheListener {

  protected static final int DEFAULT_CW_VALIDITY = 10000;
  protected ProxyLogger logger;

  private Map cacheMaps = new TreeMap();
  private Map dcwIntervals = new HashMap();
  private ProxyConfig config = ProxyConfig.getInstance();
  private CacheHandler cache;
  private Set commands = new HashSet();

  private Map forwarders = new TreeMap();
  private Map sources = new TreeMap();

  public CacheCoveragePlugin() {
    logger = ProxyLogger.getLabeledLogger(getClass().getName());
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    ProxyXmlConfig contextXml; String key; int iv;
    for(Iterator iter = xml.getMultipleSubConfigs("cache-context"); iter.hasNext(); ) {
      contextXml = (ProxyXmlConfig)iter.next();
      key = CaProfile.getKeyStr(Integer.parseInt(contextXml.getStringValue("network-id"), 16),
          Integer.parseInt(contextXml.getStringValue("ca-id"), 16));
      iv = contextXml.getTimeValue("interval", DEFAULT_CW_VALIDITY / 1000, "s");
      dcwIntervals.put(key, new Integer(iv));
    }

    ProxyXmlConfig forwarderXml; String name; CacheForwarder forwarder;
    for(Iterator iter = xml.getMultipleSubConfigs("cache-forwarder"); iter.hasNext(); ) {
      forwarderXml = (ProxyXmlConfig)iter.next();
      name = forwarderXml.getStringValue("name");
      forwarder = (CacheForwarder)forwarders.get(name);
      if(forwarder == null) {
        forwarder = new CacheForwarder(this, name);
        forwarders.put(name, forwarder);
      }
      forwarder.configUpdated(forwarderXml);
    }
  }  
  
  public boolean lockRequest(int successFactor, CamdNetMessage request) {
    return false;
  }

  public void onRequest(int successFactor, CamdNetMessage request) {
    // do nothing for now
  }

  public int getCwValidityTime(String key) {
    if(dcwIntervals.containsKey(key)) return ((Integer)dcwIntervals.get(key)).intValue();
    else return DEFAULT_CW_VALIDITY;
  }

  public void onReply(CamdNetMessage request, CamdNetMessage reply) {
    if(replyStat(request, reply))
      if(!forwarders.isEmpty()) forwardReply(request, reply);
  }

  public boolean replyStat(CamdNetMessage request, CamdNetMessage reply) {
    ServiceCacheEntry entry = getServiceEntry(request);
    SourceCacheEntry source = getSourceEntry(request);
    return entry.update(request, reply, source);
  }

  private ServiceCacheEntry getServiceEntry(CamdNetMessage request) {
    String profileKey = CaProfile.getKeyStr(request.getNetworkId(), request.getCaId());
    TvService ts = config.getService(request);
    // allow TvService class to be used even for profiles that dont exist locally
    if(ts.getProfileName() == null || "*".equals(ts.getProfileName())) ts = new TvService(ts, 0, profileKey);
    int validity = getCwValidityTime(profileKey);
    CacheCoverageMap map;
    if(cacheMaps.containsKey(profileKey)) {
      map = (CacheCoverageMap)cacheMaps.get(profileKey);
    } else {
      map = new CacheCoverageMap(profileKey, validity * 2);
      cacheMaps.put(profileKey, map);
    }
    ServiceCacheEntry entry = (ServiceCacheEntry)map.get(ts);
    if(entry == null) {
      entry = new ServiceCacheEntry(ts, request, validity, map);
      map.put(ts, entry);
    }
    return entry;
  }

  private SourceCacheEntry getSourceEntry(CamdNetMessage request) {
    String sourceStr = SourceCacheEntry.getSourceStr(request);
    SourceCacheEntry entry = (SourceCacheEntry)sources.get(sourceStr);
    if(entry == null) {
      entry = new SourceCacheEntry(sourceStr);
      sources.put(sourceStr, entry);
      if(entry.isLocal()) {
        List list = SessionManager.getInstance().getSessionsIP(entry.ipStr);
        if(list != null) {
          if(!list.isEmpty()) entry.label = list.iterator().next().toString();
          if(list.size() > 1) entry.label += " (" + list.size() + ")";
        }
      } else entry.label = "Remote ClusteredCache";
    }
    return entry;
  }

  public void forwardReply(CamdNetMessage request, CamdNetMessage reply) {
    CacheForwarder forwarder;
    for(Iterator iter = forwarders.values().iterator(); iter.hasNext(); ) {
      forwarder = (CacheForwarder)iter.next();
      if(forwarder.isConnected()) forwarder.forwardReply(request, reply);
    }
  }

  public void start(CardServProxy proxy) {
    cache = config.getCacheHandler();
    if(cache != null) cache.setMonitor(this);
    if(commands.isEmpty()) registerCommands();
  }

  public void stop() {
    if(cache != null) cache.setMonitor(null);
    unregisterCommands();
    for(Iterator iter = forwarders.values().iterator(); iter.hasNext(); ) {
      ((CacheForwarder)iter.next()).close();
    }
  }

  private void registerCommands() {
    /*
    try {
      commands.addAll(XmlHelper.registerControlCommands(CacheCoveragePlugin.class.getResourceAsStream("ctrl-commands.xml"), this, null));
    } catch (Exception e) {
      logger.severe("Failed to load/parse internal control commands (ctrl-commands.xml).", e);
    }
    */
    try {
      commands.addAll(XmlHelper.registerStatusCommands(CacheCoveragePlugin.class.getResourceAsStream("status-commands.xml"), this, null));
    } catch (Exception e) {
      logger.severe("Failed to load/parse internal status commands (status-commands.xml).", e);
    }
  }

  private void unregisterCommands() {
    Command cmd;
    for(Iterator iter = commands.iterator(); iter.hasNext(); ) {
      cmd = (Command)iter.next();
      cmd.unregister();
      iter.remove();
    }
  }

  public String getName() {
    return "CacheCoveragePlugin";
  }

  public String getDescription() {
    return "Assists with cache monitoring and propagation.";
  }

  public Properties getProperties() {
    Properties p = new Properties();
    String key; TreeSet set;
    for(Iterator iter = cacheMaps.keySet().iterator(); iter.hasNext(); ) {
      key = (String)iter.next();
      set = new TreeSet(((Map)cacheMaps.get(key)).values());
      p.setProperty(key, set.size() + " services");
    }
    return p;
  }

  public void runStatusCmdCacheForwarders(XmlStringBuffer xb) {
    xmlFormatCacheForwarders(xb);
  }

  public void runStatusCmdCacheContents(XmlStringBuffer xb, Map params) {
    boolean hideExpired = "true".equals(params.get("hide-expired"));
    boolean showMissing = "true".equals(params.get("show-missing"));
    String sourceStr = (String)params.get("source-filter");
    String excludeStr = (String)params.get("exclude-keys");
    Set excludedKeys = excludeStr==null?Collections.EMPTY_SET:new HashSet(Arrays.asList(excludeStr.split(",")));
    if("".equals(sourceStr)) sourceStr = null;
    SourceCacheEntry source = null;
    if(sourceStr != null) source = (SourceCacheEntry)sources.get(sourceStr.toUpperCase());
    xmlFormatCacheContents(xb, hideExpired, showMissing, source, excludedKeys);
  }

  public void runStatusCmdServiceBacklog(XmlStringBuffer xb, Map params) {
    int sid = -1;
    String sidStr = (String)params.get("sid");
    if(sidStr.startsWith("0x")) sid = Integer.parseInt(sidStr.substring(2), 16);
    else sid = Integer.parseInt(sidStr);
    int onid = Integer.parseInt((String)params.get("onid"), 16);
    int caid = Integer.parseInt((String)params.get("caid"), 16);

    String profileKey = CaProfile.getKeyStr(onid, caid);
    Map map = (Map)cacheMaps.get(profileKey);
    CaProfile profile = config.getProfileById(onid, caid);
    String profileName = profile==null?null:profile.getName();
    TvService ts = config.getService(profileName, sid);
    if(ts.getProfileName() == null || "*".equals(ts.getProfileName())) ts = new TvService(ts, 0, profileKey);
    xmlFormatServiceBacklog(xb, (ServiceCacheEntry)map.get(ts));
  }

  public void runStatusCmdCacheSources(XmlStringBuffer xb, Map params) {
    boolean hideLocal = "true".equals(params.get("hide-local"));
    String name = (String)params.get("name");
    xmlFormatCacheSources(xb, hideLocal, name);
  }

  public void runStatusCmdListTransponders(XmlStringBuffer xb, Map params) {
    String profile = (String)params.get("profile");
    String tidStr = (String)params.get("tid");
    int tid = -1;
    if(tidStr != null) {
      if(tidStr.startsWith("0x")) tid = Integer.parseInt(tidStr.substring(2), 16);
      else tid = Integer.parseInt(tidStr);
    }
    if(profile != null) xmlFormatTransponderList(xb, profile, tid);
  }

  public void xmlFormatTransponderList(XmlStringBuffer xb, String profileName, int tidFilter) {
    xb.appendElement("transponder-list", "profile", profileName);
    CaProfile profile = config.getProfile(profileName);
    if(profile != null) {
      Map allServices = profile.getServices();
      Map tpMap = new TreeMap();
      TransponderEntry entry;
      TvService ts; String key;
      for(Iterator iter = allServices.values().iterator(); iter.hasNext(); ) {
        ts = (TvService)iter.next();
        key = profileName + "-" + Long.toHexString(ts.getTransponder());
        entry = (TransponderEntry)tpMap.get(key);
        if(entry == null) {
          entry = new TransponderEntry(ts.getTransponder(), profileName);
          tpMap.put(key, entry);
        }
        entry.addService(ts);
      }
      xb.appendAttr("count", tpMap.size());
      xb.endElement(false);
      for(Iterator iter = tpMap.values().iterator(); iter.hasNext(); ) {
        entry = (TransponderEntry)iter.next();
        if(tidFilter == -1) xmlFormatTransponder(xb, entry);
        else if(tidFilter == entry.tid) xmlFormatTransponder(xb, entry);
      }
    }
    xb.closeElement("transponder-list");
  }

  public void xmlFormatTransponder(XmlStringBuffer xb, TransponderEntry entry) {
    xb.appendElement("transponder", "id", (int)entry.tid);
    xb.appendAttr("service-count", entry.services.size());
    xb.appendAttr("sd-count", entry.sd);
    xb.appendAttr("hd-count", entry.hd);
    xb.appendAttr("radio-count", entry.radio);
    xb.endElement(false);
    XmlHelper.xmlFormatServices((TvService[])entry.services.toArray(new TvService[entry.services.size()]), xb, false, false, null);
    xb.closeElement("transponder");
  }

  public void xmlFormatCacheContents(XmlStringBuffer xb, boolean hideExpired, boolean showMissing, SourceCacheEntry filter, Set excludedKeys) {
    xb.appendElement("cache-contents", "contexts", cacheMaps.size());
    xb.appendAttr("sources", sources.size());
    xb.endElement(false);
    String key; String[] s; CacheCoverageMap map; CaProfile profile;
    for(Iterator iter = cacheMaps.keySet().iterator(); iter.hasNext(); ) {
      key = (String)iter.next();
      s = key.split("-");
      map = (CacheCoverageMap)cacheMaps.get(key);
      xb.appendElement("cache-context", "key", key);
      profile = getProfileByKey(key);
      if(profile != null) xb.appendAttr("local-name", profile.getName());
      xb.appendAttr("onid", s[0]);
      xb.appendAttr("caid", s[1]);
      xb.appendAttr("expected-interval", getCwValidityTime(key));
      xb.appendAttr("total-seen", map.size());
      xb.endElement(false);
      if(!excludedKeys.contains(key)) {
        Set set = new TreeSet(map.values());
        if(profile != null && showMissing) {
          TvService ts;
          for(Iterator i = profile.getServices().values().iterator(); i.hasNext(); ) {
            ts = (TvService)i.next();
            if(ts.isTv() && ts.getType() != TvService.TYPE_RADIO)
              if(!map.containsKey(ts)) set.add(new ServiceCacheEntry(ts, null, getCwValidityTime(key), map));
          }
        }
        xmlFormatCacheContext(xb, set, hideExpired, filter);
      }
      xb.closeElement("cache-context");
    }
    xb.closeElement("cache-contents");
  }

  public void xmlFormatCacheForwarders(XmlStringBuffer xb) {
    xb.appendElement("cache-forwarders", "count", forwarders.size());
    CacheForwarder forwarder;
    for(Iterator iter = forwarders.values().iterator(); iter.hasNext(); ) {
      forwarder = (CacheForwarder)iter.next();
      xb.appendElement("forwarder", "name", forwarder.getName());
      xb.appendAttr("connected", forwarder.isConnected());
      xb.appendAttr("avg-latency", forwarder.getAvgLatency());
      xb.appendAttr("peak-latency", forwarder.getPeakLatency());
      xb.appendAttr("avg-rsize", forwarder.getAvgRecordSize());
      xb.appendAttr("peak-rsize", forwarder.getPeakRecordSize());
      xb.appendAttr("msg-count", forwarder.getCount());
      xb.appendAttr("reconnects", forwarder.getReconnects());
      xb.appendAttr("errors", forwarder.getErrors());
      xb.endElement(true);
    }
    xb.closeElement("cache-forwarders");
  }

  public void xmlFormatCacheSources(XmlStringBuffer xb, boolean hideLocal, String name) {
    xb.appendElement("cache-sources", "count", sources.size());
    SourceCacheEntry source;
    for(Iterator iter = sources.values().iterator(); iter.hasNext(); ) {
      source = (SourceCacheEntry)iter.next();
      if(hideLocal && source.isLocal()) continue;
      if(name != null && !source.sourceStr.equalsIgnoreCase(name)) continue;
      xb.appendElement("source", "name", source.sourceStr);
      xb.appendAttr("label", source.label);
      xb.appendAttr("update-count", source.updateCount);
      xb.appendAttr("aborts", source.abortCount);
      xb.appendAttr("overwrites", source.overwriteCount);
      xb.appendAttr("duplicates", source.duplicateCount);
      xb.endElement(true);
    }
    xb.closeElement("cache-sources");
  }

  public void xmlFormatServiceBacklog(XmlStringBuffer xb, ServiceCacheEntry entry) {
    if(entry == null) return;
    Map backLog = entry.getBackLog();
    xb.appendElement("service-backlog", "size", backLog.size());
    xb.appendAttr("label", entry.ts.toString());
    xb.endElement(false);
    SourceCacheEntry source;
    xb.appendElement("current-sources", "window-size", ServiceCacheEntry.WINDOW_SIZE);
    for(Iterator iter = entry.getSources(true).iterator(); iter.hasNext(); ) {
      source = (SourceCacheEntry)iter.next();
      xb.appendElement("source", "name", source.sourceStr);
      xb.appendAttr("label", source.label);
      xb.endElement(true);
    }
    xb.closeElement("current-sources");
    CamdNetMessage request, reply; List list = new ArrayList(backLog.keySet());
    Collections.reverse(list);  long prev = -1, first = -1;
    for(Iterator iter = list.iterator(); iter.hasNext(); ) {
      request = (CamdNetMessage)iter.next();
      reply = (CamdNetMessage)backLog.get(request);
      if(first == -1) first = request.getTimeStamp();
      xb.appendElement("request", "hash", request.hashCodeStr());
      xb.appendAttr("tag", Integer.toHexString(request.getCommandTag()));
      xb.appendAttr("offset", prev==-1?0:prev - request.getTimeStamp());
      xb.appendAttr("age", first - request.getTimeStamp());
      xb.appendAttr("source", SourceCacheEntry.getSourceStr(request));
      xb.endElement(false);
      xb.appendElement("reply", "data", DESUtil.bytesToString(reply.getCustomData()));
      xb.appendAttr("source", SourceCacheEntry.getSourceStr(reply));
      xb.endElement(true);
      xb.closeElement("request");
      prev = request.getTimeStamp();
    }
    xb.closeElement("service-backlog");
  }

  public CaProfile getProfileByKey(String key) {
    String[] pair = key.split("-");
    return config.getProfileById(Integer.parseInt(pair[0], 16), Integer.parseInt(pair[1], 16));
  }

  public static void xmlFormatCacheContext(XmlStringBuffer xb, Set entries, boolean hideExpired, SourceCacheEntry filter) {
    ServiceCacheEntry sce;

    for(Iterator iter = entries.iterator(); iter.hasNext(); ) {
      sce = (ServiceCacheEntry)iter.next();
      if(hideExpired && sce.isExpired()) continue;
      if(sce.request != null && filter != null && !sce.getSources(false).contains(filter)) continue;
      int avgInterval = sce.getAvgInterval();
      int avgVariance = sce.getAvgVariance();
      xb.appendElement("service");
      xb.appendAttr("name", sce.ts.getName());
      xb.appendAttr("id", sce.ts.getId());
      if(sce.ts.getTransponder() != -1) xb.appendAttr("tid", sce.ts.getTransponder());
      xb.appendAttr("expired", sce.isExpired());
      xb.appendAttr("update-count", sce.getUpdateCount());
      xb.appendAttr("continuity-errors", sce.getContinuityErrors());
      xb.appendAttr("total-continuity-errors", sce.getContinuityErrorsTotal());
      xb.appendAttr("avg-interval", avgInterval==-1?"?":String.valueOf(avgInterval));
      xb.appendAttr("avg-variance", avgVariance==-1?"?":String.valueOf(avgVariance));
      if(sce.request != null) xb.appendAttr("age", sce.getAge());
      else xb.appendAttr("missing", "true");
      if(sce.getMultiple() > 0 ) xb.appendAttr("multiple", sce.getMultiple());
      if(sce.getOverwriteCount() > 0) xb.appendAttr("overwrites", sce.getOverwriteCount());
      if(sce.getDuplicateCount() > 0) xb.appendAttr("duplicates", sce.getDuplicateCount());
      if(sce.getAbortCount() > 0) xb.appendAttr("aborts", sce.getAbortCount());
      xb.appendAttr("offset", sce.getTimeOffset()==-1?"?":String.valueOf(sce.getTimeOffset()));
      xb.appendAttr("sources", sce.getSources(false).size());
      xb.closeElement();
    }
  }

  public CamdNetMessage doFilter(ProxySession session, CamdNetMessage msg) {
    return msg;
  }

  public byte[] getResource(String path, boolean admin) {
    if(path.startsWith("/")) path = path.substring(1);
    try {
      InputStream is = CacheCoveragePlugin.class.getResourceAsStream("/web/" + path);
      if(is == null) return null;
      DataInputStream dis = new DataInputStream(is);
      byte[] buf = new byte[dis.available()];
      dis.readFully(buf);
      return buf;
    } catch (IOException e) {
      return null;
    }
  }

  public byte[] getResource(String path, byte[] inData, boolean admin) {
    return null;
  }

  static class TransponderEntry {

    long tid;
    String profile;
    int radio, sd, hd;

    Set services = new TreeSet();

    TransponderEntry(long tid, String profile) {
      this.tid = tid;
      this.profile = profile;
    }

    void addService(TvService ts) {
      switch(ts.getType()) {
        case TvService.TYPE_TV:
          sd++;
          break;
        case TvService.TYPE_HDTV_MPEG2:
        case TvService.TYPE_HDTV_MPEG4:
          hd++;
          break;
        case TvService.TYPE_RADIO:
          radio++;
          break;
      }
      services.add(ts);
    }
  }

}
