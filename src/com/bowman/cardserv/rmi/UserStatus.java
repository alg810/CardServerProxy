package com.bowman.cardserv.rmi;

import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.interfaces.ProxySession;

import java.io.Serializable;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Nov 3, 2005
 * Time: 1:29:20 PM
 */
public class UserStatus extends AbstractStatus implements Serializable {

  private static final long serialVersionUID = -4091190186183181716L;

  private final String userName, displayName, startDate, expirationDate;
  private final boolean admin;
  private final int maxSessions;

  private final List sessions = new ArrayList();

  public UserStatus(String userName, String displayName, String startDate, String expirationDate, int maxSessions, boolean admin) {
    this.userName = userName;
    this.displayName = displayName;
    this.admin = admin;
    this.startDate = startDate;
    this.expirationDate = expirationDate;
    this.maxSessions = maxSessions;
  }

  public String getUserName() {
    return userName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isAdmin() {
    return admin;
  }

  public String getStartDate() {
    return startDate;
  }

  public String getExpirationDate() {
    return expirationDate;
  }

  public int getSessionCount(String profileName) {
    if(profileName == null) return sessions.size();
    int count = 0;
    for(Iterator iter = sessions.iterator(); iter.hasNext(); )
      if(profileName.equals(((SessionStatus)iter.next()).getProfileName())) count++;
    return count;
  }

  public int getMaxSessions() {
    return maxSessions;
  }

  void addSession(ProxySession session) {
    sessions.add(new SessionStatus(session));
  }

  public TvService[] getServices() {
    if(sessions == null || sessions.isEmpty()) return new TvService[0];
    else {
      TvService[] services = new TvService[sessions.size()];
      for(int i = 0; i < services.length; i++)
        services[i] = ((SessionStatus)sessions.get(i)).getCurrentService();
      return services;
    }
  }

  public String[] getRemoteHosts() {
    if(sessions == null || sessions.isEmpty()) return new String[0];
    else {
      String[] hosts = new String[sessions.size()];
      for(int i = 0; i < hosts.length; i++)
        hosts[i] = ((SessionStatus)sessions.get(i)).getRemoteHost();
      return hosts;
    }
  }

  public SessionStatus[] getSessions() {
    if(sessions == null || sessions.isEmpty()) return new SessionStatus[0];
    else return (SessionStatus[])sessions.toArray(new SessionStatus[sessions.size()]);
  }

}
