package com.bowman.cardserv.rmi;

import com.bowman.cardserv.*;

import java.io.Serializable;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2007-jan-24
 * Time: 23:45:06
 */
public class ProfileStatus extends AbstractStatus implements Serializable {

  private final String name, caId, networkId, providerIdents;
  private final int services, conflicts, sessions;
  private final List listenPorts = new ArrayList();
  private final boolean enabled, cacheOnly, debug, mismatchedCards, requiresProviderMatch;

  public ProfileStatus(CaProfile profile) {
    name = profile.getName();
    caId = profile.getCaIdStr();
    networkId = profile.getNetworkIdStr();
    services = profile.getServices().size();
    conflicts = profile.getServiceConflicts();
    sessions = profile.getSessionCount();
    enabled = profile.isEnabled();
    cacheOnly = profile.isCacheOnly();
    debug = profile.isDebug();
    mismatchedCards = profile.isMismatchedCards();
    providerIdents = profile.getProviderIdentsStr();
    requiresProviderMatch = profile.isRequireProviderMatch();
    for(Iterator iter = profile.getListenPorts().iterator(); iter.hasNext(); ) {
      listenPorts.add(new PortStatus((ListenPort)iter.next()));
    }
  }

  public String getName() {
    return name;
  }

  public String getCaId() {
    return caId;
  }

  public String getNetworkId() {
    return networkId;
  }

  public PortStatus[] getListenPorts() {
    if(listenPorts == null || listenPorts.isEmpty()) return new PortStatus[0];
    else return (PortStatus[])listenPorts.toArray(new PortStatus[listenPorts.size()]);
  }

  public int getServices() {
    return services;
  }

  public int getConflicts() {
    return conflicts;
  }

  public int getSessions() {
    return sessions;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isCacheOnly() {
    return cacheOnly;
  }

  public boolean isDebug() {
    return debug;
  }

  public boolean isMismatchedCards() {
    return mismatchedCards;
  }

  public String getProviderIdents() {
    return providerIdents;
  }

  public boolean isRequiresProviderMatch() {
    return requiresProviderMatch;
  }
}
