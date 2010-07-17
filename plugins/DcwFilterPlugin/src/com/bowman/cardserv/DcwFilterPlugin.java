package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.crypto.DESUtil;

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
  private Set profiles = new HashSet();
  private Set badDcws = new HashSet();

  public DcwFilterPlugin() {
    logger = ProxyLogger.getLabeledLogger(getClass().getName());
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    String profilesStr = xml.getStringValue("profiles", "");
    if(profilesStr != null && profilesStr.length() > 0) {
      profiles = new HashSet(Arrays.asList(profilesStr.toLowerCase().split(" ")));
    } else profiles = Collections.EMPTY_SET;
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
    return null;
  }

  public CamdNetMessage doFilter(ProxySession session, CamdNetMessage msg) {    
    return msg; // do nothing with ecm requests from clients
  }
  
  public CamdNetMessage doReplyFilter(CwsConnector connector, CamdNetMessage msg) {
    try {
      if(msg.isDcw() && msg.getDataLength() == 16) {
        if(profiles.isEmpty() || profiles.contains(connector.getProfileName().toLowerCase())) {
          byte[] badDcw;
          for(Iterator iter = badDcws.iterator(); iter.hasNext(); ) {
            badDcw = (byte[])iter.next();
            if(Arrays.equals(msg.getCustomData(), badDcw)) {
              msg.setCustomData(new byte[0]);
              break;
            }
          }
        }
      }
    } catch (Throwable t) {
      t.printStackTrace();
    }
    return msg; 
  }

  public byte[] getResource(String path, boolean admin) {
    return null; 
  }

  public byte[] getResource(String path, byte[] inData, boolean admin) {
    return null;
  }


}
