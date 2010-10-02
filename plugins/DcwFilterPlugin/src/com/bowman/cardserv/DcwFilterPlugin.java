package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.cws.AbstractCwsConnector;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Apr 10, 2009
 * Time: 00:45:17 AM
 */
public class DcwFilterPlugin implements ProxyPlugin, ReplyFilter {

  private static final byte[] badDcw1 = DESUtil.stringToBytes("00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00");
  private static final byte[] badDcw2 = DESUtil.stringToBytes("00 00 00 00 00 00 3C 3C 00 00 00 00 00 00 3C 3C");

  private ProxyLogger logger;
  private Set badDcws = new HashSet();

  private Map replyMap = Collections.synchronizedMap(new MessageCacheMap(20000));
  private Map sidMap = Collections.synchronizedMap(new HashMap());

  public DcwFilterPlugin() {
    logger = ProxyLogger.getLabeledLogger(getClass().getName());
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    Iterator iter = xml.getMultipleStrings("bad-dcw");
    byte[] bd;
    if(iter != null) while(iter.hasNext()) {
      bd = DESUtil.stringToBytes((String)iter.next());
      if(bd.length != 16) throw new ConfigException(xml.getFullName(), "bad-dcw not 16 bytes: " + DESUtil.bytesToString(bd));
      else badDcws.add(bd);
    }
    badDcws.add(badDcw1);
    badDcws.add(badDcw2);
  }

  public void start(CardServProxy proxy) {
    Set set = new HashSet();
    for(Iterator iter = badDcws.iterator(); iter.hasNext(); ) {
      set.add(DESUtil.bytesToString((byte[])iter.next()));
    }
    logger.info("Filtering bad CWs: " + set);
  }
  public void stop() {}

  public String getName() {
    return "DcwFilterPlugin";
  }

  public String getDescription() {
    return "Change all-zero cw pairs and other bad data into empty newcamd replies (cannot decode).";
  }
  
  public Properties getProperties() {
    Set links = new TreeSet();
    for(Iterator iter = sidMap.values().iterator(); iter.hasNext(); ) links.add(iter.next().toString());
    Properties p = new Properties();    
    p.setProperty("detected-service-links", links.toString());
    p.setProperty("reply-map", String.valueOf(replyMap.size()));        
    return p;
  }

  public CamdNetMessage doFilter(ProxySession session, CamdNetMessage msg) {    
    return msg; // do nothing with ecm requests from clients
  }

  public CamdNetMessage doReplyFilter(CwsConnector connector, CamdNetMessage msg) {
    try {
      if(msg.isDcw() && msg.getDataLength() != 0) { // ignore other types of replies and cannot-decodes

        if(msg.getDataLength() != 16) { // check for bad length
          logger.warning("Bad DCW length (" + msg.getDataLength() + ") from '" + connector.getName() + "': " +
              DESUtil.bytesToString(msg.getCustomData()));
          msg.setCustomData(new byte[0]); // turn bad length dcws into cannot-decodes

        } else if(!checksumDcw(msg.getCustomData())) { // verify dcw checksums
          logger.warning("Bad DCW checksum in reply from '" + connector.getName() + "': " +
              DESUtil.bytesToString(msg.getCustomData()));
          msg.setCustomData(new byte[0]); // turn bad checksum dcws into cannot-decodes

        } else if(isBadDcw(msg, badDcws)) { // verify against list of preconfigured bad replies
          logger.warning("Bad DCW reply from '" + connector.getName() + "': " +
              DESUtil.bytesToString(msg.getCustomData()));
          msg.setCustomData(new byte[0]); // turn preconfigured bad dcws into cannot-decodes

        } else if(msg.getServiceId() != 0) { // dcw looks ok and has sid, check for services sharing the same dcw sequence

          if(replyMap.containsKey(msg)) {
            CamdNetMessage prev = (CamdNetMessage)replyMap.get(msg);
            if(prev.getServiceId() != msg.getServiceId()) {
              String prevId = Integer.toHexString(prev.getServiceId()) + ":" + prev.getProfileName();
              String msgId = Integer.toHexString(msg.getServiceId()) + ":" + msg.getProfileName();
              Set links = (Set)sidMap.get(prevId);
              if(links == null) links = (Set)sidMap.get(msgId);
              if(links == null) links = new TreeSet();
              ProxyConfig config = ProxyConfig.getInstance();
              links.add(config.getService(prev.getProfileName(), prev.getServiceId()).toString());
              links.add(config.getService(msg.getProfileName(), msg.getServiceId()).toString());
              if(sidMap.put(prevId, links) == null || sidMap.put(msgId, links) == null) {
                System.out.println("Link found: " + links + "\n\t" + prev + "\n\t" + msg);
              }
            }

          }

          replyMap.put(msg, msg);

        }

      }
    } catch (Throwable t) {
      t.printStackTrace(); // intentional catch-all while debugging this use case
    }
    return msg;
  }

  private static boolean checksumDcw(byte[] data) {
    if(data[3] != (byte)((data[0] + data[1] + data[2])&0xFF) || data[7] != (byte)((data[4] + data[5] + data[6])&0xFF)) {
      return false;
    }
    if(data[11] != (byte)((data[8] + data[9] + data[10])&0xFF) || data[15] != (byte)((data[12] + data[13] + data[14])&0xFF)) {
      return false;
    }
    return true;
  }

  private static boolean isBadDcw(CamdNetMessage msg, Set badDcws) {
    byte[] badDcw;
    for(Iterator iter = badDcws.iterator(); iter.hasNext(); ) {
      badDcw = (byte[])iter.next();
      if(Arrays.equals(msg.getCustomData(), badDcw)) {
        return true;
      }
    }
    return false;
  }

  public byte[] getResource(String path, boolean admin) {
    return null; 
  }

  public byte[] getResource(String path, byte[] inData, boolean admin) {
    return null;
  }

}
