package com.bowman.cardserv.cws;

import com.bowman.cardserv.util.ProxyXmlConfig;
import com.bowman.cardserv.*;
import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.crypto.DESUtil;

import java.net.SocketException;
import java.io.IOException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Jan 10, 2010
 * Time: 6:19:21 PM
 */
public class ChameleonCwsConnector extends NewcamdCwsConnector implements MultiCwsConnector, CwsListener {

  private Set selectedProfiles = new HashSet();
  private Set receivedData = new HashSet(); 
  private Set unmappedData = Collections.synchronizedSet(new HashSet());
  private Map profileMap = new HashMap();

  private String remoteVersion;

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    super.configUpdated(xml);

    clientId = new byte[] {0x6D, 0x67};
    profile = CaProfile.MULTIPLE;
    asynchronous = true;
    noEncrypt = false;
    overrideChecks = true;

    String profiles = xml.getStringValue("profiles", "");
    if(profiles.length() > 0) {
      selectedProfiles.clear();
      String[] names = profiles.split(" "); CaProfile profile;
      ProxyConfig config = ProxyConfig.getInstance();
      for(int i = 0; i < names.length; i++) {
        profile = config.getProfile(names[i]);
        if(profile != null && profile != CaProfile.MULTIPLE) selectedProfiles.add(profile);
      }
    } else {
      selectedProfiles = null;
    }

