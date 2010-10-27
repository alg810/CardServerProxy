package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.crypto.DESUtil;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: merek
 * Date: Okt 27, 2009
 * Time: 02:14:10 PM
 */
public class BetacryptTunnelPlugin implements ProxyPlugin {

  private ProxyLogger logger;
  private Set profiles = new HashSet();
  private int targetNetworkId, tunneledCount = 0;
  private byte [] ecmHeader;

  public BetacryptTunnelPlugin() {
    logger = ProxyLogger.getLabeledLogger(getClass().getName());
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    String profilesStr = xml.getStringValue("profiles", "");
    int targetNetworkIdConfig = Integer.parseInt(xml.getStringValue("target-network-id", "0085"), 16);
    byte[] custEcmHeader = DESUtil.stringToBytes(xml.getStringValue("ecm-header", "C7 00 00 00 01 10 10 00 87"));

    if(profilesStr != null && profilesStr.length() > 0) {
      profiles = new HashSet(Arrays.asList(profilesStr.toLowerCase().split(" ")));
    } else profiles = Collections.EMPTY_SET;

    if (targetNetworkIdConfig == 0)
      throw new ConfigException(xml.getFullName(), "target-network-id should not be zero: " + targetNetworkIdConfig);
    else
      targetNetworkId = targetNetworkIdConfig;

    if(custEcmHeader.length != 9)
      throw new ConfigException(xml.getFullName(), "ecm-header not 9 bytes: " + DESUtil.bytesToString(custEcmHeader));
    else
      ecmHeader = custEcmHeader;
  }

  public void start(CardServProxy proxy) {
    logger.info("Adding header to Nagra3 ECMs: " + DESUtil.bytesToString(ecmHeader));
  }
  public void stop() {}

  public String getName() {
    return "BetacryptTunnelPlugin";
  }

  public String getDescription() {
    return "Converts plain Nagra3 ECMs to Betacrypt tunneled ECMs (CaId 0x1833/0x1834).";
  }
  
  public Properties getProperties() {
    Properties p = new Properties();
    if(tunneledCount > 0) p.setProperty("tunneled-count", String.valueOf(tunneledCount));
    if(ecmHeader != null) p.setProperty("ecm-header", DESUtil.bytesToString(ecmHeader));
    if(p.isEmpty()) return null;
    else return p;
  }

  public CamdNetMessage doFilter(ProxySession session, CamdNetMessage msg) {
    try {
      if(msg.isEcm() && msg.getType() == CamdNetMessage.TYPE_RECEIVED && !msg.isFiltered()) {

        if(profiles.isEmpty() || profiles.contains(session.getProfileName().toLowerCase())) {

          if((msg.getCaId() == 0x1833 || msg.getCaId() == 0x1834) &&
              msg.getDataLength() >= 134 && msg.getDataLength() < 144) {
          	
          	logger.fine("Processing ecm (length: " + msg.getDataLength() + "): " +
                        DESUtil.bytesToString(msg.getCustomData()));
          	
            byte[] ecmData = new byte[msg.getDataLength() + 10];
            boolean odd = ((msg.getCommandTag() & 1) > 0) ? true : false;

            // copy new header
            System.arraycopy(ecmHeader, 0, ecmData, 0, 9);

            // copy old ecm behind the header
            System.arraycopy(msg.getCustomData(), 0, ecmData, 10, msg.getDataLength());

            // set odd/even marker in new betacrypt header
            if (odd) ecmData[9] = 0x13;
            else ecmData[9] = 0x12;
           
			// set new caid and network id
            switch (msg.getCaId()) {
                case 0x1833:
                    msg.setCaIdInHdr(0x1702);
			        msg.setCaId(0x1702);
			        msg.setNetworkId(targetNetworkId);
                    break;

                case 0x1834:
                    msg.setCaIdInHdr(0x1722);
			        msg.setCaId(0x1722);
			        msg.setNetworkId(targetNetworkId);
                    break;
            }

            msg.setCustomData(ecmData);
            tunneledCount++;
            
            logger.fine("Resulting ecm (length: " + msg.getDataLength() + "): " +
                        DESUtil.bytesToString(msg.getCustomData()));
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