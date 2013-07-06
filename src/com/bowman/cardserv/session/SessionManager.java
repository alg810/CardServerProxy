package com.bowman.cardserv.session;

import com.bowman.cardserv.*;
import com.bowman.cardserv.cws.ServiceMapping;
import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.ProxyLogger;
import com.bowman.util.*;

import java.io.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2007-mar-04
 * Time: 15:24:28
 */
public class SessionManager implements CronTimerListener {

  private static SessionManager instance = null;
  public static SessionManager getInstance() {
    if(instance == null) instance = new SessionManager();
    return instance;
  }

  private Map sessions = new TreeMap();
  private Map sessionsByUser = new HashMap();
  private Map sessionsByProfile = new HashMap();
  private Map sessionsByIp = new HashMap();
  private Map users = new HashMap();
  private int counter = 1;
  private ProxyLogger logger;
  private ProxyConfig config;

  private Map seenMap = new HashMap();
  private Map failMap = new HashMap();
  private File seenFile;

  private List userStatusListeners = new ArrayList();
  private Map userStatusMap = new HashMap();

  private long changeTimeStamp;

  private SessionManager() {
    logger = ProxyLogger.getLabeledLogger(getClass().getName());
    config = ProxyConfig.getInstance();
    seenFile = new File("etc", "seen.dat");
    if(seenFile.exists()) {
      try {
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(seenFile)));
        seenMap = (Map)ois.readObject();
        try {
          failMap = (Map)ois.readObject();
        } catch (Exception e) {
          logger.throwing(e);
          failMap = new HashMap();
        }
        logger.fine("Loaded seen-data, " + (seenMap.size() + failMap.size()) + " entries.");
        ois.close();
      } catch (Exception e) {
        logger.throwing(e);
        logger.warning("Failed to load seen-data ('" + seenFile.getPath() + "'): " + e);
      }
    }
    CronTimer seenTimer = new CronTimer("* * * * *");
    seenTimer.addTimerListener(this);
    seenTimer.start();
  }

  public int getNewSessionId() {
    return counter++;
  }

  public synchronized void addSession(ProxySession session) {
    if(session != null) {
      sessions.put(String.valueOf(session.getId()), session);
      addSessionLookup(session, session.getProfileName(), sessionsByProfile);
      addSessionLookup(session, session.getRemoteAddress(), sessionsByIp);      
      if(session.getUser() != null) {
        if(!session.isTempUser()) seenUserLogin(session.getUser(), session.getProfileName(), session.getRemoteAddress());
        addSessionLookup(session, session.getUser(), sessionsByUser);
        fireUserLogin(session.getUser(), session.getProfileName(), session.getRemoteAddress(), getSessionIdStr(session));
      }
    }
  }

  private void addSessionLookup(ProxySession session, String key, Map map) {
    List sessions = (List)map.get(key);
    if(sessions == null) {
      sessions = new ArrayList();
      map.put(key, sessions);
    }
    sessions.add(session);
  }

  public synchronized void removeSession(ProxySession session) {
    if(session != null) {
      userStatusMap.remove(session.toString());
      removeSessionLookup(session, session.getProfileName(), sessionsByProfile);
      removeSessionLookup(session, session.getRemoteAddress(), sessionsByIp);
      if(sessions.remove(String.valueOf(session.getId())) != null)
        if(session.getUser() != null) {
          if(!session.isTempUser()) seenUserLogout(session.getUser(), session.getProfileName());
          removeSessionLookup(session, session.getUser(), sessionsByUser);
          fireUserLogout(session.getUser(), session.getProfileName(), getSessionIdStr(session));
        }
    }
  }

  private void removeSessionLookup(ProxySession session, String key, Map map) {
    List sessions = (List)map.get(key);
    if(sessions != null) {
      sessions.remove(session);
      if(sessions.isEmpty()) map.remove(key);
    } else {
      logger.fine("No sessions for ip: " + key + " (" + session + " " + session.getUser() + ")");
      logger.throwing(new Throwable());
    }
  }

  public synchronized int countSessionsIP(String ip, String profileName) {
    int count = 0;
    List ipSessions = (List)sessionsByIp.get(ip);
    if(ipSessions != null) {
      if(profileName == null) return ipSessions.size();
      for(Iterator iter = ipSessions.iterator(); iter.hasNext(); )
        if(profileName.equals(((ProxySession)iter.next()).getProfileName())) count++;
    }
    return count;
  }

  public synchronized List getSessionsIP(String ip) {
    return (List)sessionsByIp.get(ip);
  }

  int countSessions(String user, String profileName) {
    int count = 0;
    List userSessions = (List)sessionsByUser.get(user);
    if(userSessions != null) {
      if(profileName == null) return userSessions.size();
      for(Iterator iter = userSessions.iterator(); iter.hasNext(); )
        if(profileName.equals(((ProxySession)iter.next()).getProfileName())) count++;
    }
    return count;
  }

  synchronized int syncCountSessions(String user, String profileName) {
    return countSessions(user, profileName);
  }

  synchronized ProxySession hasSession(String user, String className) {
    List userSessions = (List)sessionsByUser.get(user);
    ProxySession session;
    if(userSessions != null) {
      for(Iterator iter = userSessions.iterator(); iter.hasNext(); ) {
        session = (ProxySession)iter.next();
        if(className.equals(session.getClass().getName())) return session;
      }
    }
    return null;
  }

  synchronized long closeOldestSession(String key, boolean ip, String profileName) {
    List existingSessions = (List)(ip?sessionsByIp.get(key):sessionsByUser.get(key));
    ProxySession session;
    if(existingSessions == null) return -1;
    else {
      existingSessions = new ArrayList(existingSessions);
      if("*".equals(profileName)) profileName = null;
      if(profileName != null) { // exclude sessions from other profiles if one is specified
        for(Iterator iter = existingSessions.iterator(); iter.hasNext(); ) {
          session = (ProxySession)iter.next();
          if(!session.getProfileName().equals(profileName)) iter.remove();
        }
        if(existingSessions.isEmpty()) return -1;
      }
      if(existingSessions.size() > 1) {
        Collections.sort(existingSessions, new Comparator() {
          public int compare(Object a, Object b) {
            ProxySession pa = (ProxySession)a; ProxySession pb = (ProxySession)b;
            return new Long(pb.getIdleTime()).compareTo(new Long(pa.getIdleTime()));
          }
        });
      }
      session = (ProxySession)existingSessions.get(0);
      long idleTime = session.getIdleTime();
      session.close();
      return idleTime;
    }
  }

  synchronized boolean checkSessionIP(String user, String ip) {
    if("0.0.0.0".equals(ip)) return false;
    ProxySession session;
    List sessions = (List)sessionsByUser.get(user);
    if(sessions == null || sessions.isEmpty()) return false;
    for(Iterator iter = new ArrayList(sessions).iterator(); iter.hasNext(); ) {
      session = (ProxySession)iter.next();
      if(session != null) {
        if(!ip.equals(session.getRemoteAddress())) {
          if(!"0.0.0.0".equals(session.getRemoteAddress())) {
            if(session.isActive()) return true;
            else session.close();
          } else session.close();
        }
      }
    }
    return false;
  }

  public synchronized int getSessionCount() {
    return sessions.size();
  }

  public synchronized int getSessionCount(String profileName) {
    if(profileName == null) return getSessionCount();
    else {
      List sessions = (List)sessionsByProfile.get(profileName);
      if(sessions != null) return sessions.size();
      else return 0;
    }
  }

  public List getActiveSessions() {
    return getSessions(null, Boolean.TRUE);
  }

  public List getActiveSessions(String profileName) {
    return getSessions(profileName, Boolean.TRUE);
  }

  public List getInactiveSessions() {
    return getSessions(null, Boolean.FALSE);
  }

  public List getInactiveSessions(String profileName) {
    return getSessions(profileName, Boolean.FALSE);
  }

  public synchronized List getSessionsForUser(String user) {
    if(sessionsByUser.containsKey(user)) return new ArrayList((List)sessionsByUser.get(user));
    else return null;
  }

  public synchronized List getSessions() {
    return new ArrayList(sessions.values());
  }

  public List getSessions(String profileName) {
    return getSessions(profileName, null); // both active and inactive
  }

  synchronized List getSessions(String profileName, Boolean active) {
    ProxySession session;
    List activeSessions = new ArrayList();
    List sessions;
    if(profileName == null) sessions = getSessions();
    else {
      sessions = (List)sessionsByProfile.get(profileName);
      if(sessions == null || sessions.isEmpty()) return activeSessions;
      else sessions = new ArrayList(sessions);
    }

    if(active == null) return sessions; // all sessions for selected profile

    for(Iterator iter = sessions.iterator(); iter.hasNext(); ) {
      session = (ProxySession)iter.next();
      if(session != null) {
        if(active.booleanValue() != session.isActive()) continue;
        activeSessions.add(session);
      }
    }
    return activeSessions;
  }

  public synchronized ProxySession getSession(String id) {
    return (ProxySession)sessions.get(id);
  }

  public synchronized List getSeenData(String profileName) {
    List entries = new ArrayList(); SeenEntry se;
    for(Iterator iter = new ArrayList(seenMap.values()).iterator(); iter.hasNext();) {
      se = (SeenEntry)iter.next();
      if(users.containsKey(getKey(se.name, se.profile))) continue; // skip all currently logged in
      if(profileName == null) entries.add(se);
      else if(profileName.equals(se.profile)) entries.add(se);
    }
    return entries;
  }

  public synchronized List getFailData() {
    return new ArrayList(failMap.values());
  }

  void seenUserLogin(String name, String profile, String hostAddr) {
    SeenEntry se = getSeenEntry(name, profile);
    se.setLastLogin(System.currentTimeMillis());
    se.setHostAddr(hostAddr);

    incrementUsersMap(name, profile);
  }

  void seenUserFail(String name, String profile, String hostAddr, String reason) {
    SeenEntry se = getFailEntry(name, profile);
    se.setLastLogin(System.currentTimeMillis());
    se.setHostAddr(hostAddr);
    se.setLastReason(reason);
    se.incCount();
  }

  void incrementUsersMap(String name, String profile) {
    String key = getKey(name, profile);
    Integer count = (Integer)users.get(key);
    if(count == null) {
      count = new Integer(1);
    } else {
      count = new Integer(count.intValue() + 1);
    }
    users.put(key, count);
  }

  void seenUserLogout(String name, String profile) {
    getSeenEntry(name, profile).setLastLogout(System.currentTimeMillis());

    decrementUsersMap(name, profile);
  }

  void decrementUsersMap(String name, String profile) {
    String key = getKey(name, profile);
    Integer count = (Integer)users.get(key);
    if(count != null) {
      count = new Integer(count.intValue() - 1);
      if(count.intValue() < 1) users.remove(key);
      else users.put(key, count);
    }
  }

  synchronized SeenEntry getSeenEntry(String name, String profile) {
    String key = getKey(name, profile);
    SeenEntry se = (SeenEntry)seenMap.get(key);
    if(se == null) {
      se = new SeenEntry(name, profile);
      seenMap.put(key, se);
    }
    changeTimeStamp = System.currentTimeMillis();
    return se;
  }

  synchronized SeenEntry getFailEntry(String name, String profile) {
    String key = getKey(name, profile);
    SeenEntry se = (SeenEntry)failMap.get(key);
    if(se == null) {
      se = new SeenEntry(name, profile);
      se.setLastLogout(System.currentTimeMillis()); // use this as timestamp for "first failed login"
      failMap.put(key, se);
    }
    changeTimeStamp = System.currentTimeMillis();
    return se;
  }

  public synchronized int removeSeenUser(String name) {
    int removed = 0;
    if(name == null) {
      removed = seenMap.size();
      seenMap.clear();
    } else {
      String key;
      for(Iterator iter = seenMap.keySet().iterator(); iter.hasNext(); ) {
        key = (String)iter.next();
        if(name.equalsIgnoreCase(((SeenEntry)seenMap.get(key)).getName())) {
          iter.remove();
          removed++;
        }
      }
    }
    if(removed > 0) changeTimeStamp = System.currentTimeMillis();
    return removed;
  }


  public synchronized int removeLoginFailure(String mask) {
    int removed = 0;
    if(mask == null || "*".equals(mask)) {
      removed = failMap.size();
      failMap.clear();
    } else {
      String key, name;
      for(Iterator iter = failMap.keySet().iterator(); iter.hasNext(); ) {
        key = (String)iter.next();
        name = ((SeenEntry)failMap.get(key)).getName();
        if(Globber.match(mask, name, false)) {
          iter.remove();
          removed++;
        }
      }
    }
    if(removed > 0) changeTimeStamp = System.currentTimeMillis();
    return removed;
  }

  private static String getKey(String name, String profile) {
    return name + ":" + profile;
  }

  protected static String getKey(ProxySession session) {
    return getKey(session.getUser(), session.getProfileName());
  }

  private static String getKey(SeenEntry se) {
    return getKey(se.name, se.profile);
  }

  public void timeout(CronTimer cronTimer) {
    saveSeenData();
    checkKeepAlives();
  }

  public synchronized void saveSeenData() {
    if(!seenFile.exists() || (changeTimeStamp > seenFile.lastModified())) {
      SeenEntry se; long now = System.currentTimeMillis();
      for(Iterator iter = seenMap.values().iterator(); iter.hasNext(); ) {
        se = (SeenEntry)iter.next();
        if(users.containsKey(getKey(se))) se.setLastSeen(now);
      }
      try {
        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(seenFile)));
        oos.writeObject(seenMap);
        oos.writeObject(failMap);
        logger.fine("Saved seen-data, " + (seenMap.size() + failMap.size()) + " entries.");
        oos.close();
      } catch (IOException e) {
        logger.throwing(e);
        logger.warning("Failed to save seen-data ('" + seenFile.getPath() + "'): " + e);
      }
    }
  }

  public void checkKeepAlives() {
    long keepAlive = config.getSessionKeepAlive();
    if(keepAlive == 0) return;
    logger.fine("Checking " + sessions.size() + " sessions for keep-alives...");
    Set clients = config.getKeepAliveExcludedClients();
    ProxySession session; long idleTime;
    for(Iterator iter = getSessions().iterator(); iter.hasNext(); ) {
      session = (ProxySession)iter.next();
      if(session != null && session.getIdleTime() > config.getSessionKeepAlive()) {
        if(!clients.contains(session.getClientId().toLowerCase())) try {
          idleTime = session.getIdleTime();
          session.sendMessage(new CamdNetMessage(CamdConstants.MSG_KEEPALIVE));
          logger.fine("Keep-alive sent to " + session + " (was idle " + idleTime + " ms)");
        } catch (Exception e) {
          logger.warning("Failed to send keep-alive to " + session + " (" + e + ")");
          logger.throwing(e);
        }
      }
    }
  }

  public void updateUserStatus(ProxySession session, CamdNetMessage msg, boolean debug) {
    if(!session.isConnected()) return;
    ServiceMapping id = new ServiceMapping(msg);

    Integer chanId = new Integer(id.serviceId);
    if(!chanId.equals(userStatusMap.get(session.toString()))) {
      if(userStatusMap.put(session.toString(), chanId) == null) session.setFlag(msg, '1'); // first msg for session
      else session.setFlag(msg, 'Z'); // zap
      
      if(config.isLogZap() || debug) logger.info("User '" + session.getUser() + "' (" +
          com.bowman.cardserv.util.CustomFormatter.formatAddress(session.getRemoteAddress()) + ") now watching channel [" +
          config.getServiceName(msg) + "]");

      TvService service = config.getService(msg);
      fireUserStatusChanged(session.getUser(), service, session.getProfileName(), getSessionIdStr(session));
    }
  }

  public void addUserStatusListener(UserStatusListener listener) {
    if(!userStatusListeners.contains(listener)) userStatusListeners.add(listener);
  }

  public void removeUserStatusListener(UserStatusListener listener) {
    userStatusListeners.remove(listener);
  }

  private void fireUserStatusChanged(String user, TvService service, String profile, String id) {
    for(Iterator iter = userStatusListeners.iterator(); iter.hasNext(); ) {
      ((UserStatusListener)iter.next()).userStatusChanged(user, service, profile, id);
    }
  }

  private void fireUserLogin(String user, String profile, String ip, String id) {
    for(Iterator iter = userStatusListeners.iterator(); iter.hasNext(); ) {
      ((UserStatusListener)iter.next()).userLogin(user, profile, ip, id);
    }
  }

  private void fireUserLogout(String user, String profile, String id) {
    for(Iterator iter = userStatusListeners.iterator(); iter.hasNext(); ) {
      ((UserStatusListener)iter.next()).userLogout(user, profile, id);
    }
  }

  public void fireUserLoginFailed(String user, String profile, String ip, String reason) {
    seenUserFail(user, profile, ip, reason);
    for(Iterator iter = userStatusListeners.iterator(); iter.hasNext(); ) {
      ((UserStatusListener)iter.next()).userLoginFailed(user, profile, ip, reason);
    }
  }

  public static String getSessionIdStr(ProxySession session) {
    return session.getProtocol() + "[" + session.getId() + "]";
  }

}
