package com.bowman.cardserv;

import com.bowman.cardserv.cws.*;
import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.web.*;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.tv.TvService;
import com.bowman.util.*;

import java.lang.reflect.*;
import java.util.*;
import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Sep 12, 2010
 * Time: 6:06:46 AM
 */
public class SidCacheLinker implements CacheListener, FileChangeListener, CronTimerListener {

  private static final int MAX_DELTA = 5000;

  private ProxyConfig config;
  private CacheHandler cache;
  private Map sidLinksMap = Collections.synchronizedMap(new HashMap());

  private Map sidLockMap = new MessageCacheMap(MAX_DELTA);  // ongoing original requests: sid > orig-req
  private Map requestMap = new MessageCacheMap(20000); // all requests linked to a specific req: orig-req > reqs
  private Map sidRequestMap = new MessageCacheMap(MAX_DELTA); // undecodable held reqs, by sid: sid > req
  private Map replyMap = new MessageCacheMap(20000); // recently received replies that may satisfy late reqs before hold

  private ProxyLogger logger;
  private SidEntry testService;

  private FileWatchdog fw;
  private File linksFile;
  private long linksFileTimeStamp;
  private boolean linksChanged;
  private String defaultProfile;

  private Map addedServices = Collections.synchronizedMap(new HashMap()); // extra services decodable through linking
  private Map requiredServices = Collections.synchronizedMap(new HashMap()); // services that must be in cache for links

  public SidCacheLinker() {
    registerStatusCommands();
  }

  public void init() {
    if(logger == null) {
      logger = ProxyLogger.getLabeledLogger(getClass().getName());
      config = ProxyConfig.getInstance();
      cache = config.getCacheHandler();
      linksFile = new File("etc", "links.cfg");

      loadSidLinkMap();
      registerCtrlCommands();

      fw = new FileWatchdog(linksFile.getPath(), ProxyConfig.DEFAULT_INTERVAL);
      fw.addFileChangeListener(this);
      fw.start();

      CronTimer saveTimer = new CronTimer("* * * * *");
      saveTimer.addTimerListener(this);
      saveTimer.start();
    }
  }

  public void fileChanged(String s) {
    logger.info("File changes detected: " + s);
    loadSidLinkMap();
  }

  public void timeout(CronTimer cronTimer) {
    saveSidLinkMap();
  }

  protected synchronized void loadSidLinkMap() {
    if(!linksFile.exists()) {
      try {
        linksFile.createNewFile();
      } catch(IOException e) {
        logger.throwing(e);
        logger.warning("Unable to create sid links file: " + e);
      }
    } else {
      try {
        sidLinksMap.clear();
        ProxyConfig config = ProxyConfig.getInstance();
        BufferedReader br = new BufferedReader(new FileReader(linksFile));
        String line; String[] links, tokens, profileStr; CaProfile profile; Set tmp;
        while((line = br.readLine()) != null) {
          links = line.split(",");
          tmp = new HashSet();
          for(int i = 0; i < links.length; i++) {
            tokens = links[i].trim().split(":");
            profileStr = tokens[1].split("-");
            profile = config.getProfileById(Integer.parseInt(profileStr[0], 16), Integer.parseInt(profileStr[1], 16));
            if(profile == null) logger.warning("Ignoring links for unknown profile '" + tokens[1] + "' in line: " + line);
            else {
              tmp.add(tokens[0] + ":" + profile.getName());
              if(defaultProfile == null) defaultProfile = profile.getName();
            }
          }
          addMultipleLinks((String[])tmp.toArray(new String[tmp.size()]));          
        }
        br.close();
        Set linkStrs = new TreeSet();
        for(Iterator iter = sidLinksMap.values().iterator(); iter.hasNext(); ) linkStrs.add(iter.next().toString());
        logger.info("Loaded sid links file, " + linkStrs.size() + " collections: " + linkStrs);
        refreshAddedSet();
        linksChanged = false;
      } catch(NullPointerException e) {
        logger.throwing(e);
        logger.fine("Unable to read sid links file, retrying...");
      } catch(Exception e) {
        logger.throwing(e);
        logger.warning("Unable to read sid links file: "+ e);
      }
    }
  }

