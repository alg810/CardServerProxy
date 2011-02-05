package com.bowman.cardserv;

import com.bowman.cardserv.rmi.*;
import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.util.ProxyXmlConfig;
import com.bowman.cardserv.interfaces.XmlConfigurable;
import com.bowman.cardserv.web.FileFetcher;
import com.bowman.xml.*;
import com.bowman.util.*;

import java.rmi.RemoteException;
import java.text.*;
import java.util.*;
import java.io.IOException;
import java.net.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Jul 13, 2008
 * Time: 1:51:29 PM
 */
public class TriggerMessenger implements RemoteListener, XmlConfigurable, CronTimerListener {

  private static final String[] LEGEND = {
      "User login", "Channel changed", "Successfully connected", "Disconnected", "Connection attempt failed",
      "Warning (timeout)", "Lost service", "", "Invalid card data", "", "Startup"
  };
  private static final SimpleDateFormat fmt = new SimpleDateFormat("yyMMdd HH:mm:ss");

  private MessagingPlugin parent;

  private Set excludeUsers, excludeProfiles;
  private Set triggers = new HashSet();
  private Set externalTriggers = new HashSet();
  private Map dynamicTriggers = new HashMap();
  private Set connecting = new HashSet();
  private Set invalid = new HashSet();

  private URL triggerFileUri;
  private String triggerFile, triggerFileKey;
  private int triggerFileInterval;
  private long triggerFileLastModified, triggerFileLastCheck;

  public TriggerMessenger(MessagingPlugin parent) {
    this.parent = parent;
  }

  public void configUpdated(ProxyXmlConfig autoCfg) throws ConfigException {
    String eu = autoCfg.getStringValue("exclude-users", "");
    if(!"".equals(eu)) excludeUsers = new HashSet(Arrays.asList(eu.split(" ")));
    else excludeUsers = Collections.EMPTY_SET;
    String ep = autoCfg.getStringValue("exclude-profiles", "");
    if(!"".equals(ep)) excludeProfiles = new HashSet(Arrays.asList(ep.split(" ")));
    else excludeProfiles = Collections.EMPTY_SET;

    for(Iterator iter = autoCfg.getMultipleSubConfigs("msg-trigger"); iter.hasNext(); )
      addMsgTrigger((ProxyXmlConfig)iter.next(), triggers);

    ProxyXmlConfig extCfg = null;
    try {
      extCfg = autoCfg.getSubConfig("external-msg-triggers");
    } catch (ConfigException e) {}

    if(extCfg != null) updateExternalTriggers(extCfg);
    else {
      triggerFileUri = null;
      triggerFileInterval = 0;      
    }
  }

  private void updateExternalTriggers(ProxyXmlConfig xml) throws ConfigException {
    boolean enabled = true;
    try {
      enabled = "true".equalsIgnoreCase(xml.getStringValue("enabled"));
    } catch (ConfigException e) {
    }
    if(enabled) {
      String url = xml.getStringValue("trigger-file-url");
      try {
        triggerFileUri = new URL(url);
      } catch(MalformedURLException e) {
        throw new ConfigException(xml.getFullName(), "trigger-file-url", "Malformed URL: " + e.getMessage());
      }
      try {
        triggerFileKey = xml.getStringValue("trigger-file-key");
      } catch (ConfigException e) {
        triggerFileKey = null;
      }
      triggerFileInterval = xml.getIntValue("update-interval") * 60 * 1000;
      fetchTriggerFile();
    } else { // disabled
      externalTriggers.clear();
      triggerFileUri = null;
      triggerFileInterval = 0;
    }
  }

  private void addMsgTrigger(ProxyXmlConfig xml, Set triggers) throws ConfigException {
    MsgTrigger trigger = new MsgTrigger(xml.getStringValue("match-flags", ""),
        xml.getStringValue("match-profiles", ""), xml.getStringValue("match-usernames", ""),
        xml.getStringValue("match-sids", ""), xml.getStringValue("admin-only", ""),
        xml.getStringValue("match-warnings", ""), xml.getStringValue("cws-owner-only", ""),
        xml.getStringValue("match-events", ""));

    ProxyXmlConfig msgXml = xml.getSubConfig("msg");
    trigger.message = new TriggerMsg("true".equals(msgXml.getStringValue("wait-for-zap", "false")),
        "true".equals(msgXml.getStringValue("store", "false")), msgXml.getStringValue("format"),
        msgXml.getStringValue("target", "@trigger"));

    triggers.add(trigger);
  }

