package com.bowman.cardserv;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import com.bowman.cardserv.interfaces.ProxyPlugin;
import com.bowman.cardserv.interfaces.ProxySession;
import com.bowman.cardserv.mysql.User;
import com.bowman.cardserv.util.ProxyLogger;
import com.bowman.cardserv.util.ProxyXmlConfig;
import com.bowman.cardserv.util.XmlStringBuffer;
import com.bowman.cardserv.web.CtrlCommandResult;
import com.bowman.cardserv.web.FileFetcher;
import com.bowman.cardserv.web.XmlHelper;
import com.bowman.cardserv.MySQLUserManager;
import com.bowman.xml.XMLConfig;
import com.bowman.xml.XMLConfigException;

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

	private final String CTRL_COMMANDS_FILE = "ctrl-commands.xml";
	private final String STATUS_COMMANDS_FILE = "status-commands.xml";
	
	private MySQLUserManager mysqlUserManager;
	protected ProxyLogger logger = null;
	private Set commands = new HashSet();
	
	public MySQLWebManagementPlugin() {
		   logger = ProxyLogger.getLabeledLogger(getClass().getName());
	}
	
	/**
	 * gets the instance of the loaded MySQLUserManager.
	 */
	private void getMySQLUserManager() {
		if (mysqlUserManager == null) {
			if (ProxyConfig.getInstance().getUserManager() instanceof MySQLUserManager) {
				mysqlUserManager = (MySQLUserManager) ProxyConfig.getInstance().getUserManager();
				logger.fine("(getMySQLUserManager) received instance of MySQLUserManager.");
			} else {
				logger.severe("Could not get instance of MySQLUserManager! Maybe not in use?", 
						new ConfigException("could not get MySQLUserManager"));
			}
		}

	}
	
	/**
	 * registers the status- and ctrl-commands defined through the xml files.
	 */
	protected void registerCommands() {
		try {
			commands.addAll(XmlHelper.registerControlCommands(MySQLWebManagementPlugin.class.getResourceAsStream(CTRL_COMMANDS_FILE), this, null));
		} catch (Exception e) {
			logger.severe("Failed to load/parse internal control commands (ctrl-commands.xml).", e);
		}
		try {
			commands.addAll(XmlHelper.registerStatusCommands(MySQLWebManagementPlugin.class.getResourceAsStream(STATUS_COMMANDS_FILE), this, null));
		} catch (Exception e) {
			logger.severe("Failed to load/parse internal status commands (status-commands.xml).", e);
		}
	}
	
	/**
	 * processes a xml user source and adds the new users to the database.
	 * If a user with the same name already exists it skips the new one.
	 * @param newFile
	 * @return TRUE, when url was successfully imported.
	 */
	private boolean processUserFile(String newFile) {
		try {
			ProxyXmlConfig xml = new ProxyXmlConfig(new XMLConfig(newFile, false));
			ArrayList newUsers = new ArrayList();
			for(Iterator iter = xml.getMultipleSubConfigs("user"); iter.hasNext(); ) {
				newUsers.add(parseUser((ProxyXmlConfig)iter.next()));
			}
			
			for (Iterator iter = newUsers.iterator(); iter.hasNext();) {
				User user = (User) iter.next();
				if (!mysqlUserManager.existsUserInDatabase(user.getUsername())) {
					mysqlUserManager.addUserToDB(user);
				} else {
					logger.info("user '" + user.getUsername() + "' already exists in MySQL database...not imported!");
				}
			}
			
			return true;
		    
		} catch(XMLConfigException e) {
			logger.throwing(e);
			logger.warning("Unable to parse '" + newFile + "': " + e.getMessage());
		} catch(ConfigException e) {
			logger.throwing(e);
			logger.warning("Error in user file '" + newFile + "': " + e.getMessage());
		}
		return false;
	}
	
	/**
	 * parses user informations out of xml.
	 * @param xml
	 * @return User object containing parsed user details.
	 * @throws ConfigException
	 */
	private User parseUser(ProxyXmlConfig xml) throws ConfigException {
		String ipMask = xml.getStringValue("ip-mask", "*");

		String username = xml.getStringValue("name");
		String password = xml.getStringValue("password");
		    
		String displayName = "";
		try {
		  	displayName = xml.getStringValue("display-name");
		} catch (ConfigException e) {}

		Set allowedProfiles = new HashSet();
		try {
			String profiles = xml.getStringValue("profiles");
			for(StringTokenizer st = new StringTokenizer(profiles); st.hasMoreTokens(); ) allowedProfiles.add(st.nextToken());
		} catch (ConfigException e) {}

		String email = "";
		try {
			email = xml.getStringValue("email-address");
		} catch (ConfigException e) {}

		int maxConnections = xml.getIntValue("max-connections", 1);

		boolean isEnabled = "true".equalsIgnoreCase(xml.getStringValue("enabled", "true"));
		boolean isAdmin = "true".equalsIgnoreCase(xml.getStringValue("admin", "false"));
		boolean mapExcluded = "true".equalsIgnoreCase(xml.getStringValue("map-exclude", "false"));
		boolean isDebug = "true".equalsIgnoreCase(xml.getStringValue("debug", "false"));

		return new User(username, password, displayName, maxConnections, ipMask, email, 
				allowedProfiles, isEnabled, isAdmin, isDebug, mapExcluded);
	}
	  
	/* ############################################################################################ */
	/* ctrl-commands (user)                                                                         */
	/* ############################################################################################ */
	
	/**
	 * add a new MySQL database user
	 * @param params
	 * @return CtrlCommandResult
	 */
	public CtrlCommandResult runCtrlCmdMysqlAddUser(Map params) {
		getMySQLUserManager();
		
		// must be entered in order to add a user
		if (((String)params.get("username")).equals(new String("")))
			return new CtrlCommandResult(false, "please enter a username.");
		
		if (((String)params.get("username")).contains(" "))
			return new CtrlCommandResult(false, "the username contains whitespaces.");
		
		if (((String)params.get("password")).equals(new String("")))
			return new CtrlCommandResult(false, "please enter a password.");
		
		if (!((String)params.get("password")).equals((String)params.get("passwordretyped")))
			return new CtrlCommandResult(false, "please check the password.");
		
		// does the username already exists?
		if (mysqlUserManager.existsUserInDatabase((String)params.get("username")))
			return new CtrlCommandResult(false, "the username already exists in database.");

		mysqlUserManager.addUserToDB(
				(String)params.get("username"), 
				(String)params.get("password"), 
				((String)params.get("displayname")).equals(new String("")) ? 
						(String)params.get("username") : (String)params.get("displayname"), 
				((String)params.get("ipmask")).equals(new String("")) ? "*" : (String)params.get("ipmask"), 
				Integer.parseInt((String)params.get("maxconnections")), 
				Boolean.parseBoolean((String)params.get("enabled")), 
				Boolean.parseBoolean((String)params.get("debug")), 
				Boolean.parseBoolean((String)params.get("admin")), 
				(String)params.get("mail"), 
				Boolean.parseBoolean((String)params.get("mapexcluded")),
				(String)params.get("profiles")
		);
		return new CtrlCommandResult(true, "user successfully added to database.");
	}
	
	/**
	 * edit an existing MySQL database user.
	 * @param params
	 * @return CtrlCommandResult
	 */
	public CtrlCommandResult runCtrlCmdMysqlEditUser(Map params) {
		getMySQLUserManager();
		
		// must be entered in order to add a user
		if (((String)params.get("username")).equals(new String("")))
			return new CtrlCommandResult(false, "please enter a username.");
		
		// does the username already exists?
		if (!mysqlUserManager.existsUserInDatabase((String)params.get("username")))
			return new CtrlCommandResult(false, "the username doesn't exist in database.");
		
		mysqlUserManager.editUserInDB(
				(String)params.get("username"),
				(String)params.get("password"),
				(String)params.get("displayname"),
				((String)params.get("ipmask")).equals(new String("")) ? "*" : (String)params.get("ipmask"), 
				Integer.parseInt((String)params.get("maxconnections")), 
				Boolean.parseBoolean((String)params.get("enabled")), 
				Boolean.parseBoolean((String)params.get("debug")), 
				Boolean.parseBoolean((String)params.get("admin")), 
				(String)params.get("mail"), 
				Boolean.parseBoolean((String)params.get("mapexcluded")),
				(String)params.get("profiles")
		);
		return new CtrlCommandResult(true, "user successfully edited.");
	}
	
	/**
	 * delete an MySQL database user.
	 * @param params - username (user which should be deleted or ALL when all should be delted.)
	 * @param user - current logged in user - which does not get deleted.
	 * @return CtrlCommandResult
	 */
	public CtrlCommandResult runCtrlCmdMysqlDeleteUser(Map params, String user) {
		// make sure we got it
		getMySQLUserManager();
		
		if (((String)params.get("username")).equals(new String("")))
			return new CtrlCommandResult(false, "please enter a username.");
		
		if (!mysqlUserManager.existsUserInDatabase((String)params.get("username")) && 
				!((String)params.get("username")).equals(new String("ALL")))
			return new CtrlCommandResult(false, "the username does not exist in the database.");
		
		if (((String)params.get("username")).equals(new String("ALL"))) {
			mysqlUserManager.deleteAllUsersFromDB(user);
			return new CtrlCommandResult(true, "all users successfully deleted.");
		} else {
			if (!((String)params.get("username")).equals(user)) {
				mysqlUserManager.deleteUserFromDB((String)params.get("username"));
				return new CtrlCommandResult(true, "user successfully deleted.");
			} else {
				return new CtrlCommandResult(false, "you can't delete yourself!");
			}
		}
	}
	
	/* ############################################################################################ */
	/* ctrl-commands (import/export)                                                                */
	/* ############################################################################################ */
	
	public CtrlCommandResult runCtrlCmdMysqlImportUsers(Map params) {
		// make sure we got it
		getMySQLUserManager();
		
        String newFile = null;
        try {
        	newFile = FileFetcher.fetchFile(new URL((String)params.get("url")),
        			((String)params.get("key")).equals(new String("")) ? null : (String)params.get("key"), -1);
        } catch (MalformedURLException e) {
            logger.throwing(e);
            logger.warning("Malformed URL: " + e.getMessage());
        } catch (IOException e) {
            logger.throwing(e);
            logger.warning("Failed to fetch user file '" + (String)params.get("url") +"': " + e);
		}
        
        if(newFile != null && processUserFile(newFile)) {
    		return new CtrlCommandResult(true, "Users successfully imported.");
        }
		return new CtrlCommandResult(false, "Importing users failed!");
	}

	/* ############################################################################################ */
	/* status-commands (user)                                                                         */
	/* ############################################################################################ */
	
	public void runStatusCmdMysqlUsers(XmlStringBuffer xb) throws RemoteException {
		getMySQLUserManager();
		
		xb.appendElement("mysql-users");
		String[] userNames = mysqlUserManager.getMySQLUserNames();
		for(int i = 0; i < userNames.length; i++) {
			xmlFormatUser(xb, userNames[i], false);
		}
		xb.closeElement("mysql-users");
	} 
	
	public void runStatusCmdMysqlUserDetails(XmlStringBuffer xb, Map params, String user) throws RemoteException {
		getMySQLUserManager();
		
		if (mysqlUserManager.isAdmin(user)) {
			xb.appendElement("mysql-user-details");
			xmlFormatUser(xb, (String)params.get("username"), false);
			xb.closeElement("mysql-user-details");
		}
	}

	public void runStatusCmdMysqlAddUser(XmlStringBuffer xb) throws RemoteException {
		getMySQLUserManager();
		
		xb.appendElement("mysql-add-user");
		String[] profileNames = mysqlUserManager.getProfileNames();
		for(int i = 0; i < profileNames.length; i++) {
			xmlFormatProfile(xb, profileNames[i]);
		}
		xb.closeElement("mysql-add-user");
	} 
	
	public void runStatusCmdMysqlEditUser(XmlStringBuffer xb, Map params) throws RemoteException {
		getMySQLUserManager();
		
		xb.appendElement("mysql-edit-user");
		User user = mysqlUserManager.getMySQLUser((String)params.get("username"));
		xb.appendElement("user", "username", user.getUsername());
		xb.appendAttr("password", user.getPassword());
		xb.appendAttr("displayname", user.getDisplayName());
		xb.appendAttr("mail", user.getEmail());
		xb.appendAttr("ipmask", user.getIpMask());
		xb.appendAttr("maxconnections", user.getMaxConnections());
		xb.appendAttr("enabled", user.isEnabled());
		xb.appendAttr("admin", user.isAdmin());
		xb.appendAttr("debug", user.isDebug());
		xb.appendAttr("mapexcluded", user.isMapExcluded());
		xb.endElement(true);
		
		Set allowedProfiles = user.getAllowedProfiles();
		String[] profileNames = mysqlUserManager.getProfileNames();
		for(int i = 0; i < profileNames.length; i++) {
			xb.appendElement("profile", "profilename", profileNames[i]);
			xb.appendAttr("checked", allowedProfiles.contains(profileNames[i]) ? "true" : "false");
			xb.endElement(true);
		}
		xb.closeElement("mysql-edit-user");
	} 
	
	public void runStatusCmdMysqlDeleteUser(XmlStringBuffer xb) throws RemoteException {
		getMySQLUserManager();
		
		xb.appendElement("mysql-delete-user");
		xmlFormatUserNames(xb);
		xb.closeElement("mysql-delete-user");
	} 
	
	public void runStatusCmdMysqlSelectUser(XmlStringBuffer xb) throws RemoteException {
		getMySQLUserManager();
		
		xb.appendElement("mysql-select-user");
		xmlFormatUserNames(xb);
		xb.closeElement("mysql-select-user");
	} 
	
	private void xmlFormatUser(XmlStringBuffer xb, String username, boolean includePassword) {
		User user = mysqlUserManager.getMySQLUser(username);
		xb.appendElement("user", "username", user.getUsername());
		if (includePassword)
			xb.appendAttr("password", user.getPassword());
		xb.appendAttr("displayname", user.getDisplayName());
		xb.appendAttr("mail", user.getEmail());
		xb.appendAttr("ipmask", user.getIpMask());
		String profiles = user.getAllowedProfiles().toString();
		xb.appendAttr("profiles", (profiles.equals(new String("[]"))) ? "" : 
			profiles.substring(1, profiles.length() - 1).replace(", ", " "));
		xb.appendAttr("maxconnections", user.getMaxConnections());
		xb.appendAttr("enabled", user.isEnabled());
		xb.appendAttr("admin", user.isAdmin());
		xb.appendAttr("debug", user.isDebug());
		xb.appendAttr("mapexcluded", user.isMapExcluded());
		xb.endElement(true);
	}

	private void xmlFormatUserNames(XmlStringBuffer xb) {
		String[] userNames = mysqlUserManager.getMySQLUserNames();
		for(int i = 0; i < userNames.length; i++) {
			xb.appendElement("entry", "username", userNames[i]);
			xb.closeElement("entry");
		}
	}
	
	/* ############################################################################################ */
	/* status-commands (database details)                                                                         */
	/* ############################################################################################ */
	
	public void runStatusCmdMysqlDatabaseDetails(XmlStringBuffer xb, Map params, String user) throws RemoteException {
		getMySQLUserManager();
		
		if (mysqlUserManager.isAdmin(user)) {
			xb.appendElement("mysql-database-details", "host", mysqlUserManager.getDatabaseHost());
			xb.appendAttr("name", mysqlUserManager.getDatabaseName());
			xb.appendAttr("port", mysqlUserManager.getDatabasePort());
			xb.appendAttr("user", mysqlUserManager.getDatabaseUser());
			xb.endElement(false);
			xb.closeElement("mysql-database-details");
		}
	} 
	
	/* ############################################################################################ */
	/* ctrl-commands (profile)                                                                      */
	/* ############################################################################################ */
	
	/**
	 * add profile to the MySQL database.
	 * @param params
	 * @return CtrlCommandResult
	 */
	public CtrlCommandResult runCtrlCmdMysqlAddProfile(Map params) {
		getMySQLUserManager();
		
		if (((String)params.get("profilename")).equals(new String("")))
			return new CtrlCommandResult(false, "please enter a profilename.");
		
		if (((String)params.get("profilename")).contains(" "))
			return new CtrlCommandResult(false, "the name contains whitespaces.");
		
		// does the profile already exists?
		if (mysqlUserManager.existsProfileInDatabase((String)params.get("profilename")))
			return new CtrlCommandResult(false, "the profile already exists in database.");
		
		mysqlUserManager.addProfileToDB((String)params.get("profilename"));
		
		return new CtrlCommandResult(true, "profile successfully added to database.");
	}
	
	/**
	 * delete profile from MySQL database.
	 * @param params
	 * @return CtrlCommandResult
	 */
	public CtrlCommandResult runCtrlCmdMysqlDeleteProfile(Map params) {
		getMySQLUserManager();
		
		if (((String)params.get("profilename")).equals(new String("")))
			return new CtrlCommandResult(false, "please enter a profilename.");
		
		if (!mysqlUserManager.existsProfileInDatabase((String)params.get("profilename")) && 
				!((String)params.get("profilename")).equals(new String("ALL")))
			return new CtrlCommandResult(false, "the profilename does not exist in the database.");
		
		if (((String)params.get("profilename")).equals(new String("ALL"))) {
			mysqlUserManager.deleteAllProfilesFromDB();
			return new CtrlCommandResult(true, "all profiles successfully deleted.");
		} else {
			mysqlUserManager.deleteProfileFromDB((String)params.get("profilename"));
			return new CtrlCommandResult(true, "profile successfully deleted.");
		}
	}
	
	/* ############################################################################################ */
	/* status-commands (profile)                                                                    */
	/* ############################################################################################ */
	
	public void runStatusCmdMysqlProfiles(XmlStringBuffer xb) throws RemoteException {
		getMySQLUserManager();
		
		xb.appendElement("mysql-profiles");
		String[] profileNames = mysqlUserManager.getProfileNames();
		for(int i = 0; i < profileNames.length; i++) {
			xmlFormatProfile(xb, profileNames[i]);
		}
		xb.closeElement("mysql-profiles");
	} 

	private void xmlFormatProfile(XmlStringBuffer xb, String profileName) {
		xb.appendElement("profile", "profilename", profileName);
		xb.closeElement("profile");
	}
	
	/* ############################################################################################ */
	/* ProxyPlugin                                                                                  */
	/* ############################################################################################ */
	
	public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
	}
	
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
	    } catch (IOException e) {
	      return null;
	    }    
	}

	public byte[] getResource(String path, byte[] inData, boolean admin) {
		return null;
	}

}