  protected synchronized void saveSidLinkMap() {
    if(System.currentTimeMillis() - linksFileTimeStamp < 30000 || !linksChanged) return;
    fw.removeFile(linksFile.getPath());

    Set linkStrs = new TreeSet();
    for(Iterator iter = sidLinksMap.values().iterator(); iter.hasNext(); ) linkStrs.add(iter.next().toString());
    try {
      ProxyConfig config = ProxyConfig.getInstance();
      Map profiles = config.getProfiles();
      PrintWriter pw = new PrintWriter(new FileWriter(linksFile, false), false);
      String line; CaProfile profile; String name;
      for(Iterator iter = linkStrs.iterator(); iter.hasNext(); ) {
        line = (String)iter.next();
        line = line.substring(1, line.length() - 1);
        for(Iterator i = profiles.keySet().iterator(); i.hasNext(); ) {
          name = (String)i.next();
          profile = (CaProfile)profiles.get(name);
          if(profile != CaProfile.MULTIPLE)
            line = line.replaceAll(name, profile.getNetworkIdStr() + "-" + DESUtil.intToHexString(profile.getCaId(), 4));
        }
        pw.println(line);
      }
      pw.flush();
      pw.close();
      linksFileTimeStamp = System.currentTimeMillis();
      linksChanged = false;
      logger.fine("Saved sid links, " + linkStrs.size() + " collections.");
    } catch(IOException e) {
      logger.throwing(e);
      logger.warning("Unable to write sid links file: " + e);
    }

    fw.addFile(linksFile.getPath());
  }

  protected void registerCtrlCommands() {
    CtrlCommand cmd;
    try {
      new CtrlCommand("toggle-linker", "Toggle sid linker", "Activate/deactivate sid linking.").register(this);

      cmd = new CtrlCommand("add-link", "Add link", "Add a link between two service ids, indicating they use the same dcw sequence.");
      cmd.addParam("sid1", "Service-id 1");
      cmd.addParam("profile1", "Profile 1").setOptions("@profiles", false);
      cmd.addParam("sid2", "Service-id 2");
      cmd.addParam("profile2", "Profile 2").setOptions("@profiles", false);      
      cmd.register(this);

      cmd = new CtrlCommand("remove-link", "Del link", "Remove a link between two service ids.");
      cmd.addParam("sid1", "Service-id 1");
      cmd.addParam("profile1", "Profile 1").setOptions("@profiles", false);
      cmd.addParam("sid2", "Service-id 2");
      cmd.addParam("profile2", "Profile 2").setOptions("@profiles", false);
      cmd.register(this);

      cmd = new CtrlCommand("test-link", "Test link", "Test mode, links the selected service with any other currently being watched (set same sid again to disable).");
      cmd.addParam("sid", "Service-id");
      cmd.addParam("profile", "Profile").setOptions("@profiles", false);
      cmd.register(this);

      new CtrlCommand("clear-links", "Clear links", "Remove all sid links.").register(this);

    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }
  }

  protected void registerStatusCommands() {
    StatusCommand sCmd;
    try {
      sCmd = new StatusCommand("required-services", "List required services", "List services that need to be permanently in cache for the sid linker to work", false);
      sCmd.register(this);

      sCmd = new StatusCommand("linked-services", "List linked services", "List all services that have been configured for sid-cache-linking", false);
      sCmd.register(this);
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }
  }

  public CtrlCommandResult runCtrlCmdToggleLinker() {
    boolean active = (cache.getListener() == this);
    if(active) cache.setListener(null);
    else cache.setListener(this);
    active = !active;
    return new CtrlCommandResult(true, "Sid linker is now " + (active?"on.":"off."));
  }

  public CtrlCommandResult runCtrlCmdAddLink(Map params) {
    boolean result = false;
    String resultMsg;
    String sid1 = params.get("sid1") + ":" + params.get("profile1");
    String sid2 = params.get("sid2") + ":" + params.get("profile2");
    if(sid1.indexOf("null") != -1 || sid2.indexOf("null") != -1) resultMsg = "Missing/invalid sid1 or sid2 param";
    else {
      result = addSidLink(sid1, sid2);
      resultMsg = "Sid link added: " + sid1 + " <> " + sid2;
    }
    return new CtrlCommandResult(result, resultMsg);
  }

