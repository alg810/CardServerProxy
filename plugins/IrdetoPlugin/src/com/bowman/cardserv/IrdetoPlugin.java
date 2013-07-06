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
public class IrdetoPlugin implements ProxyPlugin {

  private ProxyLogger logger;
  private Set profiles = new HashSet();

  public IrdetoPlugin() {
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
    return "IrdetoPlugin";
  }

  public String getDescription() {
    return "Extracts irdeto ch-id from irdeto ecms.";
  }
  
  public Properties getProperties() {
    return null;
  }

  public CamdNetMessage doFilter(ProxySession session, CamdNetMessage msg) {
    try {
      if(msg.isEcm() && msg.getType() == CamdNetMessage.TYPE_RECEIVED && !msg.isFiltered()) { // look at incoming ecms only

        if(profiles.isEmpty() || profiles.contains(session.getProfileName().toLowerCase())) {
          if((msg.getCaId() & 0xFF00) == 0x0600) {
            if(msg.getDataLength() < 6) {
              logger.warning("Truncated Irdeto ECM ignored: " + DESUtil.bytesToString(msg.getCustomData()));
              msg.setFilteredBy("IrdetoPlugin: Bad ECM length (" + msg.getDataLength() +")");
              session.sendEcmReply(msg, msg.getEmptyReply());
              return msg;
            }
            byte chId[] = new byte[2];
            System.arraycopy(msg.getCustomData(), 3, chId, 0, 2);
            msg.setCustomId(DESUtil.bytesToInt(chId));
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
