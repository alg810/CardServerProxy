package com.bowman.cardserv.session;

import java.io.Serializable;

/**
 * Created by IntelliJ IDEA.
* User: bowman
* Date: Feb 21, 2008
* Time: 3:48:15 PM
*/
public class SeenEntry implements Serializable, Comparable {

  private static final long serialVersionUID = 8576134124062762697L;

  final String name, profile;
  
  private String hostAddr;
  private long lastLogin = -1, lastLogout = -1, lastSeen = -1;
  private int count;
  private String lastReason;

  public SeenEntry(String name, String profile) {
    this.name = name;
    this.profile = profile;
  }

  public String getName() {
    return name;
  }

  public String getProfile() {
    return profile;
  }

  public long getLastLogin() {
    return lastLogin;
  }

  public long getLastSeen() {
    return lastSeen;
  }

  public String getHostAddr() {
    return hostAddr;
  }

  public void setHostAddr(String hostAddr) {
    this.hostAddr = hostAddr;
  }

  public void setLastLogin(long lastLogin) {
    this.lastLogin = lastLogin;
    this.lastSeen = lastLogin;
  }

  public void setLastLogout(long lastLogout) {
    this.lastLogout = lastLogout;
    this.lastSeen = lastLogout;
  }

  public void setLastSeen(long lastSeen) {
    this.lastSeen = lastSeen;
  }

  public long getLastLogout() {
    if(lastLogout == -1) return lastSeen;
    else return lastLogout;
  }

  public int getCount() {
    return count;
  }

  public void incCount() {
    count++;
  }

  public String getLastReason() {
    return lastReason;
  }

  public void setLastReason(String lastReason) {
    this.lastReason = lastReason;
  }

  public int compareTo(Object o) {
    SeenEntry se = (SeenEntry)o;
    return new Long(lastSeen).compareTo(new Long(se.lastSeen));
  }


}
