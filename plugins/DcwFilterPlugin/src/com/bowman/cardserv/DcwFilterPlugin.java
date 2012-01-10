package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.cws.AbstractCwsConnector;

import java.io.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Apr 10, 2009
 * Time: 00:45:17 AM
 */
public class DcwFilterPlugin implements ProxyPlugin, ReplyFilter {

  private static final byte[] badDcw1 = DESUtil.stringToBytes("00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00");

  private ProxyLogger logger;
  private Set badDcws = new HashSet();

  // these MessageCacheMaps will delete the oldest entry on every insertion, if it is older than 20 secs
  private Map replyMap = Collections.synchronizedMap(new MessageCacheMap(20000));
  private Map connMap = Collections.synchronizedMap(new MessageCacheMap(20000)); // dcw -> connector that recevied it
  private Map sidLinksMap = Collections.synchronizedMap(new HashMap());
  private Set removedLinks = new HashSet(), profiles;
  private Map monitoredSidsMap = Collections.synchronizedMap(new MessageCacheMap(20000));
  private Map invalidLinksMap = Collections.synchronizedMap(new HashMap());

  private Map zeroedReplyMap = Collections.synchronizedMap(new MessageCacheMap(20000));

  private boolean detectLinks = false, verifyReplies = false, forceContinuity = false, zeroCounting = true;
  private int verifiedCount = 0, badLengthCount = 0, filteredCount = 0, checksumFailCount = 0, mergeCount = 0;
  private String badDcwStr;
  private File mapFile;

  public DcwFilterPlugin() {
    logger = ProxyLogger.getLabeledLogger(getClass().getName());
    mapFile = new File("etc", "links.dat");
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    Iterator iter = xml.getMultipleStrings("bad-dcw");
    byte[] bd;
    if(iter != null) while(iter.hasNext()) {
      bd = DESUtil.stringToBytes((String)iter.next());
      if(bd.length != 16)
        throw new ConfigException(xml.getFullName(), "bad-dcw not 16 bytes: " + DESUtil.bytesToString(bd));
      else badDcws.add(bd);
    }
    badDcws.add(badDcw1);

    detectLinks = "true".equalsIgnoreCase(xml.getStringValue("detect-links", "false"));
    zeroCounting = "true".equalsIgnoreCase(xml.getStringValue("zero-counting", "true"));
    verifyReplies = "true".equalsIgnoreCase(xml.getStringValue("verify-replies", "false"));
    forceContinuity = "true".equalsIgnoreCase(xml.getStringValue("force-continuity", "false"));

    String profilesStr = xml.getStringValue("profiles", "");
    if(profilesStr != null && profilesStr.length() > 0) {
      profiles = new HashSet(Arrays.asList(profilesStr.toLowerCase().split(" ")));
    } else profiles = Collections.EMPTY_SET;
  }

  public void start(CardServProxy proxy) {
    Set set = new HashSet();
    for(Iterator iter = badDcws.iterator(); iter.hasNext();) {
      set.add(DESUtil.bytesToString((byte[])iter.next()));
    }
    badDcwStr = set.toString();
    logger.info("Filtering bad CWs: " + badDcwStr);
    loadLinksMap();
  }

  public void stop() {
    saveLinksMap();
  }

  public String getName() {
    return "DcwFilterPlugin";
  }

  public String getDescription() {
    return "Change bad dcws into empty newcamd replies (cannot decode) and do some basic analysis.";
  }

  public Properties getProperties() {
    Properties p = new Properties();
    if(detectLinks) {
      Set links = new TreeSet();
      Set grp, strGrp;
      String s, m;
      String[] sa;
      ProxyConfig config = ProxyConfig.getInstance();
      long now = System.currentTimeMillis();
      TvService srv;
      for(Iterator iter = sidLinksMap.values().iterator(); iter.hasNext();) {
        grp = (Set)iter.next();
        strGrp = new TreeSet();
        for(Iterator i = grp.iterator(); i.hasNext();) {
          s = (String)i.next();
          sa = s.split(":");
          m = (monitoredSidsMap.containsKey(s) && (now - ((CamdNetMessage)monitoredSidsMap.get(s)).getTimeStamp() < 20000)) ? "*" : "";
          if(profiles.isEmpty() || profiles.contains(sa[1].toLowerCase())) {
            srv = config.getService(sa[1], Integer.parseInt(sa[0], 16));
            if(srv.isTv()) strGrp.add(srv + m);
          }
        }
        if(strGrp.size() >= 2) links.add(strGrp.toString());
      }
      p.setProperty("detected-service-links", links.toString());
      p.setProperty("removed-service-links", removedLinks.toString());

      /*
      TvService ts; Set monitoredStr = new TreeSet();
      for(Iterator iter = monitoredSidsMap.keySet().iterator(); iter.hasNext(); ) {
        s = (String)iter.next();
        sa = s.split(":");
        ts = config.getService(sa[1], Integer.parseInt(sa[0], 16));
        if((now - ((CamdNetMessage)(monitoredSidsMap.get(s))).getTimeStamp()) < 20000)
          monitoredStr.add(ts.toString() + "=" + (now - ((CamdNetMessage)(monitoredSidsMap.get(s))).getTimeStamp()));
      }
      p.setProperty("monitored-service-links", monitoredStr.toString());
      p.setProperty("service-link-keys", sidLinksMap.keySet().toString());
      */
    }
    if(verifyReplies) {
      p.setProperty("verified-count", String.valueOf(verifiedCount));
      if(!connMap.isEmpty()) p.setProperty("connector-map", String.valueOf(connMap.size()));
    }
    if(!replyMap.isEmpty()) p.setProperty("reply-map", String.valueOf(replyMap.size()));

    if(badDcwStr != null) p.setProperty("filtering-dcws", badDcwStr);

    if(filteredCount > 0) p.setProperty("filtered-count", String.valueOf(filteredCount));
    if(badLengthCount > 0) p.setProperty("bad-length-count", String.valueOf(badLengthCount));
    if(checksumFailCount > 0) p.setProperty("checksum-fail-count", String.valueOf(checksumFailCount));
    if(mergeCount > 0) p.setProperty("merged-reply-count", String.valueOf(mergeCount));

    if(p.isEmpty()) return null;
    else return p;
  }

