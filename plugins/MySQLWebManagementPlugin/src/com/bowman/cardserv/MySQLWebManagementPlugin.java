package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.mysql.*;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.web.*;
import com.bowman.xml.*;

import java.io.*;
import java.net.*;
import java.rmi.RemoteException;
import java.util.*;

/**
 * This plugin adds the functionality to add/edit/delete/import/show users and profiles
 * through the web-frontend. It only works together with the "MySQLUserManager" (not with the Simple
 * or XMLUserManager).
 * The only task of this plugin is to provide status- and ctrl-commands to get/send the changes
 * to/from the backend and is therefor independent from the actual user manager.
 * So the "MySQLUserManager" can also be used without this plugin.
 *
 * @author DonCarlo
 * @since 29.12.2010
 */
public class MySQLWebManagementPlugin implements ProxyPlugin {

  private static final String CTRL_COMMANDS_FILE = "ctrl-commands.xml";
  private static final String STATUS_COMMANDS_FILE = "status-commands.xml";

  private static final int NUM_ROWS = 25;

  private MySQLUserManager mysqlUserManager;
  protected ProxyLogger logger = null;
  private Set commands = new HashSet();

  public MySQLWebManagementPlugin() {
    logger = ProxyLogger.getLabeledLogger(getClass().getName());
  }

  private void getMySQLUserManager() {
    if(mysqlUserManager == null) {
      if(ProxyConfig.getInstance().getUserManager() instanceof MySQLUserManager) {
        mysqlUserManager = (MySQLUserManager)ProxyConfig.getInstance().getUserManager();
        logger.fine("(getMySQLUserManager) received instance of MySQLUserManager.");
      } else {
        logger.severe("Could not get instance of MySQLUserManager! Maybe not in use?",
            new ConfigException("could not get MySQLUserManager"));
      }
    }

  }

  protected void registerCommands() {
    try {
      commands.addAll(XmlHelper.registerControlCommands(MySQLWebManagementPlugin.class.getResourceAsStream(CTRL_COMMANDS_FILE), this, null));
    } catch(Exception e) {
      logger.severe("Failed to load/parse internal control commands (ctrl-commands.xml).", e);
    }
    try {
      commands.addAll(XmlHelper.registerStatusCommands(MySQLWebManagementPlugin.class.getResourceAsStream(STATUS_COMMANDS_FILE), this, null));
    } catch(Exception e) {
      logger.severe("Failed to load/parse internal status commands (status-commands.xml).", e);
    }
  }

  private Set stringToSetOfInteger(String token) {
    Set set = new HashSet();
    for(StringTokenizer st = new StringTokenizer(token); st.hasMoreTokens(); ) set.add(new Integer(st.nextToken()));
    return set;
  }

  public CtrlCommandResult runCtrlCmdMysqlAddUser(Map params) {
    getMySQLUserManager();

    if(params.get("username").equals(""))
      return new CtrlCommandResult(false, "please enter a username.");

    if(((String)params.get("username")).contains(" "))
      return new CtrlCommandResult(false, "the username contains whitespaces.");

    if(params.get("password").equals(""))
      return new CtrlCommandResult(false, "please enter a password.");

    if(!params.get("password").equals(params.get("passwordretyped")))
      return new CtrlCommandResult(false, "please check the password.");

    if(mysqlUserManager.existsMySQLUser((String)params.get("username")))
      return new CtrlCommandResult(false, "the username already exists in database.");

    if(mysqlUserManager.addUser(
        (String)params.get("username"),
        (String)params.get("password"),
        (String)params.get("displayname"),
        (String)params.get("ipmask"),
        Integer.parseInt((String)params.get("maxconnections")),
        Boolean.parseBoolean((String)params.get("enabled")),
        Boolean.parseBoolean((String)params.get("debug")),
        Boolean.parseBoolean((String)params.get("admin")),
        (String)params.get("mail"),
        Boolean.parseBoolean((String)params.get("mapexcluded")),
        stringToSetOfInteger((String)params.get("profile_ids"))
    )) {
      return new CtrlCommandResult(true, "user successfully added to database.");
    } else return new CtrlCommandResult(false, "failed to add user to database.");

  }

