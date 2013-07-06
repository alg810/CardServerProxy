package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.crypto.DESUtil;

import java.util.*;

public class ProviderIdentPlugin implements ProxyPlugin {

  private Set profiles = new HashSet();
  private ProxyLogger logger;

  public ProviderIdentPlugin() {
    logger = ProxyLogger.getLabeledLogger(getClass().getName());
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    String profilesStr = xml.getStringValue("profiles", "");
    if(profilesStr != null && profilesStr.length() > 0) {
      profiles = new HashSet(Arrays.asList(profilesStr.toLowerCase().split(" ")));
    } else profiles = Collections.EMPTY_SET;
  }

  public void start(CardServProxy proxy) {}

  public void stop() {}

  public String getName() {
    return "ProviderIdentPlugin";
  }

  public String getDescription() {
    return "Extracts provider ident from viaccess and seca ecms.";
  }
  
  public Properties getProperties() {
    return null;
  }

  public CamdNetMessage doFilter(ProxySession session, CamdNetMessage msg) {
    try {
      if(msg.isEcm() && msg.getType() == CamdNetMessage.TYPE_RECEIVED && !msg.isFiltered()) { // look at incoming ecms only
        if(profiles.isEmpty() || profiles.contains(session.getProfileName().toLowerCase())) {
          if(msg.getDataLength() < 10) return msg;
          switch(msg.getCaId()) {
            case 0x0500:
              handleViaccess(msg);
              break;
            case 0x0100:
              handleSeca(msg);
              break;
          }
        }
      }
    } catch (Throwable t) {
      t.printStackTrace();
    }
    
    return msg;
  }

  protected void handleViaccess(CamdNetMessage msg) {
    byte[] data = msg.getCustomData();
    int del = data[1] & 0xFF;
    int del2 = data[2] & 0xFF;
    // int i = del != 0xD2 ? 0 : 3;
    int i = del != 0xD2 ? 0 : del2 + 2;
    if(data.length <= (2 + i)) {
      logger.warning("Unknown viaccess ecm structure or bad ecm from " + msg.getOriginAddress() + ": " + DESUtil.bytesToString(data));
      return;
    }
    int sec = data[2 + i] & 0xFF;
    int fir = data[1 + i] & 0xFF;
    if(sec == 3 && (fir == 0x90 || fir == 0x40)) {
      byte[] prcId = new byte[3];
      System.arraycopy(data, 3 + i, prcId, 0, 3);
      prcId[2] = (byte)(prcId[2] & 0xF0);
      int ident = DESUtil.bytesToInt(prcId);
      if(msg.getProviderIdent() > 0 && msg.getProviderIdent() != ident)
        logger.warning("Viaccess provider ident " + DESUtil.intToByteString(ident, 3) +
            " doesn't match ident in header: " + DESUtil.intToByteString(msg.getProviderIdent(), 3) +
            " (overwriting)");
      msg.setProviderIdent(ident);
    } else logger.finer("No provider-ident in: " + DESUtil.bytesToString(data));
  }

  protected void handleSeca(CamdNetMessage msg) {
    byte[] prcId = new byte[2];
    System.arraycopy(msg.getCustomData(), 0 , prcId, 0, 2);
    int ident = DESUtil.bytesToInt(prcId);
    if(msg.getProviderIdent() > 0 && msg.getProviderIdent() != ident)
      logger.warning("Seca provider ident " + DESUtil.intToByteString(ident, 3) +
          " doesn't match ident in header: " + DESUtil.intToByteString(msg.getProviderIdent(), 3) +
          " (overwriting)");
    msg.setProviderIdent(ident);
  }

  public byte[] getResource(String path, boolean admin) {
    return null; 
  }

  public byte[] getResource(String path, byte[] inData, boolean admin) {
    return null;
  }
  
}