  public CtrlCommandResult runCtrlCmdRemoveLink(Map params) {
    boolean result = false;
    String resultMsg;
    String sid1 = params.get("sid1") + ":" + params.get("profile1");
    String sid2 = params.get("sid2") + ":" + params.get("profile2");
    if(sid1.indexOf("null") != -1 || sid2.indexOf("null") != -1) resultMsg = "Missing/invalid sid1 or sid2 param";
    else {
      result = true;
      removeSidLink(sid1, sid2);
      resultMsg = "Sid link removed: " + sid1 + " <> " + sid2;
    }
    return new CtrlCommandResult(result, resultMsg);
  }

  public CtrlCommandResult runCtrlCmdTestLink(Map params) {
    boolean result = false;
    String resultMsg;
    String profile = (String)params.get("profile");
    String sid = params.get("sid") + ":" + profile;
    SidEntry oldTestService = testService;
    if(sid.indexOf("null") != -1) resultMsg = "Missing/invalid sid";
    else {
      result = true;
      CwsServiceMapper mapper = null;
      if(oldTestService != null) mapper = config.getConnManager().getServiceMapper(oldTestService.profileName);
      SidEntry se = new SidEntry(sid);
      if(se.equals(testService)) {
        testService = null;
        resultMsg = "Test mode disabled";
        if(mapper != null) mapper.unblockService(oldTestService.serviceId);
      } else {
        testService = se;
        resultMsg = "Test link set: " + sid + " <> *";
        if(mapper != null) mapper.unblockService(oldTestService.serviceId);
        mapper = config.getConnManager().getServiceMapper(testService.profileName);
        if(mapper != null) mapper.blockService(testService.serviceId);
      }
    }
    return new CtrlCommandResult(result, resultMsg);
  }

  public CtrlCommandResult runCtrlCmdClearLinks() {
    sidLinksMap.clear();
    testService = null;
    return new CtrlCommandResult(true, "Sid links cleared.");
  }

  public void runStatusCmdRequiredServices(XmlStringBuffer xb, Map params, String user) {
    String[] profiles = (String[])params.get("profiles");
    TvService[] services = (TvService[])requiredServices.keySet().toArray(new TvService[requiredServices.size()]);
    Set opens;
    xb.appendElement("required-services", "count", services.length);
    for(int i = 0; i < services.length; i++) {
      opens = (Set)requiredServices.get(services[i]);
      if(opens != null && !opens.isEmpty()) {
        xb.appendElement("link").appendAttr("id", i + 1).endElement(false);
        xb.appendElement("required");
        XmlHelper.xmlFormatServices(new TvService[] {services[i]}, xb, false, true, true, null, profiles);
        xb.closeElement("required");
        xb.appendElement("opens");
        XmlHelper.xmlFormatServices((TvService[])opens.toArray(new TvService[opens.size()]), xb, false, true, true, null, profiles);
        xb.closeElement("opens");
        xb.closeElement("link");
      }
    }
    xb.closeElement("required-services");
  }

  public void runStatusCmdLinkedServices(XmlStringBuffer xb, Map params, String user) {
    String[] profiles = (String[])params.get("profiles");
    Set linkStrs = new TreeSet();
    Set set; int i = 0;
    xb.appendElement("linked-services", "count", sidLinksMap.values().size());
    for(Iterator iter = sidLinksMap.values().iterator(); iter.hasNext(); ) {
      set = (Set)iter.next();
      if(!linkStrs.contains(set.toString())) {
        xb.appendElement("link").appendAttr("id", (i++) + 1).endElement(false);
        XmlHelper.xmlFormatServices(toTvServiceArray(set), xb, false, true, true, null, profiles);
        xb.closeElement("link");
        linkStrs.add(set.toString());
      }
    }
    xb.closeElement("linked-services");
  }