  protected void addDynamicServiceTrigger(String name, TvService ts, String message) throws ConfigException {
    Set dt = (Set)dynamicTriggers.get(name);
    if(dt == null) dt = new HashSet();
    MsgTrigger tr = new MsgTrigger("Z", ts.getProfileName(), "", Integer.toHexString(ts.getId()), "", "", "", "");
    tr.message = new TriggerMsg(false, false, message, "@trigger");
    dt.add(tr);
    dynamicTriggers.put(name, dt);
  }

  protected void clearDynamicServiceTrigger(String name) {
    dynamicTriggers.remove(name);
  }

  // make the cws events behave like in the status web
  private boolean filterRepeats(RemoteEvent event) {
    switch(event.getType()) {
      case RemoteEvent.CWS_CONNECTED:
        connecting.remove(event.getLabel());
        invalid.remove(event.getLabel());
        return true;
      case RemoteEvent.CWS_CONNECTION_FAILED:
        if(!connecting.contains(event.getLabel())) {
          connecting.add(event.getLabel());
          return true;
        }
        break;
      case RemoteEvent.CWS_DISCONNECTED:
      case RemoteEvent.CWS_WARNING:
      case RemoteEvent.CWS_LOST_SERVICE:
      case RemoteEvent.PROXY_STARTUP:
      case RemoteEvent.USER_STATUS_CHANGED:
      case RemoteEvent.ECM_TRANSACTION:
        return true;
      case RemoteEvent.CWS_INVALID_CARD:
        if(!invalid.contains(event.getLabel())) {
          invalid.add(event.getLabel());
          return true;
        }
        break;
    }
    return false;
  }

  public void eventRaised(RemoteEvent event) throws RemoteException {
    Collection mtr;
    boolean admin = false, cwsOwner = false;
    String text, triggerUser = null, name, service = "";

    if(!filterRepeats(event)) return;
    else if(excludeProfiles.contains(event.getProfile())) return;
    else if(event.getType() == RemoteEvent.ECM_TRANSACTION) {
      String userName = event.getMessage();
      if(excludeUsers.contains(userName)) return;
      else {

        // ecm transaction event

        boolean warning = "true".equalsIgnoreCase(event.getProperty("warning"));
        String sid = event.getProperty("sid");
        admin = parent.proxy.isAdmin(userName);
        mtr = findTrTriggers(userName, event.getProfile(), event.getProperty("flags"), warning, admin,
            Integer.parseInt(sid, 16));

        if(!mtr.isEmpty()) {
          name = event.getMessage();
          triggerUser = name;
          service = event.getProperty("service");
          text = name + " " + event.getLabel() + " - " + service + " - " + event.getProperty("time") + " ms - '" +
              event.getProperty("flags") + "'";
        } else return;
      }
    } else {

      // cws related event

      mtr = findEvTriggers(event.getProfile(), event.getType());

      if(!mtr.isEmpty()) {
        name = event.getLabel();
        if(event.getType() > 1 && event.getType() < 10) {
          triggerUser = name.substring(name.indexOf('[') + 1);
          triggerUser = triggerUser.substring(0, triggerUser.indexOf(':'));
          // assume there is a user with the same name as the cws connector, and that this users owns the cws
          cwsOwner = true;
          admin = parent.proxy.isAdmin(triggerUser);
        }
        text = name + ":" + LEGEND[event.getType()];
        if(!name.equals(event.getMessage())) text = text + " - " + event.getMessage();
      } else return;
    }

    String time = fmt.format(new Date(event.getTimeStamp()));
    String preFormat =  time + " " + text;
    Object[] strings = new Object[] {preFormat, name, event.getProfile(), service, time, text};

    MsgTrigger trigger; String target = null;
    for(Iterator iter = mtr.iterator(); iter.hasNext(); ) {
      trigger = (MsgTrigger)iter.next();

      if("@trigger".equals(trigger.message.target)) { // resolve trigger to username
        if(cwsOwner) {
          if(trigger.matchCwsOwner != null && trigger.matchCwsOwner.booleanValue())
            target = triggerUser; // only set cwsowner as target if instructed
        } else target = triggerUser;
      } else target = trigger.message.target;

      if(trigger.matchAdmin != null && trigger.matchAdmin.booleanValue() && !admin) continue; // trigger says admin only
      if("@trigger".equals(target) || target == null) continue; // unable to resolve target user
      if(excludeUsers.contains(target)) continue; // target is an excluded users

      parent.triggeredMessage(this, target,  MessageFormat.format(trigger.message.msgFormat, strings));
    }

  }

