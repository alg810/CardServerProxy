package com.bowman.cardserv.session;

import com.bowman.cardserv.*;
import com.bowman.cardserv.cws.CwsConnectorManager;
import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.interfaces.*;

import java.net.Socket;
import java.util.*;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Jan 12, 2010
 * Time: 4:22:37 PM
 */
public class ExtNewcamdSession extends NewcamdSession implements CwsListener {

  private Set allowedClients = new HashSet(); // todo
  private Map sentData = new HashMap();

  private CardData mainCard;
  private int mainCaId, mainNetworkId;
  private Integer[] mergedProviders;

  private boolean catchAllExists;

  public ExtNewcamdSession(Socket conn, ListenPort listenPort, CamdMessageListener listener) {
    super(conn, listenPort, listener);

    String mainCaId = listenPort.getStringProperty("main-ca-id");
    if(mainCaId != null) {
      this.mainCaId = Integer.parseInt(mainCaId, 16);
      this.mergedProviders = getProvidersForCaId(this.mainCaId);
    }
    catchAllExists = ProxyConfig.getInstance().isCatchAll();
    initSession();
  }

  protected void initSession() {
    String clients = listenPort.getStringProperty("clients");
    if(clients != null) allowedClients.addAll(Arrays.asList(clients.split(" ")));
    String excludeProfiles = listenPort.getStringProperty("exclude-profiles");
    ProxyConfig config = ProxyConfig.getInstance();
    mappedProfiles = config.getRealProfiles();
    if(excludeProfiles != null) {
      Set excluded = new HashSet(Arrays.asList(excludeProfiles.split(" "))); CaProfile profile;
      for(Iterator iter = mappedProfiles.iterator(); iter.hasNext(); ) {
        profile = (CaProfile)iter.next();
        if(excluded.contains(profile.getName())) iter.remove();
      }
    }
    if(user != null) setupLimits(ProxyConfig.getInstance().getUserManager());
  }

  protected Integer[] getProvidersForCaId(int caId) {
    CaProfile profile; Set providers = new HashSet();
    for(Iterator iter = mappedProfiles.iterator(); iter.hasNext(); ) {
      profile = (CaProfile)iter.next();
      if(profile.getCaId() == caId) providers.addAll(profile.getProviderSet());
    }
    return (Integer[])providers.toArray(new Integer[providers.size()]);
  }

  protected CamdNetMessage getLoginOkMsg() {
    CamdNetMessage msg = super.getLoginOkMsg();
    msg.setServiceId(0x6E73);
    msg.getFixedData()[9] = 0x14; // newcs protocol version?
    return msg;
  }

  protected boolean handleMessage(CamdNetMessage msg) {
    switch(msg.getCommandTag()) {
      case EXT_GET_VERSION:
        CamdNetMessage versionMsg = new CamdNetMessage(EXT_GET_VERSION);
        versionMsg.setCustomData("1.67".getBytes());
        sendMessageNative(versionMsg, msg.getSequenceNr(), true);
        fireCamdMessage(versionMsg, true);
        break;

      case MSG_CARD_DATA_REQ:
        // if main-ca-id is configured: attempt to merge provider idents for that id as a cccam workaround
        // if a single au-card can be identified for this user, use that instead of main-ca-id        
        CwsConnector auConn = findAuConnector();
        CardData card = getAuCardData(auConn);
        boolean au = true;
        if(card == null) {
          card = CardData.createEmptyData(mainCaId);
          au = false;
        }
        if(card.getCaId() == mainCaId && mergedProviders != null) card = CardData.createMergedData(card, mergedProviders);

        if(au) {
          mainCard = card;
          mainNetworkId = auConn.getProfile().getNetworkId();
        } else mainCard = new CardData(card.getData(true));

        CamdNetMessage cardDataMsg = new CamdNetMessage(MSG_CARD_DATA);
        cardDataMsg.setCustomData(card.getData(!au));
        sendMessageNative(cardDataMsg, msg.getSequenceNr(), true);
        fireCamdMessage(cardDataMsg, true);
        logger.fine("Sent card-data: " + card);

        sentData.clear();
        Map map = getProfileMap(); CaidProviderPair pair;
        for(Iterator iter = map.keySet().iterator(); iter.hasNext(); ) {
          pair = (CaidProviderPair)iter.next();
          sendAddCardData(msg.getSequenceNr(), pair, (CaProfile)map.get(pair));
        }
        ProxyConfig.getInstance().getConnManager().addCwsListener(this);

        logger.fine("Sent extended state: " + sentData);
        if(sentData.isEmpty() && !catchAllExists)
          logger.warning("No extended data available for user '" + user + "' (no provider-idents currently associated with any active and allowed profiles).");
        break;
      
      default:
        return handleNewcamd(msg);
    }
    return true;
  }

  void endSession() {
    ProxyConfig.getInstance().getConnManager().removeCwsListener(this);
    super.endSession();
  }

  private CwsConnector findAuConnector() {
    ProxyConfig config = ProxyConfig.getInstance();
    CwsConnectorManager cm = config.getConnManager(); List connectors = new ArrayList(); CwsConnector cws;
    for(Iterator iter = config.getRealProfiles().iterator(); iter.hasNext(); ) {
      cws = cm.getConnectorForAU(((CaProfile)iter.next()).getName(), user);
      if(cws != null) connectors.add(cws);
    }
    if(connectors.size() > 1) {
      logger.warning("AU-user '" + user + "' assigned for multiple active connectors: " + connectors + " (only " +
          connectors.get(0) + " is likely to receive the correct updates)");
    } else if(connectors.isEmpty()) return null;

    return (CwsConnector)connectors.get(0);
  }

