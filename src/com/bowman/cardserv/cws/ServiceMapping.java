package com.bowman.cardserv.cws;

import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.*;
import com.bowman.cardserv.tv.TvService;

import java.io.Serializable;

/**
 * Created by IntelliJ IDEA.
* User: bowman
* Date: Jan 15, 2010
* Time: 11:22:19 PM
*/
public class ServiceMapping implements Serializable, Comparable {

  public static final int NO_PROVIDER = 0xFFFFFF;
  public int serviceId;
  private long customData;

  public ServiceMapping(int serviceId) {
    this.serviceId = serviceId;
    this.customData = -1;
  }

  public ServiceMapping(int serviceId, long customData) {
    this.serviceId = serviceId;
    this.customData = customData & 0xFFFFFFFFFFL;
  }

  public ServiceMapping(CamdNetMessage msg) {
    this.serviceId = msg.getServiceId();
    setCustomId(msg.getCustomId());
    setProviderIdent(NO_PROVIDER);
    if(msg.getProfileName() != null) {
      CaProfile profile = ProxyConfig.getInstance().getProfile(msg.getProfileName());
      if(profile != null && profile.isRequireProviderMatch()) setProviderIdent(msg.getProviderIdent());
    }
  }

  public ServiceMapping(TvService service) {
    this.serviceId = service.getId();
    this.customData = service.getCustomData();
  }

  public boolean equals(Object o) {
    if(this == o) return true;
    if(o == null || getClass() != o.getClass()) return false;
    ServiceMapping that = (ServiceMapping)o;
    if(customData != that.customData) return false;
    if(serviceId != that.serviceId) return false;
    return true;
  }

  public int hashCode() {
    int result = serviceId;
    result = 31 * result + (int)(customData ^ (customData >>> 32));
    return result;
  }

  public String toString() {
    return DESUtil.intToHexString(serviceId, 4) + (getCustomId() != 0?":" + DESUtil.intToHexString(getCustomId(), 4):"") +
        (getProviderIdent() != NO_PROVIDER?":" + DESUtil.intToHexString(getProviderIdent(), 6):"");
  }

  public int compareTo(Object o) {
    int os = ((ServiceMapping)o).serviceId;
    return (serviceId<os ? -1 : (serviceId==os ? 0 : 1));
  }

  public int getCustomId() {
    return (int)(customData & 0xFFFF);
  }

  public void setCustomId(int customId) {
    this.customData &= 0xFFFFFF0000L;
    this.customData |= (customId & 0xFFFF);
  }

  public int getProviderIdent() {
    return (int)(customData >> 16 & 0xFFFFFF);
  }

  public void setProviderIdent(int ident) {
    this.customData &= 0x000000FFFFL;
    this.customData |= (((long)ident & 0xFFFFFF) << 16);
  }

  public long getCustomData() {
    return customData;
  }
}