  private Collection findTrTriggers(String userName, String profile, String flags, boolean warning, boolean admin,
                                    int sid) throws RemoteException
  {
    Set result = new HashSet();
    MsgTrigger trigger;
    for(Iterator iter = triggers.iterator(); iter.hasNext();) {
      trigger = (MsgTrigger)iter.next();
      if(trigger.matchesTransaction(userName, profile, flags, warning, admin, sid)) result.add(trigger);
    }
    for(Iterator iter = externalTriggers.iterator(); iter.hasNext();) {
      trigger = (MsgTrigger)iter.next();
      if(trigger.matchesTransaction(userName, profile, flags, warning, admin, sid)) result.add(trigger);
    }
    Set group;
    for(Iterator iter = dynamicTriggers.values().iterator(); iter.hasNext();) {
      group = (Set)iter.next();
      for(Iterator i = group.iterator(); i.hasNext(); ) {
        trigger = (MsgTrigger)i.next();
        if(trigger.matchesTransaction(userName, profile, flags, warning, admin, sid)) result.add(trigger);
      }
    }
    return result;
  }

  private Collection findEvTriggers(String profile, int event) {
    Set result = new HashSet();
    MsgTrigger trigger;
    for(Iterator iter = triggers.iterator(); iter.hasNext();) {
      trigger = (MsgTrigger)iter.next();
      if(trigger.matchesCwsEvent(profile, event)) result.add(trigger);
    }
    for(Iterator iter = externalTriggers.iterator(); iter.hasNext();) {
      trigger = (MsgTrigger)iter.next();
      if(trigger.matchesCwsEvent(profile, event)) result.add(trigger);
    }
    return result;
  }

  void fetchTriggerFile() {
    try {
      triggerFileLastCheck = System.currentTimeMillis();
      parent.logger.fine("Fetching '" + triggerFileUri + (triggerFileLastModified != -1?", lm: " +
          new Date(triggerFileLastModified):""));
      String newFile = FileFetcher.fetchFile(triggerFileUri, triggerFileKey, triggerFileLastModified);
      if(newFile != null) {
        if(triggerFile != null && triggerFile.hashCode() == newFile.hashCode())
          parent.logger.fine("No changes found after fetch...");
        else processTriggerFile(newFile);
      } else parent.logger.fine("Trigger file unchanged...");
    } catch(IOException e) {
      parent.logger.throwing(e);
      parent.logger.warning("Failed to fetch trigger file '" + triggerFileUri +"': " + e);
    }
  }

  void processTriggerFile(String newFile) {
    try {
      ProxyXmlConfig xml = new ProxyXmlConfig(new XMLConfig(newFile, false));

      Set triggers = new HashSet();
      for(Iterator iter = xml.getMultipleSubConfigs("msg-trigger"); iter.hasNext(); )
        addMsgTrigger((ProxyXmlConfig)iter.next(), triggers);
      externalTriggers = triggers;

      parent.logger.info(externalTriggers.size() + " trigger definitions parsed/updated from '" + triggerFileUri +
          "', local: " + triggers.size());

      triggerFile = newFile;
      triggerFileLastModified = System.currentTimeMillis();
    } catch(XMLConfigException e) {
      parent.logger.throwing(e);
      parent.logger.warning("Unable to parse '" + triggerFileUri + "': " + e.getMessage());
    } catch(ConfigException e) {
      parent.logger.throwing(e);
      parent.logger.warning("Error in connector file '" + triggerFileUri + "': " + e.getMessage());
    }
  }

