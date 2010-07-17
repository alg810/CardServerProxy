package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.web.*;
import com.bowman.cardserv.crypto.DESUtil;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Jul 8, 2008
 * Time: 2:05:17 PM
 */
public class EmmAnalyzerPlugin implements ProxyPlugin {

  private Set profiles;
  private Set users;
  private StatusCommand statCmd;

  private Map capture = new HashMap();

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {

    String profiles = xml.getStringValue("profiles", "");
    if("".equals(profiles)) profiles = null;
    String users = xml.getStringValue("users", "");
    if("".equals(users)) users = null;

    if(profiles != null) this.profiles = new HashSet(Arrays.asList(profiles.split(" ")));
    else this.profiles = Collections.EMPTY_SET;
    if(users != null) this.users = new HashSet(Arrays.asList(users.split(" ")));
    else this.users = Collections.EMPTY_SET;

  }

  public void start(CardServProxy proxy) {
    statCmd = new StatusCommand("emm-log", "Show emm stats", "Show gathered emm statistics for the specified user.", true);
    statCmd.addParam("name", "").setOptions("@known-users", false);
    statCmd.addParam("profile", "").setOptions("@profiles", false);
    statCmd.addParam("data", "").setOptions(Arrays.asList(new String[] {"true", "false"}), false);
    try {
      statCmd.register(this);
    } catch(NoSuchMethodException e) {
      e.printStackTrace();
    }
  }

  public void stop() {
    if(statCmd != null) {
      statCmd.unregister();
      statCmd = null;
    }
  }

  public void runStatusCmdEmmLog(XmlStringBuffer xb, Map params) {
    String user = (String)params.get("name");
    String profile = (String)params.get("profile");
    boolean data = "true".equals(params.get("data"));

    EmmInfoAggregate info = getInfo(user, profile);
    if(info != null) xmlFormatEmmInfo(xb, info, data);
  }

  public static void xmlFormatEmmInfo(XmlStringBuffer xb, EmmInfoAggregate info, boolean includeData) {
    xb.appendElement("emm-info");
    xb.appendAttr("user-name", info.user);
    xb.appendAttr("profile", info.profile);
    if(info.lastCard != null) xb.appendAttr("sent-carddata", info.lastCard.toString());
    xb.appendAttr("total-count", info.receiveOrder.size());
    xb.appendAttr("unique-count", info.emms.size());
    if(info.lastInterval != -1) xb.appendAttr("last-interval", info.lastInterval);
    if(info.shortestInterval != -1) xb.appendAttr("shortest-interval", info.shortestInterval);
    if(info.longestInterval != -1) xb.appendAttr("longest-interval", info.longestInterval);
    xb.endElement(false);
    if(info.mostFrequent != null) xmlFormatEmmRecord("most-frequent", xb, info.mostFrequent, includeData);
    if(info.leastFrequent != null) xmlFormatEmmRecord("least-frequent", xb, info.leastFrequent, includeData);    
    xb.appendElement("all-emms");
    EmmRecord er; String key;
    for(Iterator iter = info.emms.keySet().iterator(); iter.hasNext(); ) {
      key = (String)iter.next();
      er = (EmmRecord)info.emms.get(key);
      xmlFormatEmmRecord("emm-record", xb, er, includeData);
    }
    xb.closeElement("all-emms");
    xb.closeElement("emm-info");
  }

  public static void xmlFormatEmmRecord(String name, XmlStringBuffer xb, EmmRecord er, boolean includeData) {
    xb.appendElement(name);
    xb.appendAttr("hash", er.emm.hashCodeStr());
    xb.appendAttr("count", er.count());
    xb.appendAttr("size", er.emm.getDataLength());
    if(includeData) xb.appendAttr("data", DESUtil.bytesToString(er.emm.getCustomData()));
    xb.endElement(false);
    xb.appendElement("seen-log");
    xmlFormatEmmRecordSeenLog(xb, er);
    xb.closeElement("seen-log");    
    xb.closeElement(name);
  }

  public static void xmlFormatEmmRecordSeenLog(XmlStringBuffer xb, EmmRecord er) {
    if(er.linked != null) xmlFormatEmmRecordSeenLog(xb, er.linked); // oldest first, recursive
    xb.appendElement("sighting");
    xb.appendAttr("timestamp", XmlHelper.formatTimeStamp(er.emm.getTimeStamp()));
    xb.appendAttr("client-sid", Integer.toHexString(er.sid));
    xb.appendAttr("index", er.index);
    if(er.repeatInterval != -1) xb.appendAttr("repeat-interval", XmlHelper.formatDuration(er.repeatInterval / 1000));
    xb.endElement(true);
  }

