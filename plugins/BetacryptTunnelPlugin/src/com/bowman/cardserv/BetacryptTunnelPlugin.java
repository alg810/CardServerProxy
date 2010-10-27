package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.crypto.DESUtil;

import java.util.*;


public class BetacryptTunnelPlugin implements ProxyPlugin {

  private ProxyLogger logger;
  private Set profiles = new HashSet();
  private int targetNetworkId;

  private static final byte[] headerN3 = DESUtil.stringToBytes("C7 00 00 00 01 10 10 00 87 12");

  public BetacryptTunnelPlugin() {
    logger = ProxyLogger.getLabeledLogger(getClass().getName());
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    String profilesStr = xml.getStringValue("profiles", "");
    if(profilesStr != null && profilesStr.length() > 0) {
      profiles = new HashSet(Arrays.asList(profilesStr.toLowerCase().split(" ")));
    } else profiles = Collections.EMPTY_SET;

    int targetNetworkIdConfig = Integer.parseInt(xml.getStringValue("target-network-id", "0000"), 16);
    if (targetNetworkIdConfig > 0) {
        targetNetworkId = targetNetworkIdConfig;
    } else targetNetworkId = 0x0085;
  }

  public void start(CardServProxy proxy) {}
  public void stop() {}

  public String getName() {
    return "BetacryptTunnelPlugin";
  }

  public String getDescription() {
    return "Converts plain Nagra3 ECMs to Betacrypt tunneled ECMs (CaId 0x1833/0x1834).";
  }
  
  public Properties getProperties() {
    return null;
  }

  public CamdNetMessage doFilter(ProxySession session, CamdNetMessage msg) {
    try {
      if(msg.isEcm() && msg.getType() == CamdNetMessage.TYPE_RECEIVED && !msg.isFiltered()) {

        if(profiles.isEmpty() || profiles.contains(session.getProfileName().toLowerCase())) {
          if((msg.getCaId() == 0x1833 || msg.getCaId() == 0x1834) && msg.getDataLength() >= 134) {
          	
          	logger.fine("Processing ecm (length: " + msg.getDataLength() + "): " + DESUtil.bytesToString(msg.getCustomData()));
          	
            byte[] ecmData = new byte[msg.getDataLength() + 10];
            boolean odd = ((msg.getCommandTag() & 1) > 0) ? true : false;

            System.arraycopy(headerN3, 0, ecmData, 0, 10);
            System.arraycopy(msg.getCustomData(), 0, ecmData, 10, msg.getDataLength());

            // odd or even  
            if (odd) ecmData[9] = 0x13;
           
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
            
            logger.fine("Resulting ecm (length: " + msg.getDataLength() + "): " + DESUtil.bytesToString(msg.getCustomData()));
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