  public void timeout(CronTimer cronTimer) {
    if(triggerFileInterval > 0)
      if(System.currentTimeMillis() - triggerFileLastCheck > triggerFileInterval) {
        new Thread("TriggerFileFetchThread") {
          public void run() {
            fetchTriggerFile();
          }
        }.start();
      }
  }

  static class MsgTrigger {
    Set matchFlags;
    Set matchProfiles;
    Set matchUsernames;
    Set matchSids;
    Set matchEvents;
    Boolean matchAdmin;
    Boolean matchWarnings;
    Boolean matchCwsOwner;

    TriggerMsg message;

    MsgTrigger(String matchFlags, String matchProfile, String matchUsername, String matchSid, String matchAdmin,
                  String matchWarnings, String matchCwsOwner, String matchEvents) throws ConfigException {
      if(!"".equals(matchFlags)) this.matchFlags = new HashSet(Arrays.asList(matchFlags.split(" ")));
      if(!"".equals(matchProfile)) this.matchProfiles = new HashSet(Arrays.asList(matchProfile.split(" ")));
      if(!"".equals(matchUsername)) this.matchUsernames = new HashSet(Arrays.asList(matchUsername.split(" ")));
      if(!"".equals(matchSid)) this.matchSids = ProxyXmlConfig.getIntTokens("match-sid", matchSid);
      if(!"".equals(matchEvents)) {
        String[] se = matchEvents.split(" ");
        Integer[] e = new Integer[se.length];
        for(int i = 0; i < e.length; i++) {
          e[i] = Integer.valueOf(se[i]);
          if(e[i].intValue() < 0 || e[i].intValue() > 10)
            throw new ConfigException("match-events", "Invalid event: " + e[i] + " (must be 0-8 or 10)");
        }
        this.matchEvents = new HashSet(Arrays.asList(e));
      }
      if(!"".equals(matchAdmin)) this.matchAdmin = Boolean.valueOf(matchAdmin);
      if(!"".equals(matchWarnings)) this.matchWarnings = Boolean.valueOf(matchWarnings);
      if(!"".equals(matchCwsOwner)) this.matchCwsOwner = Boolean.valueOf(matchCwsOwner);
    }

    boolean matchesFlags(String flags) {
      if(matchFlags != null) {
        Set fs = new HashSet(Arrays.asList(flags.split("")));
        fs.remove("");
        return fs.containsAll(matchFlags);
      } else return true;
    }

    boolean matchesUsername(String userName) {
      if(matchUsernames != null) return matchUsernames.contains(userName);
      else return true;
    }

    boolean matchesProfile(String profile) {
      if(matchProfiles != null) return matchProfiles.contains(profile);
      else return true;
    }

    boolean matchesSid(int sid) {
      if(matchSids != null) return matchSids.contains(new Integer(sid));
      else return true;
    }

    boolean matchesEvent(int event) {
      if(matchEvents != null) return matchEvents.contains(new Integer(event));
      else return true;
    }

    boolean matchesTransaction(String userName, String profile, String flags, boolean warning, boolean admin, int sid) {
      if(matchEvents != null) return false;
      boolean result = true;
      result = result && matchesUsername(userName);
      result = result && matchesProfile(profile);
      result = result && matchesFlags(flags);
      result = result && matchesSid(sid);
      if(matchAdmin != null) result = result && (admin == matchAdmin.booleanValue());
      if(matchWarnings != null) result = result && (warning == matchWarnings.booleanValue());
      return result;
    }

    boolean matchesCwsEvent(String profile, int event) {
      if(matchFlags != null || matchSids != null || matchUsernames != null) return false;
      boolean result = true;
      result = result && matchesProfile(profile);
      result = result && matchesEvent(event);
      return result;
    }

  }

  static class TriggerMsg {
    boolean waitForZap;
    boolean store;
    String msgFormat;
    String target;

    TriggerMsg(boolean waitForZap, boolean store, String msgFormat, String target) {
      this.waitForZap = waitForZap;
      this.store = store;
      this.msgFormat = msgFormat;
      this.target = target;
    }
  }

}