    if(!receivedData.isEmpty()) refreshMappings();
  }

  public void run() {
    alive = true;
    CamdNetMessage msg;

    try {

      msg = CamdNetMessage.getNewcamdLoginMessage(user, password, clientId);
      msg.getFixedData()[9] = 0x11; // todo
      doLogin(msg);

      msg = new CamdNetMessage(EXT_GET_VERSION);
      sendMessage(msg);

      if(conn != null && conn.isConnected()) {
        conn.setSoTimeout(SESSION_SO_TIMEOUT);
        connecting = false;

        connManager.addCwsListener(this);

        while(alive && conn != null) {
          msg = conn.readMessage();
          lastSent = null;
          if(msg == null) {
            alive = false;
            logger.warning("Connection closed");
          } else {
            
            if(msg.isEcm()) { // ecm reply, report to listener
              if(!reportReply(msg)) logger.fine("No listener found for ECM reply: " + msg);
            } else if(msg.isEmm()) { // emm reply, do nothing
              logger.fine("EMM reply ignored: " + msg);
            } else if(msg.isKeepAlive()) { // keep alive reply, report to reset time-out mode if set
              logger.fine("Keep-alive reply received: [" + msg.getSequenceNr() + "]");
              reportReply(msg);
            } else if(msg.isOsdMsg()) {
              logger.info("Mgcamd OSD message received: " + msg);
            } else {
              handleExtendedNewcamd(msg);
            }
          }
        }
      }

    } catch(SocketException e) {
      logger.warning("Connection closed: " + e.getMessage());
    } catch(IOException e) {
      logger.throwing("Exception reading/decrypting message: " + e, e);
    } catch(Exception e) {
      e.printStackTrace();
    }

    conn = null;
    remoteCard = null;
    receivedData.clear();
    unmappedData.clear();
    remoteVersion = null;
    connManager.removeCwsListener(this);

    // this connection died, any pending request isn't likely to succeed :) tell the cache
    reset();

    if(!connecting) {
      lastDisconnectTimeStamp = System.currentTimeMillis();
      connManager.cwsDisconnected(this);
      synchronized(connManager) {
        connManager.notifyAll();
      }
    }
    readerThread = null;
    connecting = false;

    logger.info("Connector dying");
  }

  public boolean sendEcmRequest(CamdNetMessage request, ProxySession listener) {
    // ensure outgoing messages have the caid and provider ident in the newcamd header
    request.setCaIdInHdr(request.getCaId());
    request.setProviderInHdr(request.getProviderIdent());
    return super.sendEcmRequest(request, listener);
  }

  public boolean canDecode(CamdNetMessage request) {
    if(request.getCaId() <= 0 || request.getProviderIdent() == -1) return false; // require identified traffic
    else return profileMap.containsKey(new CaidProviderPair(request.getCaId(), request.getProviderIdent()));
  }

  protected void setRemoteCard(CardData card) {
    super.setRemoteCard(card);

    Integer[] providers = card.getProvidersAsInt();
    for(int i = 0; i < providers.length; i++)
      addProfileMapping(new CaidProviderPair(card.getCaId(), providers[i].intValue()), true);
  }

  private void handleExtendedNewcamd(CamdNetMessage msg) {

    switch(msg.getCommandTag()) {

      case EXT_ADD_CARD:
        CaidProviderPair pair = new CaidProviderPair(msg.getCaIdFromHdr(), msg.getProviderFromHdr());
        receivedData.add(pair);
        addProfileMapping(pair, true);
        logger.fine("Received card data [" + pair + "] (port: " + msg.getServiceId() + ")");
        break;

      case EXT_REMOVE_CARD:
        CaidProviderPair remPair = new CaidProviderPair(msg.getCaIdFromHdr(), msg.getProviderFromHdr());
        profileMap.remove(remPair);
        unmappedData.remove(remPair);
        logger.fine("Removed card data [" + remPair + "] (port: " + msg.getServiceId() + ")");
        break;

      case EXT_GET_VERSION:
        remoteVersion = msg.getStringData()[0];
        break;

      case EXT_SID_LIST:
        logger.fine("Sid list ignored: " + DESUtil.bytesToString(msg.getRawIn())); // todo
        break;

      default:
        logger.warning("Unknown msg received from CWS: " + msg.getCommandName() + " - " + DESUtil.bytesToString(msg.getRawIn()));
        break;

    }

  }

  private boolean addProfileMapping(CaidProviderPair pair, boolean log) {
    Set profiles = selectedProfiles==null?config.getRealProfiles():selectedProfiles;
    CaProfile profile; boolean mapped = false;
    for(Iterator iter = profiles.iterator(); iter.hasNext(); ) {
      profile = (CaProfile)iter.next();
      if(profile.getCaId() == pair.caId && profile.getProviderSet().contains(new Integer(pair.providerIdent))) {
        if(profileMap.containsKey(pair) && profile != profileMap.get(pair)) {
          logger.warning("Ambigious situation [" + pair + "] maps to multiple profiles: " + profile + ", " +
              profileMap.get(pair) + " (specify <profiles> list for connector and exclude overlapping profiles)");
        } else {
          CaProfile previous = (CaProfile)profileMap.put(pair, profile);
          if(previous == null || previous != profile) {
            if(log) logger.info("Mapped [" + pair + "] to: " + profile);
            else logger.fine("Mapped [" + pair + "] to: " + profile);
          }
          mapped = true;
        }
      }

    }
    if(!mapped) unmappedData.add(pair);
    return mapped;
  }

  private void refreshMappings() {
    profileMap.clear(); unmappedData.clear();
    CaidProviderPair pair;
    for(Iterator iter = receivedData.iterator(); iter.hasNext(); ) {
      pair = (CaidProviderPair)iter.next();
      addProfileMapping(pair, false);
    }
  }

  public Properties getRemoteInfo() {
    Properties p = new Properties();

    CaProfile profile; String label;
    for(Iterator iter = profileMap.values().iterator(); iter.hasNext(); ) {
      profile = (CaProfile)iter.next();
      label = DESUtil.intToHexString(profile.getNetworkId(), 4);
      p.setProperty(label + "-caid", DESUtil.intToHexString(profile.getCaId(), 4));
      p.setProperty(label + "-providers", ProxyConfig.providerIdentsToString(profile.getProviderIdents()));
    }
    if(remoteVersion != null) p.setProperty("remote-version", remoteVersion);
    if(!unmappedData.isEmpty()) p.setProperty("umapped-data", unmappedData.toString());
    return p;
  }

  public boolean hasMatchingProfile(int networkId, int caId) {
    if(networkId == 0 || caId == 0) return false;
    CaProfile profile; 
    for(Iterator iter = profileMap.values().iterator(); iter.hasNext(); ) { // todo, terrible to do this for every msg
      profile = (CaProfile)iter.next();
      if(profile.getNetworkId() == networkId && profile.getCaId() == caId) return true;
    }
    return false;
  }

  public void clearRemoteState(boolean all) {
    CaProfile profile;
    for(Iterator iter = profileMap.values().iterator(); iter.hasNext(); ) {
      profile = (CaProfile)iter.next();
      connManager.getServiceMapper(profile.getName()).resetStatus(name, all);
    }
  }

  public String getLabel() {
    return "ChameleonCws[" + name + ":*]";
  }

  public String getProtocol() {
    return "Chameleon"; // not really, but ExtNewcamdChameleon2CwsConnector is too long for the web.
  }

  public void cwsConnected(CwsConnector cws) {
    refreshMappings();
  }

  public void cwsDisconnected(CwsConnector cws) {
    refreshMappings();
  }

  public void cwsConnectionFailed(CwsConnector cws, String message) {}
  public void cwsEcmTimeout(CwsConnector cws, String message, int failureCount) {}
  public void cwsLostService(CwsConnector cws, TvService service, boolean show) {}
  public void cwsFoundService(CwsConnector cws, TvService service, boolean show) {}
  public void cwsInvalidCard(CwsConnector cws, String message) {}

  public void cwsProfileChanged(CaProfile profile, boolean added) {
    refreshMappings();
  }
}