  protected void sendAddCardData(int seqNr, CaidProviderPair pair, CaProfile profile) {
    CamdNetMessage addMsg = new CamdNetMessage(EXT_ADD_CARD);
    addMsg.setServiceId(listenPort.getPort()); // sid field used for portnumber
    addMsg.setCaIdInHdr(pair.caId);
    addMsg.setProviderInHdr(pair.providerIdent);
    sendMessageNative(addMsg, seqNr, true);
    sentData.put(pair, profile);
    fireCamdMessage(addMsg, true);
  }

  protected void sendRemoveCardData(int seqNr, CaidProviderPair pair) {
    CamdNetMessage delMsg = new CamdNetMessage(EXT_REMOVE_CARD);
    delMsg.setServiceId(listenPort.getPort()); // sid field used for portnumber
    delMsg.setCaIdInHdr(pair.caId);
    delMsg.setProviderInHdr(pair.providerIdent);
    sendMessageNative(delMsg, seqNr, true);
    sentData.remove(pair);
    fireCamdMessage(delMsg, true);
  }

  protected boolean profileAllowed(CaProfile profile) {
    Set allowed = ProxyConfig.getInstance().getUserManager().getAllowedProfiles(user);
    return allowed == null || allowed.isEmpty() || allowed.contains(profile.getName());
  }

  CamdNetMessage readMessage() throws IOException {
    // add extended info to msg and identify network id
    CamdNetMessage msg = super.readMessage();
    if(msg != null) {
      if(msg.isEcm()) {
        msg.setProviderIdent(msg.getProviderFromHdr());
        msg.setCaId(msg.getCaIdFromHdr());
        msg.setNetworkId(-1);

        CaidProviderPair pair = new CaidProviderPair(msg.getCaId(), msg.getProviderIdent());
        if(!sentData.containsKey(pair)) {
          if(!catchAllExists) logger.warning("User '" + user + "' sent request for ca-id/provider-ident not previously communicated: " + pair);
        } else msg.setNetworkId(((CaProfile)sentData.get(pair)).getNetworkId());
      } else if(msg.isEmm()) {
        if(mainCard != null) {
          msg.setCaId(mainCard.getCaId());         
          msg.setProviderIdent(msg.getProviderFromHdr());

          CaidProviderPair pair = new CaidProviderPair(mainCard.getCaId(), msg.getProviderIdent());
          if(!sentData.containsKey(pair)) {
            // not able to determine profile target for emm based on caid and ident in header (probably normal, no ident associated with emms?)
            // obtain profile from the card data context instead
            msg.setNetworkId(mainNetworkId);
          } else msg.setNetworkId(((CaProfile)sentData.get(pair)).getNetworkId());
        }      
      }
    }
    return msg;
  }

  private Map getProfileMap() {
    Map map = new HashMap();

    CaProfile profile; CaidProviderPair record;
    for(Iterator iter = mappedProfiles.iterator(); iter.hasNext(); ) {
      profile = (CaProfile)iter.next();
      if(profile.getNetworkId() > 0 && profile.getCaId() > 0) { // only present those profiles that have onid/caid set - todo
        if(profileAllowed(profile)) { // and only if the user has access to them
          Integer[] pi = profile.getProviderIdents();
          for(int i = 0; i < pi.length; i++) {
            record = new CaidProviderPair(profile.getCaId(), pi[i].intValue());
            if(!map.containsKey(record)) {
              map.put(record, profile);
            } else {
              if(!catchAllExists) logger.warning("Ambigious situation - [" + record + "] (profile: " + profile.getName() +
                  ") clashes with profile: " + ((CaProfile)map.get(record)).getName() +
                  " (both profiles can't be assigned to the same extended-newcamd port)");                  
            }
          }

        }
      }
    }
    return map;
  }

  private void updateSentState(Map map) {
    CaidProviderPair key;
    for(Iterator iter = map.keySet().iterator(); iter.hasNext(); ) {
      key = (CaidProviderPair)iter.next();
      if(!sentData.containsKey(key)) {
        sendAddCardData(-1, key, (CaProfile)map.get(key)); // no previous mapping
        logger.fine("New mapping: " + key + " -> " + map.get(key));
      } else {
        if(sentData.get(key) != map.get(key)) {
          logger.fine("Mapping change: " + key + " -> " + map.get(key) + " (was: " + sentData.get(key) + ")");
          sentData.put(key, map.get(key)); // change previous mapping, no need to send anything
        }
        // else no change
      }
    }
    for(Iterator iter = new ArrayList(sentData.keySet()).iterator(); iter.hasNext(); ) {
      key = (CaidProviderPair)iter.next();
      if(!map.containsKey(key)) {
        logger.fine("Mapping removed: " + key + " -> " + sentData.get(key));
        sendRemoveCardData(-1, key); // mapping disappeared
      }
    }
  }

  public String getProtocol() {
    return "ExtNewcamd";
  }

  public String getLastContext() {
    if(mainCard == null) return "?";
    else return mainCard.toString();
  }  

  public void cwsConnected(CwsConnector cws) {
    updateSentState(getProfileMap());
  }

  public void cwsDisconnected(CwsConnector cws) {
    updateSentState(getProfileMap());
  }

  public void cwsConnectionFailed(CwsConnector cws, String message) {}
  public void cwsEcmTimeout(CwsConnector cws, String message, int failureCount) {}
  public void cwsLostService(CwsConnector cws, TvService service, boolean show) {}
  public void cwsFoundService(CwsConnector cws, TvService service, boolean show) {}
  public void cwsInvalidCard(CwsConnector cws, String message) {}

  public void cwsProfileChanged(CaProfile profile, boolean added) {
    initSession();
    updateSentState(getProfileMap());
  }
}