  private TvService[] toTvServiceArray(Set set) {
    TvService[] ts = new TvService[set.size()];
    SidEntry se; int i = 0; ServiceMapping sm;
    for(Iterator iter = set.iterator(); iter.hasNext(); ) {
      se = (SidEntry)iter.next();
      ts[i] = config.getService(se.profileName, se.serviceId);
      sm = new ServiceMapping(se.serviceId, 0);
      sm.setProviderIdent(ServiceMapping.NO_PROVIDER);
      ts[i].setCustomData(sm.getCustomData());
      i++;
    }
    return ts;
  }

  public boolean lockRequest(int successFactor, CamdNetMessage req) {

    if(req.getProfileName() == null) req.setProfileName(config.getProfileNameById(req.getNetworkId(), req.getCaId()));
    if(req.getProfileName() == null) return false;
    SidEntry se = new SidEntry(req);

    if(sidLockMap.containsKey(se)) { // linked request already in progress
      CamdNetMessage origReq = (CamdNetMessage)sidLockMap.get(se);
      if(System.currentTimeMillis() - origReq.getTimeStamp() < MAX_DELTA) {

        if(replyMap.containsKey(origReq)) { // and reply already received
          logger.fine("Reply already available for: " + req);
          processReply(origReq, req, (CamdNetMessage)replyMap.get(origReq), new SidEntry(origReq).toString(), successFactor == -1);
          return false;
        }

        Set reqs = (Set)requestMap.get(origReq);
        if(reqs == null) reqs = new HashSet();
        reqs.add(req);
        requestMap.put(origReq, reqs); // add this req to those linked to the original
        logger.fine("Link lock detected for: " + req + " - locked by: " + origReq);

        return true;
      }
    }

    if(successFactor == -1 && (sidLinksMap.containsKey(se))) {
      requiredServices.remove(config.getService(req));

      // no request in progress but sid is in a linked set and will not decode locally = always lock for all linked
      Set links = (Set)sidLinksMap.get(se);
      SidEntry s;
      for(Iterator iter = links.iterator(); iter.hasNext();) {
        s = (SidEntry)iter.next();
        if(!s.equals(se)) {
          sidRequestMap.put(s, req);
          logger.fine("Locking locally undecodable req: " + req + " - for: " + s);
        }
      }

      return true;
    }

    return false;
  }

  public void onRequest(int successFactor, CamdNetMessage req) {
    if(successFactor == -1) return; // can't succeed - never create locks
    if(req.getProfileName() == null) req.setProfileName(config.getProfileNameById(req.getNetworkId(), req.getCaId()));
    if(req.getProfileName() == null) return;
    SidEntry se = new SidEntry(req);
    if(sidLockMap.containsKey(se)) return; // already locked

    if(testService != null) { // test mode - lock all with specified sid
      sidLockMap.put(testService, req);
      logger.fine("Test link lock '" + testService + "' created by req: " + req);

    } else if(sidLinksMap.containsKey(se)) { // lock if sid is listed
      Set links = (Set)sidLinksMap.get(se);
      SidEntry s;
      for(Iterator iter = links.iterator(); iter.hasNext();) {
        s = (SidEntry)iter.next();
        if(!s.equals(se)) {
          sidLockMap.put(s, req);
          logger.fine("Sid link lock '" + s + "' created by req: " + req);
        }
      }
    }
  }

