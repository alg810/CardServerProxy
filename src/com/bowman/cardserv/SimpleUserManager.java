package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.UserManager;
import com.bowman.cardserv.util.ProxyXmlConfig;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 8, 2005
 * Time: 5:48:16 PM
 */
public class SimpleUserManager implements UserManager {

  protected Map users = new HashMap();
  protected UserEntry defaultUser;

  protected String openPrefix, openPasswd;
  protected Set openProfiles;

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    users.clear();

    ProxyXmlConfig openConf = null;
    try {
      openConf = xml.getSubConfig("open-access");
    } catch (ConfigException e) {}
    if(openConf != null && "true".equals(openConf.getStringValue("enabled", "true"))) {
      openPrefix = openConf.getStringValue("open-username-prefix", "");
      openPasswd = openConf.getStringValue("open-password");
      String profiles = openConf.getStringValue("open-profiles", "");
      if(profiles.length() > 0) openProfiles = new HashSet(Arrays.asList(profiles.split(" ")));
      else openProfiles = Collections.EMPTY_SET;
    } else {
      openPrefix = null;
      openPasswd = null;
      openProfiles = Collections.EMPTY_SET;
    }

    Iterator iter = xml.getMultipleSubConfigs("user");
    ProxyXmlConfig userConf;
    try {
      userConf = xml.getSubConfig("default-user");
      defaultUser = parseUser(userConf);
    } catch (ConfigException e) {
      defaultUser = null;
    }
    while(iter.hasNext()) {
      userConf = (ProxyXmlConfig)iter.next();
      addUser(userConf);
    }   
  }

  protected void addUser(ProxyXmlConfig xml) throws ConfigException {
    addUser(parseUser(xml), users, false);
  }

  protected void addUser(UserEntry user, Map users, boolean overwrite) throws ConfigException {
    if(users.put(user.name.toLowerCase(), user) != null && !overwrite)
      throw new ConfigException("Duplicate user definition: " + user.name);
  }

  protected UserEntry parseUser(ProxyXmlConfig xml) throws ConfigException {
    String ipMask = xml.getStringValue("ip-mask", "*");

    String emailAddr = null;
    try {
      emailAddr = xml.getStringValue("email-address");
    } catch (ConfigException e) {}

    int maxConnections = xml.getIntValue("max-connections", -1);

    boolean enabled = "true".equalsIgnoreCase(xml.getStringValue("enabled", "true"));
    boolean admin = "true".equalsIgnoreCase(xml.getStringValue("admin", "false"));
    boolean exclude = "true".equalsIgnoreCase(xml.getStringValue("map-exclude", "false"));
    boolean debug = "true".equalsIgnoreCase(xml.getStringValue("debug", "false"));

    UserEntry user = new UserEntry(xml.getStringValue("name"), xml.getStringValue("password"), ipMask, emailAddr,
        maxConnections, enabled, admin, exclude, debug);

    try {
      user.displayName = xml.getStringValue("display-name");
    } catch (ConfigException e) {}

    try {
      String profiles = xml.getStringValue("profiles");
      for(StringTokenizer st = new StringTokenizer(profiles); st.hasMoreTokens(); ) user.profiles.add(st.nextToken());
    } catch (ConfigException e) {}

    return user;
  }

  public String[] getUserNames() {
    List userNames = new ArrayList(users.keySet());
    Collections.sort(userNames);
    return (String[])userNames.toArray(new String[userNames.size()]);
  }

  protected UserEntry getUser(String name) {
    if(name == null) return null;
    else {
      UserEntry user = (UserEntry)users.get(name.toLowerCase());
      if(user != null) return user;
      else return defaultUser;
    }
  }

  protected boolean matchesOpen(String user) {
    if(openPrefix == null) return false;
    if("".equals(openPrefix)) return true;
    else return user.toLowerCase().startsWith(openPrefix.toLowerCase());
  }

  public String getPassword(String user) {
    UserEntry entry = getUser(user);
    if(entry == null) {
      if(matchesOpen(user)) return openPasswd;
      else return null;
    } else return entry.password;
  }

  public String getUserName(String user) {
    UserEntry entry = getUser(user);
    if(entry == null) {
      String suffix = Long.toString(System.currentTimeMillis(), Character.MAX_RADIX);
      if("".equals(openPrefix)) return suffix;
      else if(matchesOpen(user)) return openPrefix + suffix;
      else return null;
    } else if(entry == defaultUser) return user;
    else return entry.name;
  }

  public boolean authenticate(String user, String pass) {
    UserEntry entry = getUser(user);
    if(entry == null || entry == defaultUser) return false;
    else return entry.password.equals(pass);
  }

  public String getIpMask(String user) {
    UserEntry entry = getUser(user);
    if(entry == null) return "*";
    else return entry.ipMask;
  }

  public String getEmailAddress(String user) {
    UserEntry entry = getUser(user);
    if(entry == null) return null;
    else return entry.email;
  }

  public String getDisplayName(String user) {
    UserEntry entry = getUser(user);
    if(entry == null) {
      if(matchesOpen(user)) return user;
      else return null;
    } else return entry.displayName;
  }

  public int getMaxConnections(String user) {
    UserEntry entry = getUser(user);
    if(entry == null) return 1;
    else return entry.maxConnections;
  }

  public Set getAllowedProfiles(String user) {
    UserEntry entry = getUser(user);
    if(entry == null) {
      if(matchesOpen(user)) return openProfiles;
      else return Collections.EMPTY_SET;
    }
    else return entry.profiles;
  }

  public boolean isEnabled(String user) {
    UserEntry entry = getUser(user);
    if(entry == null) return true;
    else return entry.enabled;
  }

  public boolean isAdmin(String user) {
    UserEntry entry = getUser(user);
    if(entry == null) return false;
    else return entry.admin;
  }

  public boolean exists(String user) {
    return getPassword(user) != null;
  }

  public boolean isMapExcluded(String user) {
    UserEntry entry = getUser(user);
    if(entry == null) return false;
    else return entry.exclude;
  }

  public boolean isDebug(String user) {
    UserEntry entry = getUser(user);
    if(entry == null) return false;
    else return entry.debug;
  }

  public void setDebug(String user, boolean debug) {
    UserEntry entry = getUser(user);
    if(entry != null) entry.debug = debug;    
  }

  public int getUserCount() {
    return users.size();
  }

  public void start() {
  }

  // access control/limits
  public Set getAllowedServices(String user, String profile) {
    return null; // return Set of Integer, null for all
  }

  public Set getBlockedServices(String user, String profile) {
    return null; // return Set of Integer, null for all
  }

  public Set getAllowedConnectors(String user) {
    return null; // return Set of String, null for all
  }

  public int getAllowedEcmRate(String user) {
    return -1; // return minimum interval between ecm in seconds, -1 for no limit
  }

  static class UserEntry {

    String name, password;
    String ipMask;
    String email, displayName;
    int maxConnections;
    boolean enabled, admin, exclude, debug;
    Set profiles = new HashSet();

    public UserEntry(String name, String password, String ipMask, String email, int maxConnections, boolean enabled,
                     boolean admin, boolean exclude, boolean debug)
    {
      this.name = name;
      this.displayName = name;
      this.password = password;
      this.ipMask = ipMask;
      this.email = email;
      this.maxConnections = maxConnections;
      this.enabled = enabled;
      this.admin = admin;
      this.exclude = exclude;
      this.debug = debug;
    }

  }

}