  protected void loadLinksMap() {
    if(mapFile.exists()) {
      try {
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(mapFile)));
        sidLinksMap = (Map)ois.readObject();
        logger.fine("Loaded links map, " + sidLinksMap.size() + " entries.");
        ois.close();
      } catch(Exception e) {
        logger.throwing(e);
        logger.warning("Failed to load links map ('" + mapFile.getPath() + "'): " + e);
      }
    }
  }

  protected void saveLinksMap() {
    try {
      ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(mapFile)));
      oos.writeObject(sidLinksMap);
      logger.fine("Saved links map, " + sidLinksMap.size() + " entries.");
      oos.close();
    } catch(IOException e) {
      logger.throwing(e);
      logger.warning("Failed to save links map ('" + mapFile.getPath() + "'): " + e);
    }
  }

  public CamdNetMessage doFilter(ProxySession session, CamdNetMessage msg) {
    return msg; // do nothing with ecm requests from clients
  }

  public CamdNetMessage doReplyFilter(CwsConnector connector, CamdNetMessage msg) {
    try {
      if(msg.isDcw() && msg.getDataLength() != 0) { // ignore other types of replies and cannot-decodes

        if(profiles.isEmpty() || profiles.contains(msg.getProfileName())) {

          if(msg.getDataLength() != 16) { // check for bad length
            logger.warning("Bad DCW length (" + msg.getDataLength() + ") from '" + connector.getName() + "': " +
                DESUtil.bytesToString(msg.getCustomData()));
            msg.setCustomData(new byte[0]); // turn bad length dcws into cannot-decodes
            badLengthCount++;

          } else if(!msg.checksumDcw()) { // verify dcw checksums
            logger.warning("Bad DCW checksum in reply from '" + connector.getName() + "': " +
                DESUtil.bytesToString(msg.getCustomData()));
            msg.setCustomData(new byte[0]); // turn bad checksum dcws into cannot-decodes
            checksumFailCount++;

          } else if(isBadDcw(msg, badDcws, zeroCounting)) { // verify against list of preconfigured bad replies
            logger.warning("Bad DCW reply from '" + connector.getName() + "': " +
                DESUtil.bytesToString(msg.getCustomData()));
            msg.setCustomData(new byte[0]); // turn preconfigured bad dcws into cannot-decodes
            filteredCount++;

          } else {

            // check for instances of the same dcw reply being used for other currently watched services
            if(detectLinks) detectLink(msg);

            // check for zeroed out dcws and attempt to reinsert the previous one (if availble) to help certain clients
            if(forceContinuity) forceContinuity(msg);

            boolean blockReply = false;

            if(verifyReplies) {
              // require each reply to be returned from 2 different connectors
              // if it isn't, block it here and only release if the same reply is received from another connector
              blockReply = !verifyReply(msg, connector);
            }

            if(detectLinks || verifyReplies) replyMap.put(msg, msg); // keep a 20 second backlog of all received dcws

            if(blockReply) return null;

          }
        }
      }
    } catch(Throwable t) {
      t.printStackTrace(); // intentional catch-all while debugging this use case
    }
    return msg;
  }

  private static boolean hasZeroDcw(byte[] data) {
    return data[0] + data[1] + data[3] == 0 || data[9] + data[10] + data[11] == 0;
  }

  private static void mergeZeroedReplies(CamdNetMessage currMsg, CamdNetMessage prevMsg) {
    byte[] curr = currMsg.getCustomData(), prev = prevMsg.getCustomData();
    for(int i = 0; i < curr.length; i++) curr[i] |= prev[i];
  }

  private static boolean isBadDcw(CamdNetMessage msg, Set badDcws, boolean zeroCounting) {
    byte[] badDcw; byte[] data = msg.getCustomData();
    for(Iterator iter = badDcws.iterator(); iter.hasNext();) {
      badDcw = (byte[])iter.next();
      if(Arrays.equals(data, badDcw)) {
        return true;
      }
    }
    if(zeroCounting) return msg.hasFiveZeroes();
    return false;
  }

  private boolean forceContinuity(CamdNetMessage msg) {
    if(msg.getServiceId() != 0) {
      // System.out.println("Checking for 00 dcw...");
      if(hasZeroDcw(msg.getCustomData())) {
        String context = msg.getServiceId() + ":" + msg.getProfileName();
        String key = (msg.getCommandTag() == 0x81?"81":"80") + ":" + context;
        // System.out.println("00 dcw received: \t" + key + " - " + DESUtil.bytesToString(msg.getCustomData()));
        zeroedReplyMap.put(key, new CamdNetMessage(msg));
        String prevKey = (msg.getCommandTag() == 0x81?"80":"81") + ":" + context;
        CamdNetMessage prev = (CamdNetMessage)zeroedReplyMap.get(prevKey);
        if(prev != null) {
          long age = System.currentTimeMillis() - prev.getTimeStamp();
          if(age < 12000) {
            // System.out.println("Prev dcw found: \t" + prevKey + " - " + DESUtil.bytesToString(prev.getCustomData()) + " (age: " + age);
            mergeZeroedReplies(msg, prev);
            mergeCount++;
            // System.out.println("Resulting reply: \t" + key + " - " + DESUtil.bytesToString(msg.getCustomData()));
            return true;
          }
        }
      }
    }
    return false;
  }

  private boolean detectLink(CamdNetMessage msg) {
    if(msg.getServiceId() != 0) { // dcw has sid, check for services sharing the same dcw sequence
      String msgId = Integer.toHexString(msg.getServiceId()) + ":" + msg.getProfileName();
      if(sidLinksMap.containsKey(msgId)) {
        monitoredSidsMap.put(msgId, msg);
        // check if previously detected links can be disproven with current traffic
        String testId;
        for(Iterator iter = (new ArrayList((Set)sidLinksMap.get(msgId))).iterator(); iter.hasNext();) {
          testId = (String)iter.next();
          if(testId.equals(msgId)) continue;
          if(monitoredSidsMap.containsKey(testId)) {
            CamdNetMessage prev = (CamdNetMessage)monitoredSidsMap.get(testId);
            long now = System.currentTimeMillis();
            if(now - prev.getTimeStamp() < 2000) { // zapping might cause false positives here?
              if(!prev.equals(msg)) {
                ProxyConfig config = ProxyConfig.getInstance();
                String[] sa = testId.split(":");
                TvService m = config.getService(msg.getProfileName(), msg.getServiceId());
                TvService t = config.getService(sa[1], Integer.parseInt(sa[0], 16));
                Set l = new TreeSet();
                l.add(m.toString());
                l.add(t.toString());
                String link = l.toString();
                Long timeStamp = (Long)invalidLinksMap.get(link);
                if(timeStamp != null && (now - timeStamp.longValue() < 20000)) { // two mismatches in a row in under 20 sec = probable invalid link
                  logger.info("Link no longer seems valid, removing: " + m + " > " + t);
                  removedLinks.add(link);
                  Set s = (Set)sidLinksMap.get(msgId);
                  s.remove(testId);
                  if(s.size() <= 1) {
                    sidLinksMap.remove(msgId);
                    sidLinksMap.remove(testId);
                  }
                } else invalidLinksMap.put(link, new Long(now));
              }
            }
          }
        }
      }

      if(replyMap.containsKey(msg)) {
        CamdNetMessage prev = (CamdNetMessage)replyMap.get(msg);
        if(prev.getServiceId() != msg.getServiceId()) {
          String prevId = Integer.toHexString(prev.getServiceId()) + ":" + prev.getProfileName();
          Set links = (Set)sidLinksMap.get(prevId);
          if(links == null) links = (Set)sidLinksMap.get(msgId);
          if(links == null) links = new TreeSet();
          links.add(prevId);
          links.add(msgId);
          if(sidLinksMap.put(prevId, links) == null || sidLinksMap.put(msgId, links) == null) {
            logger.fine("Link found: " + links + "\n\t" + prev + "\n\t" + msg);
            return true;
          }
        }
      }
    }
    return false;
  }

  private boolean verifyReply(CamdNetMessage msg, CwsConnector connector) {
    CwsConnector prevConn = (CwsConnector)connMap.put(msg, connector);
    if(prevConn != null) {
      if(prevConn != connector) { // same reply has previously been received from another connector
        verifiedCount++;
        ((AbstractCwsConnector)prevConn).reportReply((CamdNetMessage)replyMap.get(msg)); // retrieve and re-introduce
        return true;
      }
    }
    // no previous encounter with this reply
    return false;
  }

  public byte[] getResource(String path, boolean admin) {
    return null;
  }

  public byte[] getResource(String path, byte[] inData, boolean admin) {
    return null;
  }

}
