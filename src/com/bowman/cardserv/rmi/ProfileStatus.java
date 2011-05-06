package com.bowman.cardserv.rmi;

import com.bowman.cardserv.*;
import com.bowman.cardserv.cws.*;

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
  private final long maxCwWait, congestionLimit, maxCacheWait;
  private final List listenPorts = new ArrayList();
  private final boolean enabled, cacheOnly, debug, mismatchedCards, requiresProviderMatch;
  private final String resetStr, blockedStr, allowedStr;

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
    CwsConnectorManager cm = ProxyConfig.getInstance().getConnManager();
    maxCwWait = cm.getMaxCwWait(profile);
    congestionLimit = cm.getCongestionLimit(profile);
    maxCacheWait = ProxyConfig.getInstance().getCacheHandler().getMaxCacheWait(maxCwWait);
    CwsServiceMapper csm = cm.getServiceMapper(name);
    resetStr = csm.getResetServicesStr();
    blockedStr = csm.getBlockedServicesStr();
    allowedStr = csm.getAllowedServicesStr();
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

  public long getMaxCwWait() {
    return maxCwWait;
  }

  public long getCongestionLimit() {
    return congestionLimit;
  }

  public long getMaxCacheWait() {
    return maxCacheWait;
  }

  public String getResetStr() {
    return resetStr;
  }

  public String getBlockedStr() {
    return blockedStr;
  }

  public String getAllowedStr() {
    return allowedStr;
  }
}