  public void onReply(CamdNetMessage req, CamdNetMessage reply) {
    replyMap.put(req, reply);
    Set reqs = null;
    SidEntry se = null;
    boolean undecodable = false;

    if(requestMap.containsKey(req)) { // reply matches specific linked request(s)
      reqs = (Set)requestMap.get(req);
    } else { // reply might match locked undecodable requests

      if(req.getProfileName() == null) req.setProfileName(config.getProfileNameById(req.getNetworkId(), req.getCaId()));
      se = new SidEntry(req);

      Set links = (Set)sidLinksMap.get(se);
      if(links != null) {
        reqs = new HashSet();
        SidEntry s;
        for(Iterator iter = links.iterator(); iter.hasNext(); ) {
          s = (SidEntry)iter.next();
          if(sidRequestMap.containsKey(s)) {
            CamdNetMessage heldReq = (CamdNetMessage)sidRequestMap.remove(s);
            if(System.currentTimeMillis() - heldReq.getTimeStamp() < MAX_DELTA)
              reqs.add(heldReq);
          }
        }
        if(reqs.isEmpty()) reqs = null;
        else undecodable = true;
      }
    }

    if(reqs != null) { // distribute reply to all waiting requests
      if(se == null) se = new SidEntry(req);
      CamdNetMessage linkedReq;
      for(Iterator iter = reqs.iterator(); iter.hasNext(); ) {
        linkedReq = (CamdNetMessage)iter.next();
        logger.fine("Copying reply to linked request: " + reply + " -> " + linkedReq);
        processReply(req, linkedReq, reply, se.toString(), undecodable);
      }      
    }
  }

  private void processReply(CamdNetMessage origReq, CamdNetMessage linkedReq, CamdNetMessage reply, String linkStr, boolean undecodable) {
    if(undecodable && testService == null) reportAddedService(linkedReq, origReq);
    linkedReq.setLinkedService(linkStr);
    reply.setConnectorName("SidCacheLinker");
    cache.processReply(linkedReq, reply);
  }

  public void addMultipleLinks(String[] links) {
    for(int i = 0; i < links.length; i++) {
      if(i + 1 < links.length) {
        addSidLink(links[i], links[i + 1]);
      }
    }
  }

  public boolean addSidLink(String sid1, String sid2) {
    SidEntry se1 = new SidEntry(sid1);
    SidEntry se2 = new SidEntry(sid2);
    Set links = (Set)sidLinksMap.get(se1);
    if(links == null) links = (Set)sidLinksMap.get(se2);
    if(links == null) links = new HashSet();
    links.add(se1);
    links.add(se2);
    boolean added = sidLinksMap.put(se1, links) == null;
    added = sidLinksMap.put(se2, links) == null || added;
    if(added) {
      linksChanged = true;
      return true;
    } else return false;
  }

  public void removeSidLink(String sid1, String sid2) {
    SidEntry se1 = new SidEntry(sid1);
    SidEntry se2 = new SidEntry(sid2);
    Set links = (Set)sidLinksMap.get(se1);
    links.remove(se2);
    boolean removed = (sidLinksMap.remove(se2) != null);
    if(links.size() <= 1) {
      links.remove(se1);
      removed = removed || (sidLinksMap.remove(se1) != null);
    }
    if(removed) {
      linksChanged = true;
      refreshAddedSet();
    }
  }

  private void reportAddedService(CamdNetMessage req, CamdNetMessage origReq) {
    String profileName = req.getProfileName();
    TvService ts1 = config.getService(profileName, origReq.getServiceId());    
    Set services = (Set)requiredServices.get(ts1);
    if(services == null) services = new TreeSet();
    TvService ts2 = config.getService(profileName, req.getServiceId());
    if(ts1.equals(ts2)) return;
    services.add(ts2);
    ServiceMapping sm = new ServiceMapping(req);
    ts2.setCustomData(sm.getCustomData()); // make sure full custom data shows up in the status cmd xml views
    ts1.setCustomData(new ServiceMapping(origReq).getCustomData());
    requiredServices.put(ts1, services); // required service -> all otherwise undecodable services that it unlocks

    services = (Set)addedServices.get(profileName);
    if(services == null) services = new TreeSet();
    if(services.add(sm)) {
      logger.info("Linked previously undecodable service: " + ts2 + " (unlocked by: " + ts1 + ")");
      config.getConnManager().cwsFoundService(null, ts2, true);
    }
    addedServices.put(profileName, services); // profileName -> all services that wouldn't decode without links
  }