  public CtrlCommandResult runCtrlCmdMysqlEditUser(Map params) {
    getMySQLUserManager();

    if("".equals(params.get("username")))
      return new CtrlCommandResult(false, "please enter a username.");

    if(((String)params.get("username")).contains(" "))
      return new CtrlCommandResult(false, "the username contains whitespaces.");

    if(!mysqlUserManager.existsMySQLUser((String)params.get("username")))
      return new CtrlCommandResult(false, "the username doesn't exist in database.");

    if("".equals(params.get("password")))
      return new CtrlCommandResult(false, "please enter a password.");

    if(!params.get("password").equals(params.get("passwordretyped")))
      return new CtrlCommandResult(false, "please check the password.");

    if(mysqlUserManager.editUser(
        Integer.parseInt((String)params.get("id")),
        (String)params.get("username"),
        (String)params.get("password"),
        (String)params.get("displayname"),
        (String)params.get("ipmask"),
        Integer.parseInt((String)params.get("maxconnections")),
        Boolean.parseBoolean((String)params.get("enabled")),
        Boolean.parseBoolean((String)params.get("debug")),
        Boolean.parseBoolean((String)params.get("admin")),
        (String)params.get("mail"),
        Boolean.parseBoolean((String)params.get("mapexcluded")),
        stringToSetOfInteger((String)params.get("profile_ids"))
    )) {
      return new CtrlCommandResult(true, "user successfully edited.");
    } else return new CtrlCommandResult(false, "failed to edit user in database.");
  }

  public CtrlCommandResult runCtrlCmdMysqlDeleteUser(Map params, String user) {
    getMySQLUserManager();

    if("".equals(params.get("username")))
      return new CtrlCommandResult(false, "please enter a username.");

    if(!mysqlUserManager.existsMySQLUser((String)params.get("username")))
      return new CtrlCommandResult(false, "the username does not exist in the database.");

    if(!params.get("username").equals(user)) {
      if(mysqlUserManager.deleteUser((String)params.get("username"))) {
        return new CtrlCommandResult(true, "user '" + params.get("username") + "' successfully deleted.");
      } else {
        return new CtrlCommandResult(false, "user '" + params.get("username") + "' not deleted.");
      }
    } else {
      return new CtrlCommandResult(false, "you can't delete yourself!");
    }
  }

  public CtrlCommandResult runCtrlCmdMysqlDeleteAllUsers(Map params, String user) {
    getMySQLUserManager();

    if(mysqlUserManager.deleteAllUsers(user)) {
      return new CtrlCommandResult(true, "all users successfully deleted.");
    } else {
      return new CtrlCommandResult(false, "all users not deleted.");
    }
  }

  public CtrlCommandResult runCtrlCmdMysqlImport(Map params) {
    getMySQLUserManager();

    String newFile = null;
    try {
      newFile = FileFetcher.fetchFile(new URL((String)params.get("url")),
          params.get("key").equals("") ? null : (String)params.get("key"), -1);
    } catch(MalformedURLException e) {
      logger.throwing(e);
      logger.warning("Malformed URL: " + e.getMessage());
    } catch(IOException e) {
      logger.throwing(e);
      logger.warning("Failed to fetch user file '" + params.get("url") + "': " + e);
    }

    if(newFile != null && processUserFile(newFile)) {
      return new CtrlCommandResult(true, "Users successfully imported.");
    }
    return new CtrlCommandResult(false, "Importing users failed!");
  }

  private boolean processUserFile(String newFile) {
    Set newUsers = new HashSet();
    try {
      ProxyXmlConfig xml = new ProxyXmlConfig(new XMLConfig(newFile, false));
      for(Iterator iter = xml.getMultipleSubConfigs("user"); iter.hasNext(); ) {
        User user = parseUser((ProxyXmlConfig)iter.next());
        if(!mysqlUserManager.existsMySQLUser(user.getUserName())) {
          newUsers.add(user);
        }
      }

    } catch(XMLConfigException e) {
      logger.throwing(e);
      logger.warning("Unable to parse '" + newFile + "': " + e.getMessage());
    } catch(ConfigException e) {
      logger.throwing(e);
      logger.warning("Error in user file '" + newFile + "': " + e.getMessage());
    }
    return mysqlUserManager.importUsers(newUsers);
  }

