package com.bowman.cardserv.rmi;

import com.bowman.cardserv.util.ProxyLogger;
import com.bowman.cardserv.*;
import com.bowman.cardserv.cws.ServiceMapping;
import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.session.*;
import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.interfaces.*;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.util.*;
import java.util.logging.Level;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 23, 2005
 * Time: 10:54:23 PM
 */
public class RemoteHandler extends UnicastRemoteObject implements RemoteProxy, UserStatusListener, CwsListener,
    EcmTransactionListener, LogListener, Runnable
{
  private static final long EVENT_START_DELAY = 15 * 1000;

  private Map listeners = new HashMap();

  private CardServProxy proxy;
  private ProxyConfig config;
  private ProxyLogger logger;
  private SessionManager sm;

  private List eventQueue = Collections.synchronizedList(new ArrayList());
  private Thread eventThread;
  private String name;

  public RemoteHandler(int port, CardServProxy proxy) throws RemoteException {
    super(port);
    this.proxy = proxy;
    this.config = ProxyConfig.getInstance();
    this.logger = ProxyLogger.getLabeledLogger(getClass().getName());
    this.sm = SessionManager.getInstance();
    ProxyLogger.setLogListener(this);
  }

  public void start() {
    eventThread = new Thread(this, "RemoteEventDispatcherThread");
    eventThread.start();

    sm.addUserStatusListener(this);
    config.getConnManager().addCwsListener(this);

    fireRemoteEvent(new RemoteEvent(RemoteEvent.PROXY_STARTUP, name, "Proxy node started.", null));
  }

  private synchronized void fireRemoteEvent(RemoteEvent event) {
    if(eventThread == null) return; // rmi has been disabled
    if(event.getType() == RemoteEvent.CWS_CONNECTED &&
        System.currentTimeMillis() - proxy.getStartTime() < EVENT_START_DELAY) return; // filter events on startup
    eventQueue.add(event);
    notify();
  }

  public void addRemoteListener(RemoteListener listener) {
    addRemoteListener(listener, null);
  }

  public void addRemoteListener(RemoteListener listener, String profileName) {
    listeners.put(listener, profileName);
    logger.fine("RemoteListener added: " + listener + " profile: " + profileName);
  }

  public void removeRemoteListener(RemoteListener listener) {
    if(listeners.remove(listener) != null) logger.fine("RemoteListener removed: " + listener);
  }

  private Map getConnectors(String[] profiles) {
    if(profiles == null) return new HashMap(config.getConnManager().getConnectors());
    else {
      Map connectors = new HashMap();
      for(int i = 0; i < profiles.length; i++) {
        if(profileExists(profiles[i]))
          connectors.putAll(config.getConnManager().getConnectors(profiles[i]));
      }
      return connectors;
    }
  }

  private List getSessions(String[] profiles, boolean activeOnly) {
    if(profiles == null) return activeOnly?sm.getActiveSessions():sm.getSessions();
    else {
      List sessions = new ArrayList();
      for(int i = 0; i < profiles.length; i++) {
        if(profileExists(profiles[i]))
          sessions.addAll(activeOnly?sm.getActiveSessions(profiles[i]):sm.getSessions(profiles[i]));
      }
      return sessions;
    }
  }

  private List getSeenData(String[] profiles, boolean failures) {
    if(failures) return sm.getFailData(); // not per profile
    if(profiles == null) return sm.getSeenData(null);
    else {
      List entries = new ArrayList();
      for(int i = 0; i < profiles.length; i++) {
        if(profileExists(profiles[i]))
          entries.addAll(sm.getSeenData(profiles[i]));
      }
      return entries;
    }
  }

  public int getCwsCount(String[] profiles) {
    return getConnectors(profiles).size();
  }

  public int getSessionCount(String[] profiles, boolean activeOnly) {
    if(profiles == null) return activeOnly?sm.getActiveSessions().size():sm.getSessionCount();
    else {
      int count = 0;
      if(activeOnly) return getSessions(profiles, true).size();
      else for(int i = 0; i < profiles.length; i++) {
        if(profileExists(profiles[i]))
          count += sm.getSessionCount(profiles[i]);
      }
      return count;
    }
  }

  public int getCwsCapacity(String[] profiles) throws RemoteException {
    Map connectors = getConnectors(profiles);
    int total = 0;
    CwsConnector cws;
    for(Iterator iter = connectors.values().iterator(); iter.hasNext(); ) {
      cws = (CwsConnector)iter.next();
      if(cws.getCapacity() != -1) total += cws.getCapacity();
    }
    return total;
  }

  public long getProxyStartTime() {
    return proxy.getStartTime();
  }

  public CwsStatus getCwsStatus(String name) {
    CwsConnector cws = config.getConnManager().getCwsConnectorByName(name);
    if(cws == null) return null;
    else return new CwsStatus(cws);
  }

  public CwsStatus[] getMultiCwsStatus(String[] profiles) {
    Map connectors = getConnectors(profiles);
    CwsStatus[] result = new CwsStatus[connectors.size()];
    CwsConnector cws; int i = 0;
    for(Iterator iter = connectors.values().iterator(); iter.hasNext(); ) {
      cws = (CwsConnector)iter.next();
      result[i++] = new CwsStatus(cws);
    }
    return result;
  }

  public UserStatus[] getUsersStatus(String[] profiles, boolean activeOnly) {
    UserManager um = config.getUserManager();
    List sessions = getSessions(profiles, activeOnly);
    Map users = new TreeMap();
    ProxySession session; UserStatus user;
    for(Iterator iter = sessions.iterator(); iter.hasNext(); ) {
      session = (ProxySession)iter.next();
      String name = session.getUser();
      if(users.containsKey(name)) {
        user = (UserStatus)users.get(name);
        user.addSession(session);
      } else {
        String displayName = session.isTempUser()?session.getLoginName():um.getDisplayName(name);
          user = new UserStatus(name, displayName,um.getStartDate(name),um.getExpireDate(name), um.getMaxConnections(name), false);
          user.addSession(session);
          users.put(name, user);
      }
    }
    return (UserStatus[])users.values().toArray(new UserStatus[users.size()]);
  }

  public SeenEntry[] getSeenUsers(String[] profiles, String userName, boolean failures) throws RemoteException {
    List entries = getSeenData(profiles, failures);
    if(userName != null) {
      SeenEntry se;
      for(Iterator iter = entries.iterator(); iter.hasNext(); ) {
        se = (SeenEntry)iter.next();
        if(!se.getName().equalsIgnoreCase(userName)) iter.remove();
      }
    }
    Collections.sort(entries);
    return (SeenEntry[])entries.toArray(new SeenEntry[entries.size()]);
  }

  public UserStatus getUserStatus(String userName, boolean activeOnly) {
    UserManager um = config.getUserManager();
    List sessions = getSessions(null, activeOnly);
    ProxySession session; UserStatus user = null;
    for(Iterator iter = sessions.iterator(); iter.hasNext(); ) {
      session = (ProxySession)iter.next();
      if(userName.equalsIgnoreCase(session.getUser())) {
        if(user == null) {
          String displayName = session.isTempUser()?session.getLoginName():um.getDisplayName(userName);
            user = new UserStatus(userName, displayName,um.getStartDate(userName),um.getExpireDate(userName), um.getMaxConnections(userName), um.isAdmin(userName));
          }
          user.addSession(session);
        }
      }
      return user;
    }

  private boolean profileExists(String profileName) {
    return config.getProfile(profileName) != null;
  }

  public String getUserPasswd(String userName) {
    return config.getUserManager().getPassword(userName);
  }

  public ProfileStatus[] getUserProfiles(String userName) throws RemoteException {
    Set profiles = config.getUserManager().getAllowedProfiles(userName);
    if(profiles.isEmpty()) return getProfiles();
    else {
      List ps = new ArrayList();
      ps.add(new ProfileStatus(CaProfile.MULTIPLE));
      CaProfile profile;
      for(Iterator iter = profiles.iterator(); iter.hasNext(); ) {
        profile = config.getProfile((String)iter.next());
        if(profile != null) ps.add(new ProfileStatus(profile));
      }
      return (ProfileStatus[])ps.toArray(new ProfileStatus[ps.size()]);
    }
  }

  public boolean authenticateUser(String userName, String pass) {
    return config.getUserManager() != null && config.getUserManager().authenticate(userName, pass);
  }

  public boolean isAdmin(String userName) throws RemoteException {
    return config.getUserManager().isAdmin(userName);
  }

  public String getEmailAddress(String userName) {
    return config.getUserManager().getEmailAddress(userName);
  }

  public String[] getLocalUsers() {
    return getLocalUsers(null);
  }

  public String[] getLocalUsers(String profileName) {
    String[] names = config.getUserManager().getUserNames();
    if(profileName != null) {
      List profileUsers = new ArrayList();
      Set profiles;
      for(int i = 0; i < names.length; i++) {
        profiles = config.getUserManager().getAllowedProfiles(names[i]);
        if(profiles == null || profiles.isEmpty()) profileUsers.add(names[i]);
        else if(profiles.contains(profileName)) profileUsers.add(names[i]);
      }
      return (String[])profileUsers.toArray(new String[profileUsers.size()]);
    } else return names;
  }

  public TvService[] getServices(String name, boolean merge) {
    List services = config.getConnManager().getServicesForConnector(name, true, false);
    if(services == null) return null;
    if(!merge) return (TvService[])services.toArray(new TvService[services.size()]);
    else {
      Map map = new HashMap(); TvService service;
      for(Iterator iter = services.iterator(); iter.hasNext(); ) {
        service = (TvService)iter.next();
        map.put(new Integer(service.getId()), service);
      }
      return (TvService[])map.values().toArray(new TvService[map.size()]);
    }
  }

  public TvService[] getCannotDecodeServices(String name) {
    List services = config.getConnManager().getServicesForConnector(name, false, false);
    if(services == null) return null;
    else return (TvService[])services.toArray(new TvService[services.size()]);
  }

  public synchronized TvService[] getWatchedServices(String[] profiles) throws RemoteException {
    List activeSessions = getSessions(profiles, true);
    Set p = profiles==null?Collections.EMPTY_SET:new HashSet(Arrays.asList(profiles));
    if(!p.contains(CaProfile.MULTIPLE.getName())) activeSessions.addAll(sm.getActiveSessions(CaProfile.MULTIPLE.getName()));
    Map services = new TreeMap();
    ProxySession session; TvService service;
    for(Iterator iter = activeSessions.iterator(); iter.hasNext(); ) {
      session = (ProxySession)iter.next();
      service = session.getCurrentService();
      if(service.getId() != -1) {
        if(p.isEmpty() || p.contains(service.getProfileName()))
          if(services.containsKey(service)) services.put(service, new Integer(((Integer)services.get(service)).intValue() + 1));
          else services.put(service, new Integer(1));
      }
    }
    TvService[] result = new TvService[services.size()];
    Iterator iter = services.keySet().iterator();
    for(int i = 0; i < result.length; i++) {
      result[i] = (TvService)iter.next();
      result[i].setWatchers(((Integer)services.get(result[i])).intValue());
    }
    return result;
  }

  public ProfileStatus[] getProfiles() {
    Collection profiles = config.getProfiles().values();
    ProfileStatus[] ps = new ProfileStatus[profiles.size()]; int i = 0;
    for(Iterator iter = profiles.iterator(); iter.hasNext(); )
      ps[i++] = new ProfileStatus((CaProfile)iter.next());
    return ps;
  }

  public CacheStatus getCacheStatus() {
    CacheHandler ch = config.getCacheHandler();
    return new CacheStatus(ch.getClass().getName(), ch.getUsageStats());
  }

  public PluginStatus[] getPlugins() throws RemoteException {
    Map plugins = config.getProxyPlugins();
    PluginStatus[] status = new PluginStatus[plugins.size()];
    ProxyPlugin plugin; int i = 0;
    for(Iterator iter = plugins.values().iterator(); iter.hasNext(); ) {
      plugin = (ProxyPlugin)iter.next();
      status[i++] = new PluginStatus(plugin);
    }
    return status;
  }

  public boolean resetStatus(String profileName, int serviceId, long customData) {
    if(profileExists(profileName)) {
      ServiceMapping sm = new ServiceMapping(serviceId, customData);
      if(!config.getConnManager().resetStatus(profileName, sm)) {
        if(customData <= 0) {
          sm.setProviderIdent(ServiceMapping.NO_PROVIDER);
          return config.getConnManager().resetStatus(profileName, sm);
        } else return false;
      } else return true;
    }
    else return false;
  }

  public int resetStatus(String cwsName, boolean full) {
    return config.getConnManager().resetStatus(cwsName, full);
  }

  public int kickUser(String userName) {
    List sessions = sm.getSessions();
    ProxySession session; int count = 0;
    for(Iterator iter = sessions.iterator(); iter.hasNext(); ) {
      session = (ProxySession)iter.next();
      if(userName.equalsIgnoreCase(session.getUser())) {
        logger.info("Closing session, user kicked: " + session);
        session.close();
        count++;
      }
    }
    return count;
  }

  public void shutdown() {
    logger.warning("Remote shutdown requested, stopping proxy in 3 secs...");
    new Thread("RemoteShutdownThread") {
      public void run() {
        try {
          config.getConnManager().saveServiceMaps();
          Thread.sleep(3000);
          System.exit(0);
        } catch(InterruptedException e) {
          e.printStackTrace();
        }
      }
    }.start();
  }

  public int sendOsdMessage(String userName, String message) {
    List activeSessions = getSessions(null, true);
    ProxySession session; int count = 0;

    if(userName == null) logger.info("Sending newcamd OSD message to all eligible active sessions...");
    else logger.info("Sending newcamd OSD message to all eligible sessions for user '" + userName + "'...");

    for(Iterator iter = activeSessions.iterator(); iter.hasNext(); ) {
      session = (ProxySession)iter.next();
      if(session instanceof NewcamdSession) {
        if(userName != null) {
          if(userName.equalsIgnoreCase(session.getUser()))
            if(((NewcamdSession)session).sendOsdMessage(message)) count++;
        } else if(((NewcamdSession)session).sendOsdMessage(message)) count++;
      }
    }

    logger.info("Newcamd OSD message sucessfully sent to " + count + " sessions.");

    return count;
  }

  public void retryConnector(String cwsName) throws RemoteException {
    CwsConnector conn = (CwsConnector)config.getConnManager().getConnectors().get(cwsName);

    conn.close();
    conn.setEnabled(true);
  }

  public void disableConnector(String cwsName) throws RemoteException {
    CwsConnector conn = (CwsConnector)config.getConnManager().getConnectors().get(cwsName);

    conn.setEnabled(false);
    conn.close();    
  }

  public void setConnectorMetric(String cwsName, int metric) throws RemoteException {
    CwsConnector conn = (CwsConnector)config.getConnManager().getConnectors().get(cwsName);

    conn.setMetric(metric);
  }

  public boolean setAuUser(String cwsName, String user) throws RemoteException {
    return config.getConnManager().setTempAuUser(cwsName, user);
  }

  public void setProfileDebug(boolean debug, String profileName) throws RemoteException {
    CaProfile profile;
    if(profileName != null) {
      profile = config.getProfile(profileName);
      profile.setDebug(debug);
    } else {
      for(Iterator iter = config.getProfiles().values().iterator(); iter.hasNext(); ) {
        profile = (CaProfile)iter.next();
        profile.setDebug(debug);
      }
    }
  }

  public boolean setUserDebug(boolean debug, String userName) throws RemoteException {
    UserManager um = config.getUserManager();
    if(!um.exists(userName)) return false;
    um.setDebug(userName, debug);
    return true;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public int[] getCounters() {
    return new int[] {
        proxy.getEcmCount(), proxy.getEcmForwards(), proxy.getEcmCacheHits(), proxy.getEcmFailures(), proxy.getEmmCount(),
        proxy.getEcmDenied(), proxy.getEcmFiltered(), proxy.getEcmRate(), proxy.getProbeQueue()
    };
  }

  public int removeSeenUser(String name) throws RemoteException {
    return SessionManager.getInstance().removeSeenUser(name);
  }

  public int removeLoginFailure(String mask) throws RemoteException {
    return SessionManager.getInstance().removeLoginFailure(mask);
  }

  public void userStatusChanged(String userName, TvService service, String profile, String sessionId) {
    if(service != null) {
      RemoteEvent re = new RemoteEvent(RemoteEvent.USER_STATUS_CHANGED, userName, service.getName(), profile);
      re.setProperty("service", service.getName() + " (" + profile + ":" + Integer.toHexString(service.getId()) + ")");
      re.setProperty("sid", Integer.toHexString(service.getId()));
      re.setProperty("id", sessionId);
      fireRemoteEvent(re);
    }
  }

  public void userLogin(String userName, String profile, String ip, String sessionId) {
    RemoteEvent re = new RemoteEvent(RemoteEvent.USER_LOGIN, userName, ip, profile);
    re.setProperty("ip-address", ip);
    re.setProperty("id", sessionId);
    fireRemoteEvent(re);
  }

  public void userLogout(String userName, String profile, String sessionId) {
    RemoteEvent re = new RemoteEvent(RemoteEvent.USER_LOGOUT, userName, null, profile);
    re.setProperty("id", sessionId);
    fireRemoteEvent(re);
  }

  public void userLoginFailed(String userName, String profile, String ip, String reason) {
    RemoteEvent re = new RemoteEvent(RemoteEvent.USER_LOGINFAIL, userName, reason, profile);
    re.setProperty("ip-address", ip);
    fireRemoteEvent(re);
  }

  public void cwsConnected(CwsConnector cws) {
    fireRemoteEvent(new RemoteEvent(RemoteEvent.CWS_CONNECTED, cws.getLabel(), cws.toString(), cws.getProfileName()));
  }

  public void cwsDisconnected(CwsConnector cws) {
    fireRemoteEvent(new RemoteEvent(RemoteEvent.CWS_DISCONNECTED, cws.getLabel(), cws.toString(), cws.getProfileName()));
  }

  public void cwsConnectionFailed(CwsConnector cws, String message) {
    fireRemoteEvent(new RemoteEvent(RemoteEvent.CWS_CONNECTION_FAILED, cws.getLabel(), message, cws.getProfileName()));
  }

  public void cwsEcmTimeout(CwsConnector cws, String message, int failureCount) {
    if(failureCount >= config.getEtMinCount())
      fireRemoteEvent(new RemoteEvent(RemoteEvent.CWS_WARNING, cws.getLabel(), message, cws.getProfileName()));
  }

  public void cwsLostService(CwsConnector cws, TvService service, boolean show) {
    if(service != null && show)
      fireRemoteEvent(new RemoteEvent(RemoteEvent.CWS_LOST_SERVICE, cws.getLabel(), service.toString(), service.getProfileName()));
  }

  public void cwsFoundService(CwsConnector cws, TvService service, boolean show) {
    if(service != null && show) {
      String label = cws==null?"Internal[SidCacheLinker]":cws.getLabel();
      fireRemoteEvent(new RemoteEvent(RemoteEvent.CWS_FOUND_SERVICE, label, service.toString(), service.getProfileName()));
    }
  }

  public void cwsInvalidCard(CwsConnector cws, String message) {
    fireRemoteEvent(new RemoteEvent(RemoteEvent.CWS_INVALID_CARD, cws.getLabel(), message, cws.getProfileName()));
  }

  public void cwsProfileChanged(CaProfile profile, boolean added) {
    // ignore for now - todo
  }

  public void transactionCompleted(EcmTransaction transaction, ProxySession session) {
    RemoteEvent re = new RemoteEvent(RemoteEvent.ECM_TRANSACTION, session.getLabel(), session.getUser(),
        session.getProfileName());
    String cw = transaction.getReplyData();
    int sid = transaction.getServiceId();
    re.setProperty("id", SessionManager.getSessionIdStr(session));
    re.setProperty("request-hash", transaction.getRequest().hashCodeStr());
    re.setProperty("ecm-size", String.valueOf(transaction.getRequest().getDataLength()));
    if(cw != null && cw.length() > 0) re.setProperty("cw", cw);
    re.setProperty("timestamp", String.valueOf(transaction.getReadTime()));
    re.setProperty("time", String.valueOf(transaction.getDuration()));
    re.setProperty("flags", transaction.getFlags());
    re.setProperty("filtered-by", transaction.getFilteredBy());
    re.setProperty("service", transaction.getService().getName() + " (" + transaction.getService().getProfileName() +
        ":" + Integer.toHexString(sid) + ")");
    re.setProperty("sid", Integer.toHexString(sid));
    if(transaction.getReplyServiceId() != -1 && transaction.getReplyServiceId() != sid)
      re.setProperty("reply-sid", Integer.toHexString(transaction.getReplyServiceId()));
    if(transaction.getConnectorName() != null) re.setProperty("cws-name", transaction.getConnectorName());

    int pi = transaction.getRequest().getProviderIdent();
    if(pi != -1) re.setProperty("provider-ident", DESUtil.intToByteString(pi, 3));
    
    if(session.getProfile() == CaProfile.MULTIPLE) {
      re.setProperty("ca-id", DESUtil.intToHexString(transaction.getRequest().getCaId(), 4));
      re.setProperty("network-id", DESUtil.intToHexString(transaction.getRequest().getNetworkId(), 4));
    }
    if(session instanceof CspSession) re.setProperty("origin-id", Integer.toHexString(transaction.getRequest().getOriginId()));

    if("Newcamd".equals(transaction.getRequest().getProtocol())) {
      StringBuffer unknown = new StringBuffer(">");
      unknown.append(DESUtil.bytesToString(transaction.getRequest().getFixedData(), 4, 6));
      unknown.append(" (").append(transaction.getRequest().getUpperBits() >> 4).append(")");
      if("Newcamd".equals(transaction.getReply().getProtocol())) {
        unknown.append(" <").append(DESUtil.bytesToString(transaction.getReply().getFixedData(), 4, 6));
        unknown.append(" (").append(transaction.getReply().getUpperBits() >> 4).append(")");
      }
      re.setProperty("ext-newcamd", unknown.toString());
    }

    boolean warning = config.isTransactionWarning(transaction.getFlags(), transaction.getDuration());
    re.setProperty("warning", String.valueOf(warning));
    if(warning) re.setProperties(transaction.getTimings());

    fireRemoteEvent(re);
  }

  public void onLog(Level l, String label, String message) {
    if(l == Level.SEVERE || l == Level.WARNING) {
      if(config.isIncludeFileEvents()) {
        RemoteEvent re = new RemoteEvent(RemoteEvent.LOG_EVENT, label, message, null);
        re.setProperty("log-level", l.getName());
        fireRemoteEvent(re);
      }
    }
  }  

  public synchronized void destroy() {
    eventQueue.clear();
    listeners.clear();
    sm.removeUserStatusListener(this);
    config.getConnManager().removeCwsListener(this);
    eventThread = null;
    notify();
  }

  public void run() {

    while(Thread.currentThread() == eventThread) {
      synchronized(this) {
        try {
          wait();
        } catch(InterruptedException e) {
          eventQueue.clear();
          break;
        }
      }

      while(!eventQueue.isEmpty()) {
        RemoteEvent event = (RemoteEvent)eventQueue.remove(0);
        logger.finer("Firing remote event: " + event + " " + listeners);
        RemoteListener listener;
        for(Iterator iter = new ArrayList(listeners.keySet()).iterator(); iter.hasNext(); ) {
          listener = (RemoteListener)iter.next();
          try {
            if(listeners.get(listener) == null || listeners.get(listener).equals(event.getProfile()))
              listener.eventRaised(event);
          } catch(RuntimeException e) {
            logger.warning("Exception in remote event handling: " + e);
            logger.throwing(e);
            e.printStackTrace();
          } catch(RemoteException e) {
            logger.warning("Exception in remote event handling: " + e.getCause());
            logger.throwing(e);
            listeners.remove(listener);
          } catch(Exception e) {
            logger.severe("Exception in remote event handling: " + e, e);
            listeners.remove(listener);
          }
        }
      }
      
    }

  }

}