  private void refreshAddedSet() {
    SidEntry se; TvService ts; Set retained = new HashSet();
    for(Iterator iter = requiredServices.keySet().iterator(); iter.hasNext(); ) {
      ts = (TvService)iter.next();
      se = new SidEntry(ts.getId(), ts.getProfileName());
      if(!sidLinksMap.containsKey(se)) iter.remove(); // service is no longer linked
      else retained.addAll((Set)requiredServices.get(ts));
    }
    for(Iterator iter = retained.iterator(); iter.hasNext(); ) {
      ts = (TvService)iter.next();
      se = new SidEntry(ts.getId(), ts.getProfileName());
      if(!sidLinksMap.containsKey(se)) iter.remove();
    }
    Set added; String profileName; ServiceMapping sm;
    for(Iterator iter = addedServices.keySet().iterator(); iter.hasNext(); ) {
      profileName = (String)iter.next();
      added = (Set)addedServices.get(profileName);
      for(Iterator i = added.iterator(); i.hasNext(); ) {
        sm = (ServiceMapping)i.next();
        ts = config.getService(profileName, sm);
        if(!retained.contains(ts)) {
          i.remove();
          logger.info("Linked service no longer available: " + ts);
          config.getConnManager().cwsLostService(null, ts, true);
        }
      }
      if(added.isEmpty()) iter.remove();
    }
  }

  /*
  private void setupMessaging() { // hack to unobtrusively make use of messaging plugin
    ProxyPlugin plugin = (ProxyPlugin)ProxyConfig.getInstance().getProxyPlugins().get("messagingplugin");
    if(plugin != null) {
      try {
        Method clear = plugin.getClass().getMethod("clearDynamicServiceTriggers", new Class[]{String.class});
        if(clear == null) return;
        clear.invoke(plugin, new Object[]{"SidCacheLinker"});
      } catch(Exception e) {
        e.printStackTrace();
        return;
      }
      Map trMap = new HashMap(); TvService tsKey, tsAdded; Set unlockers;
      for(Iterator iter = requiredServices.keySet().iterator(); iter.hasNext(); ) {
        tsKey = (TvService)iter.next();
        for(Iterator i = ((Set)requiredServices.get(tsKey)).iterator(); i.hasNext(); ) {
          tsAdded = (TvService)i.next();
          unlockers = (Set)trMap.get(tsAdded);
          if(unlockers == null) unlockers = new HashSet();
          unlockers.add(tsKey);
          trMap.put(tsAdded, unlockers);
        }
      }
      Method add;
      try {
        add = plugin.getClass().getMethod("addDynamicServiceTrigger", new Class[]{String.class, TvService.class, String.class});
      } catch(NoSuchMethodException e) {
        e.printStackTrace();
        return;
      }
      TvService ts;
      for(Iterator iter = trMap.keySet().iterator(); iter.hasNext(); ) {
        ts = (TvService)iter.next();
        try {
          add.invoke(plugin, new Object[]{"SidCacheLinker", ts, "Possible links: " + trMap.get(ts).toString()});
        } catch(Exception e) {
          e.printStackTrace();
        }
      }
    }

  }
  */

  public Set getServicesForProfile(String profileName) {
    if(cache.getListener() != this) return Collections.EMPTY_SET;
    if(!addedServices.containsKey(profileName)) return Collections.EMPTY_SET;
    else return (Set)addedServices.get(profileName);
  }

  static class SidEntry {

    int serviceId;
    String profileName;

    SidEntry(CamdNetMessage msg) {
      this.serviceId = msg.getServiceId();
      this.profileName = msg.getProfileName();
    }

    SidEntry(String s) {
      String[] tokens = s.split(":");
      this.serviceId = Integer.parseInt(tokens[0], 16);
      this.profileName = tokens[1];
    }

    SidEntry(int serviceId, String profileName) {
      this.serviceId = serviceId;
      this.profileName = profileName;
    }

    public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;
      SidEntry sidEntry = (SidEntry) o;
      if(serviceId != sidEntry.serviceId) return false;
      if(profileName != null ? !profileName.equals(sidEntry.profileName) : sidEntry.profileName != null) return false;
      return true;
    }

    public int hashCode() {
      int result = serviceId;
      result = 31 * result + (profileName != null ? profileName.hashCode() : 0);
      return result;
    }

    public String toString() {
      return Integer.toHexString(serviceId) + ":" + profileName;
    }
  }

}