  public String getName() {
    return "EmmAnalyzerPlugin";
  }

  public String getDescription() {
    return "Gathers information about emm's received from clients.";
  }
  
  public Properties getProperties() {
    return null;
  }

  public CamdNetMessage doFilter(ProxySession session, CamdNetMessage msg) {

    try {
      if(msg.getCommandTag() == CamdConstants.MSG_CARD_DATA) {
        CardData newData = new CardData(msg.getCustomData());
        EmmInfoAggregate info = getInfo(session.getUser(), session.getProfileName());
        info.lastCard = newData;
      }

      if(msg.isEcm() && msg.getType() == CamdNetMessage.TYPE_RECEIVED) {
        if(msg.getServiceId() != 0)
          getInfo(session.getUser(), session.getProfileName()).currentSid = msg.getServiceId();
      }

      if(msg.isEmm() && !msg.isEmpty() && msg.getType() == CamdNetMessage.TYPE_RECEIVED) processEmm(session, msg);
    } catch (Throwable t) {
      t.printStackTrace();
    }
    
    return msg;
  }

  private void processEmm(ProxySession session, CamdNetMessage emm) {
    if(!users.isEmpty() && !users.contains(session.getUser())) return;
    if(!profiles.isEmpty() && !profiles.contains(session.getProfileName())) return;

    EmmInfoAggregate info = getInfo(session.getUser(), session.getProfileName());
    info.addEmm(new CamdNetMessage(emm));
  }

  private EmmInfoAggregate getInfo(String user, String profile) {
    String key = user + ":" + profile;
    EmmInfoAggregate info = (EmmInfoAggregate)capture.get(key);
    if(info == null) {
      info = new EmmInfoAggregate(user, profile);
      capture.put(key, info);
    }
    return info;
  }

  public byte[] getResource(String path, boolean admin) {
    return null; 
  }

  public byte[] getResource(String path, byte[] inData, boolean admin) {
    return null;
  }

  static class EmmInfoAggregate {

    String user, profile;
    int currentSid;
    long lastInterval = -1, shortestInterval = -1, longestInterval = -1;
    EmmRecord mostFrequent, leastFrequent;
    CardData lastCard;

    Map emms = new LinkedHashMap();
    List receiveOrder = new ArrayList();

    EmmInfoAggregate(String user, String profile) {
      this.user = user;
      this.profile = profile;
    }

    void addEmm(CamdNetMessage emm) {
      if(!receiveOrder.isEmpty()) {
        EmmRecord prev = (EmmRecord)emms.get(receiveOrder.get(receiveOrder.size() - 1));
        lastInterval = emm.getTimeStamp() - prev.emm.getTimeStamp();
        if(shortestInterval == -1 || lastInterval < shortestInterval) shortestInterval = lastInterval;
        if(longestInterval == -1 || lastInterval > longestInterval) longestInterval = lastInterval;
      }

      receiveOrder.add(emm.hashCodeStr());
      int index = receiveOrder.size();
      EmmRecord nr = new EmmRecord(emm, index, currentSid, lastCard);
      EmmRecord or = (EmmRecord)emms.put(emm.hashCodeStr(), nr);
      if(or != null) {
        nr.link(or);
        if(mostFrequent == null || mostFrequent.repeatInterval < nr.repeatInterval) mostFrequent = nr;
        if(leastFrequent == null || leastFrequent.repeatInterval > nr.repeatInterval) leastFrequent = nr;
      }
    }

  }

  static class EmmRecord {

    CamdNetMessage emm;
    EmmRecord linked;
    CardData card;
    int index, sid;
    long repeatInterval = -1;

    public EmmRecord(CamdNetMessage emm, int index, int sid, CardData card) {
      this.emm = emm;
      this.index = index;
      this.sid = sid;
      this.card = card;
    }

    void link(EmmRecord er) {
      repeatInterval = emm.getTimeStamp() - er.emm.getTimeStamp();
      linked = er;
    }

    int count() {
      if(linked == null) return 1;
      else return 1 + linked.count();
    }
    
  }

}