  private User parseUser(ProxyXmlConfig xml) throws ConfigException {
    String ipMask = xml.getStringValue("ip-mask", "*");
    String username = xml.getStringValue("name");
    String password = xml.getStringValue("password");

    String displayName = "";
    try {
      displayName = xml.getStringValue("display-name");
    } catch(ConfigException e) {
    }

    Set allowedProfiles = new HashSet();
    try {
      String profiles = xml.getStringValue("profiles");
      for(StringTokenizer st = new StringTokenizer(profiles); st.hasMoreTokens(); ) allowedProfiles.add(st.nextToken());
    } catch(ConfigException e) {
    }

    String email = "";
    try {
      email = xml.getStringValue("email-address");
    } catch(ConfigException e) {
    }

    int maxConnections = xml.getIntValue("max-connections", 1);

    boolean isEnabled = "true".equalsIgnoreCase(xml.getStringValue("enabled", "true"));
    boolean isAdmin = "true".equalsIgnoreCase(xml.getStringValue("admin", "false"));
    boolean mapExcluded = "true".equalsIgnoreCase(xml.getStringValue("map-exclude", "false"));
    boolean isDebug = "true".equalsIgnoreCase(xml.getStringValue("debug", "false"));

    return new User(username, password, displayName, maxConnections, ipMask, email,
        allowedProfiles, isEnabled, isAdmin, isDebug, mapExcluded);
  }

  public void runStatusCmdMysqlUsers(XmlStringBuffer xb, Map params) throws RemoteException {
    getMySQLUserManager();

    xb.appendElement("mysql-users");

    for(int i = 1; i <= mysqlUserManager.getMySQLUserCount() / NUM_ROWS; i++) {
      xb.appendElement("page", "num", i);
      xb.closeElement("page");
    }
    if(mysqlUserManager.getMySQLUserCount() % NUM_ROWS != 0) {
      xb.appendElement("page", "num", (mysqlUserManager.getMySQLUserCount() / NUM_ROWS) + 1);
      xb.closeElement("page");
    }

    if(params.get("username") != null) {
      User user = mysqlUserManager.getMySQLUser((String)params.get("username"));
      if(user != null) xmlFormatUser(xb, user, false);
    } else {
      Iterator iterator;
      if(params.get("pageNum") != null) {
        iterator = mysqlUserManager.getMySQLUserNames(Integer.parseInt((String)params.get("pageNum")) * NUM_ROWS, NUM_ROWS).iterator();
      } else {
        iterator = mysqlUserManager.getMySQLUserNames().iterator();
      }
      while(iterator.hasNext()) xmlFormatUser(xb, mysqlUserManager.getMySQLUser((String)iterator.next()), false);
    }
    xb.closeElement("mysql-users");
  }

  public void runStatusCmdMysqlAddUser(XmlStringBuffer xb, Map params) throws RemoteException {
    getMySQLUserManager();

    xb.appendElement("mysql-add-user");
    Iterator iterator = mysqlUserManager.getProfiles().iterator();
    while(iterator.hasNext()) xmlFormatProfile(xb, (Profile)iterator.next());
    xb.closeElement("mysql-add-user");
  }

  public void runStatusCmdMysqlEditUser(XmlStringBuffer xb, Map params) throws RemoteException {
    getMySQLUserManager();

    xb.appendElement("mysql-edit-user");
    xmlFormatUser(xb, mysqlUserManager.getMySQLUser((String)params.get("username")), true);
    Iterator iterator = mysqlUserManager.getProfiles().iterator();
    while(iterator.hasNext()) xmlFormatProfile(xb, (Profile)iterator.next());
    xb.closeElement("mysql-edit-user");
  }

  public void runStatusCmdMysqlImport(XmlStringBuffer xb, Map params) throws RemoteException {
    xb.appendElement("mysql-import");
    xb.closeElement("mysql-import");
  }

  private void xmlFormatUser(XmlStringBuffer xb, User user, boolean includePassword) {
    xb.appendElement("user", "id", user.getId());
    xb.appendAttr("username", user.getUserName());
    if(includePassword) xb.appendAttr("password", user.getPassword());
    xb.appendAttr("displayname", user.getDisplayName());
    xb.appendAttr("mail", user.getEmail());
    xb.appendAttr("ipmask", user.getIpMask());
    String profiles = user.getAllowedProfiles().toString();
    xb.appendAttr("profiles", (profiles.equals("[]")) ? "" : profiles.substring(1, profiles.length() - 1).replace(", ", " "));
    xb.appendAttr("maxconnections", user.getMaxConnections());
    xb.appendAttr("enabled", user.isEnabled());
    xb.appendAttr("admin", user.isAdmin());
    xb.appendAttr("debug", user.isDebug());
    xb.appendAttr("mapexcluded", user.isMapExcluded());
    xb.endElement(true);
  }

  public CtrlCommandResult runCtrlCmdMysqlAddProfile(Map params) {
    getMySQLUserManager();

    if(params.get("profilename").equals(""))
      return new CtrlCommandResult(false, "please enter a profilename.");

    if(((String)params.get("profilename")).contains(" "))
      return new CtrlCommandResult(false, "the profilename contains whitespaces.");

    if(mysqlUserManager.existsProfile((String)params.get("profilename")))
      return new CtrlCommandResult(false, "the profile already exists in database.");

    if(mysqlUserManager.addProfile((String)params.get("profilename"))) {
      return new CtrlCommandResult(true, "profile successfully added to database.");
    } else {
      return new CtrlCommandResult(false, "profile not added to database.");
    }

  }

