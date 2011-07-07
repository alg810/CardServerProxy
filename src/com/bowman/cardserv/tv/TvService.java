package com.bowman.cardserv.tv;

import java.io.Serializable;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 16, 2005
 * Time: 6:51:40 AM
 */
public class TvService implements Serializable, Comparable {

  public static final int TYPE_TV = 1, TYPE_RADIO = 2, TYPE_HDTV_MPEG2 = 17, TYPE_HDTV_MPEG4 = 25, TYPE_TV_SUB = 211;

  private final boolean unknown;
  private final int id, number, type;
  private int watchers;
  private final long namespace, transponder;
  private long customData, networkId;
  private final String profileName;
  private String name, provider;

  private final List conflicting = new ArrayList();

  public TvService(int unknownSid, String profile) {
    this.id = unknownSid;
    this.namespace = -1;
    this.transponder = -1;
    this.networkId = -1;
    this.type = TYPE_TV;
    this.number = 100;
    this.unknown = true;
    this.profileName = profile;
    this.provider = "?";
    this.name = "Unknown (" + Integer.toHexString(unknownSid) + ")";
  }

  public TvService(TvService ts, long customData) {
    this(ts, customData, null);
  }

  public TvService(TvService ts, long customData, String profile) {
    this.id = ts.id;
    this.namespace = ts.namespace;
    this.transponder = ts.transponder;
    this.networkId = ts.networkId;
    this.type = ts.type;
    this.number = ts.number;
    this.unknown = ts.unknown;
    this.profileName = profile==null?ts.profileName:profile;
    this.provider = ts.provider;
    this.name = ts.name;
    this.customData = customData;
  }

  public TvService(String[] tokens, String profile) {
    String sid = tokens[0].startsWith("0x")?tokens[0].substring(2):tokens[0];
    this.id = Integer.parseInt(sid, 16);
    this.namespace = Long.parseLong(tokens[1], 16); // ?
    this.transponder = Long.parseLong(tokens[2], 16); // ?
    this.networkId = Long.parseLong(tokens[3], 16); // ?
    this.type = Integer.parseInt(tokens[4]);
    this.number = Integer.parseInt(tokens[5]);
    this.unknown = false;
    this.profileName = profile;
  }

  public TvService(int[] tokens, String profile) {
    this.id = tokens[0];
    this.namespace = tokens[1];
    this.transponder = tokens[2];
    this.networkId = tokens[3];
    this.type = tokens[4];
    this.number = tokens[5];
    this.unknown = false;
    this.profileName = profile;
  }

  public int getId() {
    return id;
  }

  public long getNamespace() {
    return namespace;
  }

  public int getNumber() {
    return number;
  }

  public long getTransponder() {
    return transponder;
  }

  public long getNetworkId() {
    return networkId;
  }

  public long getCustomData() {
    return customData;
  }

  public void setCustomData(long customData) {
    this.customData = customData;
  }

  public int getType() {
    return type;
  }

  public boolean isTv() {
    switch(type) {
      case TYPE_TV:
      case TYPE_TV_SUB:
      case TYPE_RADIO:
      case TYPE_HDTV_MPEG2:
      case TYPE_HDTV_MPEG4:
        return true;
      default:
        return false;
    }
  }

  public String getName() {
    if(conflicting.isEmpty()) return name;
    else return name + "?(" + conflicting.size() +")?";
  }

  public String getDisplayName() {
    switch(type) {
      case TYPE_TV_SUB:
        return "[SUB]" + name;
      case TYPE_RADIO:
        return "[R]" + name;
      case TYPE_HDTV_MPEG2:
      case TYPE_HDTV_MPEG4:
        return "[HD]" + name;
      default:
        return name;
    } 
  }

  public void setName(String name) {
    this.name = name.trim();
  }

  public void setNetworkId(int networkId) {
    this.networkId = networkId;
  }

  public String getProvider() {
    return provider;
  }

  public String getProfileName() {
    return profileName;
  }

  public boolean isUnknown() {
    return unknown;
  }

  public String toEnigmaString() {
    StringBuffer sb = new StringBuffer(name);
    sb.append(' ').append(Integer.toHexString(id));
    sb.append(':').append(Long.toHexString(namespace));
    sb.append(':').append(Long.toHexString(transponder));
    sb.append(':').append(Long.toHexString(networkId));
    sb.append(':').append(type);
    sb.append(':').append(number);
    sb.append(" p: ").append(provider);
    return sb.toString();
  }

  public String toString() {
    return name + " " + profileName + ":" + Integer.toHexString(id);
  }

  public void setProviderStr(String prov) {
    String[] tokens = prov.split(",");
    for(int i = 0; i < tokens.length; i++) {
      if(tokens[i].startsWith("p:")) provider = tokens[i].substring(2).trim();
    }
  }

  public void addConflicting(TvService service) {
    conflicting.add(service);
  }

  public int getWatchers() {
    return watchers;
  }

  public void setWatchers(int watchers) {
    this.watchers = watchers;
  }

  public int compareTo(Object o) {
    return toString().compareTo(o.toString());
  }

  public boolean equals(Object o) {
    if(this == o) return true;
    if(o == null || getClass() != o.getClass()) return false;
    final TvService tvService = (TvService)o;
    return id == tvService.id && profileName.equals(tvService.profileName);
  }

  public int hashCode() {
    int result;
    result = id;
    result = 29 * result + profileName.hashCode();
    return result;
  }

  public boolean isPPVSlot() {
    return name.startsWith("CH") && name.length() == 4;
  }

  public static TvService getUnknownService(String profileName, int unknownSid) {
    return new TvService(unknownSid, profileName);
  }

}
