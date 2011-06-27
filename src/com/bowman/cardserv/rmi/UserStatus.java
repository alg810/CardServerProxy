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

  private final String userName, displayName;
  private final boolean admin;
  private final int maxSessions;
  private final String expire, start;

  private final List sessions = new ArrayList();

   public UserStatus(String userName, String displayName, String start, String expire, int maxSessions, boolean admin) {
      this.userName = userName;
      this.displayName = displayName;
      this.admin = admin;
      this.start = start;
      this.expire = expire;
      this.maxSessions = maxSessions;
    }

  public String getUserName() {
    return userName;
  }

  public String getDisplayName() {
    return displayName;
  }
    public String getExpireDate()
    {
        return expire;
    }

  public boolean isAdmin() {
    return admin;
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
    public String getStartDate() {
      return start;
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