  public CtrlCommandResult runCtrlCmdMysqlEditProfile(Map params) {
    getMySQLUserManager();

    if(params.get("id").equals(""))
      return new CtrlCommandResult(false, "please enter a profil id.");

    if(params.get("profilename").equals(""))
      return new CtrlCommandResult(false, "please enter a profilename.");

    if(((String)params.get("profilename")).contains(" "))
      return new CtrlCommandResult(false, "the name contains whitespaces.");

    if(!mysqlUserManager.existsProfile(Integer.parseInt((String)params.get("id"))))
      return new CtrlCommandResult(false, "the profile to edit does not exist in database.");

    if(mysqlUserManager.existsProfile((String)params.get("profilename")))
      return new CtrlCommandResult(false, "the profilename already exists in database.");

    if(mysqlUserManager.editProfile(Integer.parseInt((String)params.get("id")), (String)params.get("profilename"))) {
      return new CtrlCommandResult(true, "profile successfully edited.");
    } else {
      return new CtrlCommandResult(false, "profile not edited.");
    }
  }

  public CtrlCommandResult runCtrlCmdMysqlDeleteProfile(Map params) {
    getMySQLUserManager();

    if(params.get("id").equals(""))
      return new CtrlCommandResult(false, "please enter a profile id.");

    if(!mysqlUserManager.existsProfile(Integer.parseInt((String)params.get("id"))))
      return new CtrlCommandResult(false, "the profile to delete does not exist in database.");

    if(mysqlUserManager.deleteProfile(Integer.parseInt((String)params.get("id")))) {
      return new CtrlCommandResult(true, "profile successfully deleted.");
    } else {
      return new CtrlCommandResult(false, "profile not deleted.");
    }
  }

  public CtrlCommandResult runCtrlCmdMysqlDeleteAllProfiles() {
    getMySQLUserManager();

    if(mysqlUserManager.deleteAllProfiles()) {
      return new CtrlCommandResult(true, "all profiles successfully deleted.");
    } else {
      return new CtrlCommandResult(false, "all profiles not deleted.");
    }
  }

  public void runStatusCmdMysqlProfiles(XmlStringBuffer xb) throws RemoteException {
    getMySQLUserManager();

    xb.appendElement("mysql-profiles");
    Iterator iterator = mysqlUserManager.getProfiles().iterator();
    while(iterator.hasNext()) xmlFormatProfile(xb, (Profile)iterator.next());
    xb.closeElement("mysql-profiles");
  }

  private void xmlFormatProfile(XmlStringBuffer xb, Profile profile) {
    xb.appendElement("profile", "id", profile.getId());
    xb.appendAttr("profilename", profile.getProfileName());
    xb.endElement(true);
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {}

  public void start(CardServProxy proxy) {
    registerCommands();
  }

  public void stop() {
    commands.clear();
  }

  public String getName() {
    return "MySQLWebManagementPlugin";
  }

  public String getDescription() {
    return "Enables a web-ui for adding/editing/deleting users for the 'MySQLUserManager'.";
  }

  public Properties getProperties() {
    getMySQLUserManager();
    Properties p = new Properties();
    p.setProperty("mysql-database-host", mysqlUserManager.getDatabaseHost());
    p.setProperty("mysql-database-port", String.valueOf(mysqlUserManager.getDatabasePort()));
    p.setProperty("mysql-database-name", mysqlUserManager.getDatabaseName());
    p.setProperty("mysql-database-user", mysqlUserManager.getDatabaseUser());
    return p.isEmpty() ? null : p;
  }

  public CamdNetMessage doFilter(ProxySession session, CamdNetMessage msg) {
    return msg;
  }

  public byte[] getResource(String path, boolean admin) {
    if(path.startsWith("/")) path = path.substring(1);
    try {
      InputStream is = MySQLWebManagementPlugin.class.getResourceAsStream("/web/" + path);
      if(is == null) return null;
      DataInputStream dis = new DataInputStream(is);
      byte[] buf = new byte[dis.available()];
      dis.readFully(buf);
      return buf;
    } catch(IOException e) {
      return null;
    }
  }

  public byte[] getResource(String path, byte[] inData, boolean admin) {
    return null;
  }

}
