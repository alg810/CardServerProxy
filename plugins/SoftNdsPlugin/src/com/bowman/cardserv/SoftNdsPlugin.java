package com.bowman.cardserv;

import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.*;

import java.security.*;
import java.util.*;

public class SoftNdsPlugin implements ProxyPlugin {

  private static final String PLUGIN_NAME = "SoftNdsPlugin";
  private static final String PLUGIN_DESC = "Softcam decryption of NDS";
  private static final int CAID = 0x090F; // are there other nds ca-ids where this plugin can be used?

  private ProxyLogger logger;
  private Set profiles = new HashSet();
  private Set services = new HashSet();
  private int count;

  private byte[] P3;
  private byte[] P4;

  private static class EcmData {
    byte[] P1, P2, P3, P4, CW;
    boolean odd;
  }

  public SoftNdsPlugin() {
    logger = ProxyLogger.getLabeledLogger(getClass().getName());
  }

  public void start(CardServProxy cardServProxy) {}

  public void stop() {}

  public String getName() {
    return PLUGIN_NAME;
  }

  public String getDescription() {
    return PLUGIN_DESC;
  }
  
  public Properties getProperties() {
    Properties p = new Properties();
    p.setProperty("ca-id", Integer.toHexString(CAID));
    p.setProperty("decoded-count", String.valueOf(count));
    if(!services.isEmpty()) p.setProperty("decoded-services", String.valueOf(services));
    if(!profiles.isEmpty()) p.setProperty("profiles", String.valueOf(profiles));
    return p;
  }

  public byte[] getResource(String path, boolean admin) {
    return null;
  }

  public byte[] getResource(String path, byte[] inData, boolean admin) {
    return null;
  }

  public CamdNetMessage doFilter(ProxySession session, CamdNetMessage msg) {
    try {
      if(msg.isEcm() && msg.getType() == CamdNetMessage.TYPE_RECEIVED && !msg.isFiltered()) { // look at incoming ecms only
        if(profiles.isEmpty() || profiles.contains(msg.getProfileName().toLowerCase())) { // restrict to profile if set
          if(msg.getDataLength() < 10) return msg; // ignore bad/truncated ecms

          switch(msg.getCaId()) {
            case CAID:
              CamdNetMessage reply = handleSoftNds(msg);
              if(reply != null) {
                count++;
                services.add(Integer.toHexString(msg.getServiceId()) + ":" + msg.getProfileName());
                msg.setFilteredBy(PLUGIN_NAME); // set filtered to exclude from normal proxy processing later
                msg.setInstant(true);
                session.sendEcmReply(msg, reply);
              } else {
                // do nothing
              }
              break;
          }
        }
      }
    } catch(Throwable t) {
      logger.throwing(t);
      t.printStackTrace();
    }

    return msg;
  }

  private CamdNetMessage handleSoftNds(CamdNetMessage request) {
    EcmData ecmData = parseEcm(request.getCustomData());
    byte[] dw = null;

    if(ecmData != null) {
      ecmData.odd = ((request.getCommandTag() & 1) > 0) ? true : false;
      dw = calculateDW(request.getServiceId(), ecmData);
    }
    if(dw != null) {
      CamdNetMessage reply = request.getEmptyReply();
      reply.setCustomData(dw);
      reply.refreshDataHash();
      reply.setServiceId(request.getServiceId());
      return reply;
    }

    return null;
  }

  private EcmData parseEcm(byte[] ecm) {
    if(ecm.length < 10) return null;

    byte[] P1 = new byte[10];
    byte[] P2 = new byte[4];
    byte[] CW = new byte[8];

    int pos = 0;
    int len = ecm.length;

    if(ecm[pos] != 0x00 || ecm[pos + 1] != 0x00 || ecm[pos + 2] != 0x01) return null;
    
    pos += 3;

    int index = 0;

    while(index < 3 && pos < len) {
      switch(index) {
        case 0:
          pos++;
          for(int i = 0; i < P1.length; i++) P1[i] = ecm[pos++];
          if(((P1[8] & (1 << 0)) == 0) || ((P1[9] & (1 << 4)) == 0)) return null;
          pos += 5;
          index++;
          break;
        case 1:
          pos++;
          for(int i = 0; i < CW.length; i++) CW[i] = ecm[pos++];
          index++;
          break;
        case 2:
          pos++;
          for(int i = 0; i < P2.length; i++) P2[i] = ecm[pos++];
          pos += 2;
          index++;
          break;
      }
    }

    if(index != 3) return null;

    EcmData ecmData = new EcmData();
    ecmData.P1 = P1;
    ecmData.P2 = P2;
    ecmData.P3 = P3;
    ecmData.P4 = P4;
    ecmData.CW = CW;

    return ecmData;
  }

  private byte[] calculateDW(int sid, EcmData ecm) {
    try {
      MessageDigest algorithm = MessageDigest.getInstance("MD5");
      algorithm.reset();
      algorithm.update(ecm.P1);
      algorithm.update(ecm.P2);
      algorithm.update(ecm.P3);
      algorithm.update(ecm.P4);

      byte messageDigest[] = algorithm.digest();
      byte[] newDW = new byte[8];
      for(int i = 0; i < 8; i++) newDW[i] = (byte)(messageDigest[i + 8] ^ ecm.CW[i]);

      // fix DW Checksum
      newDW[3] = (byte)((newDW[0] + newDW[1] + newDW[2]) & 0xFF);
      newDW[7] = (byte)((newDW[4] + newDW[5] + newDW[6]) & 0xFF);

      logger.fine("New DCW[" + Integer.toHexString(sid) + "] : " + DESUtil.bytesToString(newDW));

      byte[] DW = new byte[16];
      if(ecm.odd) System.arraycopy(newDW, 0, DW, 8, newDW.length);
      else System.arraycopy(newDW, 0, DW, 0, newDW.length);

      return DW;
    } catch(NoSuchAlgorithmException e) {
    }
    return null;
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    String profilesStr = xml.getStringValue("profiles", "");
    if(profilesStr != null && profilesStr.length() > 0) {
      profiles = new HashSet(Arrays.asList(profilesStr.toLowerCase().split(" ")));
    } else profiles = Collections.EMPTY_SET;

    P3 = xml.getBytesValue("P3");
    P4 = xml.getBytesValue("P4");

    try {
      byte[] testEcm = xml.getBytesValue("testEcm");
      if (testEcm != null) {
        EcmData ecmData = parseEcm(testEcm);
        if(ecmData != null) {
          byte[] DW = calculateDW(0, ecmData);
          if (DW != null) logger.finest("Test DCW: " + DESUtil.bytesToString(DW));
        }
      }
    } catch (ConfigException e) {}

    logger.fine("Configuration updated.");
  }
}
