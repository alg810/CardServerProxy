package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.web.*;
import com.bowman.cardserv.util.ProxyLogger;
import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.util.*;

import java.util.*;
import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Sep 12, 2010
 * Time: 6:06:46 AM
 */
public class SidCacheLinker implements CacheListener, FileChangeListener, CronTimerListener {

  private static final int MAX_DELTA = 9000;

  private CacheHandler cache;
  private Map sidLinksMap = Collections.synchronizedMap(new HashMap());

  private Map sidLockMap = new MessageCacheMap(MAX_DELTA);  // ongoing original requests: sid > orig-req
  private Map requestMap = new MessageCacheMap(20000); // all requests linked to a specific req: orig-req > reqs
  private Map sidRequestMap = new MessageCacheMap(10000); // undecodable held reqs, by sid: sid > req
  private Map replyMap = new MessageCacheMap(20000); // recently received replies that may satisfy late reqs before hold

  private ProxyLogger logger;
  private boolean active;
  private SidEntry testService;

  private FileWatchdog fw;
  private File linksFile;
  private long linksFileTimeStamp;
  private boolean linksChanged;
  private String defaultProfile;

  public SidCacheLinker() {
    logger = ProxyLogger.getLabeledLogger(getClass().getName());
    cache = ProxyConfig.getInstance().getCacheHandler();
    cache.setListener(this);
    active = true;
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
        linksChanged = false;
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

  public CtrlCommandResult runCtrlCmdToggleLinker() {
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
    String sid = params.get("sid") + ":" + params.get("profile");
    if(sid.indexOf("null") != -1) resultMsg = "Missing/invalid sid";
    else {
      result = true;
      SidEntry se = new SidEntry(sid);
      if(se.equals(testService)) {
        testService = null;
        resultMsg = "Test mode disabled";
      } else {
        testService = se;
        resultMsg = "Test link set: " + sid + " <> *";
      }
    }
    return new CtrlCommandResult(result, resultMsg);
  }

  public CtrlCommandResult runCtrlCmdClearLinks() {
    sidLinksMap.clear();
    testService = null;
    return new CtrlCommandResult(true, "Sid links cleared.");
  }  

  public boolean lockRequest(int successFactor, CamdNetMessage req) {
    SidEntry se = new SidEntry(req);

    if(sidLockMap.containsKey(se)) { // linked request already in progress
      CamdNetMessage origReq = (CamdNetMessage)sidLockMap.get(se);
      if(System.currentTimeMillis() - origReq.getTimeStamp() < MAX_DELTA) {

        if(replyMap.containsKey(origReq)) { // and reply already received
          logger.fine("Reply already available for: " + req);
          req.setLinkedService(new SidEntry(origReq).toString());
          cache.processReply(req, (CamdNetMessage)replyMap.get(origReq));
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
    if(req.getProfileName() == null || successFactor == -1) return; // can't succeed - never create locks
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

    if(requestMap.containsKey(req)) { // reply matches specific linked request(s)
      reqs = (Set)requestMap.get(req);
    } else { // reply might match locked undecodable requests

      if(req.getProfileName() == null) { // todo - clusteredcache format doesn't include profile info, guess wildly
        se = new SidEntry(req.getServiceId(), defaultProfile);      
      } else se = new SidEntry(req);

      Set links = (Set)sidLinksMap.get(se);
      if(links != null) {
        reqs = new HashSet();
        SidEntry s;
        for(Iterator iter = links.iterator(); iter.hasNext(); ) {
          s = (SidEntry)iter.next();
          if(sidRequestMap.containsKey(s)) reqs.add(sidRequestMap.remove(s));
        }
        if(reqs.isEmpty()) reqs = null;
      }
    }

    if(reqs != null) { // distribute reply to all waiting requests
      if(se == null) se = new SidEntry(req);
      CamdNetMessage linkedReq;
      for(Iterator iter = reqs.iterator(); iter.hasNext(); ) {
        linkedReq = (CamdNetMessage)iter.next();
        logger.fine("Copying reply to linked request: " + reply.toDebugString() + " -> " + linkedReq);
        linkedReq.setLinkedService(se.toString());
        cache.processReply(linkedReq, reply);
      }      
    }
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
    links.remove(se1);
    links.remove(se2);
    boolean removed = (sidLinksMap.remove(se1) != null || sidLinksMap.remove(se2) != null);
    if(removed) linksChanged = true;
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
      SidEntry sidEntry = (SidEntry)o;
      if(serviceId != sidEntry.serviceId) return false;
      if(!profileName.equals(sidEntry.profileName)) return false;
      return true;
    }

    public int hashCode() {
      int result = serviceId;
      result = 31 * result + profileName.hashCode();
      return result;
    }

    public String toString() {
      return Integer.toHexString(serviceId) + ":" + profileName;
    }
  }

}
