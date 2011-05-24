package com.bowman.cardserv;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import com.bowman.cardserv.interfaces.ProxyPlugin;
import com.bowman.cardserv.interfaces.ProxySession;
import com.bowman.cardserv.mysql.Profile;
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
	
	private final int NUM_ROWS = 25;
	
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
	
	private Set stringToSetOfInteger(String token) {
		Set set = new HashSet();
		for(StringTokenizer st = new StringTokenizer(token); st.hasMoreTokens(); ) set.add(new Integer(st.nextToken()));
		return set;
	}
	
	/* ############################################################################################ */
	/* ctrl-commands (user)																			*/
	/* ############################################################################################ */
	
	/**
	 * add a new MySQL database user
	 * @param params
	 * @return CtrlCommandResult
	 */
	public CtrlCommandResult runCtrlCmdMysqlAddUser(Map params) {
		getMySQLUserManager();
		
		if (((String)params.get("username")).equals(new String("")))
			return new CtrlCommandResult(false, "please enter a username.");
		
		if (((String)params.get("username")).contains(" "))
			return new CtrlCommandResult(false, "the username contains whitespaces.");
		
		if (((String)params.get("password")).equals(new String("")))
			return new CtrlCommandResult(false, "please enter a password.");
		
		if (!((String)params.get("password")).equals((String)params.get("passwordretyped")))
			return new CtrlCommandResult(false, "please check the password.");
		
		if (mysqlUserManager.existsMySQLUser((String)params.get("username")))
			return new CtrlCommandResult(false, "the username already exists in database.");

		if (mysqlUserManager.addUser(
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
	
	/**
	 * edit an existing MySQL database user.
	 * @param params
	 * @return CtrlCommandResult
	 */
	public CtrlCommandResult runCtrlCmdMysqlEditUser(Map params) {
		getMySQLUserManager();
		
		if (((String)params.get("username")).equals(new String("")))
			return new CtrlCommandResult(false, "please enter a username.");
		
		if (((String)params.get("username")).contains(" "))
			return new CtrlCommandResult(false, "the username contains whitespaces.");
		
		if (!mysqlUserManager.existsMySQLUser((String)params.get("username")))
			return new CtrlCommandResult(false, "the username doesn't exist in database.");

		if (((String)params.get("password")).equals(new String("")))
			return new CtrlCommandResult(false, "please enter a password.");
		
		if (!((String)params.get("password")).equals((String)params.get("passwordretyped")))
			return new CtrlCommandResult(false, "please check the password.");
		
		if (mysqlUserManager.editUser(
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
	
	/**
	 * delete an MySQL database user.
	 * @param params - username (user which should be deleted or ALL when all should be delted.)
	 * @param user - current logged in user - which does not get deleted.
	 * @return CtrlCommandResult
	 */
	public CtrlCommandResult runCtrlCmdMysqlDeleteUser(Map params, String user) {
		getMySQLUserManager();
		
		if (((String)params.get("username")).equals(new String("")))
			return new CtrlCommandResult(false, "please enter a username.");
		
		if (!mysqlUserManager.existsMySQLUser((String)params.get("username")))
			return new CtrlCommandResult(false, "the username does not exist in the database.");
		
		if (!((String)params.get("username")).equals(user)) {
			if (mysqlUserManager.deleteUser((String)params.get("username"))) {
				return new CtrlCommandResult(true, "user '" + (String)params.get("username") + "' successfully deleted.");
			} else {
				return new CtrlCommandResult(false, "user '" + (String)params.get("username") + "' not deleted.");
			}
		} else {
			return new CtrlCommandResult(false, "you can't delete yourself!");
		}
	}
	
	/**
	 * delete all MySQL database users except the one currenty logged in
	 * @param params
	 * @param user
	 * @return CtrlCommandResult
	 */
	public CtrlCommandResult runCtrlCmdMysqlDeleteAllUsers(Map params, String user) {
		getMySQLUserManager();
		
		if (mysqlUserManager.deleteAllUsers(user)) {
			return new CtrlCommandResult(true, "all users successfully deleted.");
		} else {
			return new CtrlCommandResult(false, "all users not deleted.");
		}
	}
	
	/* ############################################################################################ */
	/* ctrl-commands (import/export)																*/
	/* ############################################################################################ */
	
	/**
	 * import users from a xml source into the database
	 * @param params
	 * @return CtrlCommandResult
	 */
	public CtrlCommandResult runCtrlCmdMysqlImport(Map params) {
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

	/**
	 * processes a xml user source and adds the new users to the database.
	 * If a user with the same name already exists it skips the new one.
	 * @param newFile
	 * @return TRUE, when url was successfully imported.
	 */
	private boolean processUserFile(String newFile) {
		Set newUsers = new HashSet();		
		try {
			ProxyXmlConfig xml = new ProxyXmlConfig(new XMLConfig(newFile, false));
			for(Iterator iter = xml.getMultipleSubConfigs("user"); iter.hasNext(); ) {
				User user = parseUser((ProxyXmlConfig)iter.next());
				if (!mysqlUserManager.existsMySQLUser(user.getUserName())) {
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
	/* status-commands (user)																		*/
	/* ############################################################################################ */
	
	public void runStatusCmdMysqlUsers(XmlStringBuffer xb, Map params) throws RemoteException {
		getMySQLUserManager();
		
		xb.appendElement("mysql-users");
		
		for (int i = 1; i <= mysqlUserManager.getMySQLUserCount() / NUM_ROWS; i++) {
			xb.appendElement("page","num", i);
			xb.closeElement("page");
		}
		if (mysqlUserManager.getMySQLUserCount() % NUM_ROWS != 0) {
			xb.appendElement("page","num", (mysqlUserManager.getMySQLUserCount() / NUM_ROWS) + 1);
			xb.closeElement("page");
		}
		
		if (((String)params.get("username")) != null) {
			User user = mysqlUserManager.getMySQLUser((String)params.get("username"));
			if(user != null) xmlFormatUser(xb, user, false);
		} else {
			Iterator iterator = null;
			if ((String)params.get("pageNum") != null) {
				iterator = mysqlUserManager.getMySQLUserNames(new Integer((String)params.get("pageNum")).intValue() * NUM_ROWS, NUM_ROWS).iterator();				
			} else {
				iterator = mysqlUserManager.getMySQLUserNames().iterator();
			}
			while (iterator.hasNext()) xmlFormatUser(xb, mysqlUserManager.getMySQLUser((String)iterator.next()), false);
		}
		xb.closeElement("mysql-users");
	} 
	
	public void runStatusCmdMysqlAddUser(XmlStringBuffer xb, Map params) throws RemoteException {
		getMySQLUserManager();
		
		xb.appendElement("mysql-add-user");
		Iterator iterator = mysqlUserManager.getProfiles().iterator();
		while (iterator.hasNext()) xmlFormatProfile(xb, (Profile)iterator.next());
		xb.closeElement("mysql-add-user");
	} 
	
	public void runStatusCmdMysqlEditUser(XmlStringBuffer xb, Map params) throws RemoteException {
		getMySQLUserManager();
		
		xb.appendElement("mysql-edit-user");
		xmlFormatUser(xb, mysqlUserManager.getMySQLUser((String)params.get("username")),true);
		Iterator iterator = mysqlUserManager.getProfiles().iterator();
		while (iterator.hasNext()) xmlFormatProfile(xb, (Profile)iterator.next());
		xb.closeElement("mysql-edit-user");
	} 
	
	public void runStatusCmdMysqlImport(XmlStringBuffer xb, Map params) throws RemoteException {
		xb.appendElement("mysql-import");
		xb.closeElement("mysql-import");
	} 
	
	private void xmlFormatUser(XmlStringBuffer xb, User user, boolean includePassword) {
		xb.appendElement("user", "id", user.getId());
		xb.appendAttr("username", user.getUserName());
		if (includePassword) xb.appendAttr("password", user.getPassword());
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
			return new CtrlCommandResult(false, "the profilename contains whitespaces.");
		
		if (mysqlUserManager.existsProfile((String)params.get("profilename")))
			return new CtrlCommandResult(false, "the profile already exists in database.");
		
		if (mysqlUserManager.addProfile((String)params.get("profilename"))) {
			return new CtrlCommandResult(true, "profile successfully added to database.");
		} else {
			return new CtrlCommandResult(false, "profile not added to database.");
		}
		
	}
	
	/**
	 * edit Profile in the MySQL database.
	 * @param params
	 * @return CtrlCommandResult
	 */
	public CtrlCommandResult runCtrlCmdMysqlEditProfile(Map params) {
		getMySQLUserManager();
		
		if (((String)params.get("id")).equals(new String("")))
			return new CtrlCommandResult(false, "please enter a profil id.");
		
		if (((String)params.get("profilename")).equals(new String("")))
			return new CtrlCommandResult(false, "please enter a profilename.");
		
		if (((String)params.get("profilename")).contains(" "))
			return new CtrlCommandResult(false, "the name contains whitespaces.");
		
		if (!mysqlUserManager.existsProfile(new Integer((String)params.get("id")).intValue()))
			return new CtrlCommandResult(false, "the profile to edit does not exist in database.");
		
		if (mysqlUserManager.existsProfile((String)params.get("profilename")))
			return new CtrlCommandResult(false, "the profilename already exists in database.");
		
		if (mysqlUserManager.editProfile(new Integer((String)params.get("id")).intValue(), (String)params.get("profilename"))) {
			return new CtrlCommandResult(true, "profile successfully edited.");
		} else {
			return new CtrlCommandResult(false, "profile not edited.");
		}
	}
	
	/**
	 * delete profile from MySQL database.
	 * @param params - id
	 * @return CtrlCommandResult
	 */
	public CtrlCommandResult runCtrlCmdMysqlDeleteProfile(Map params) {
		getMySQLUserManager();
		
		if (((String)params.get("id")).equals(new String("")))
			return new CtrlCommandResult(false, "please enter a profile id.");
		
		if (!mysqlUserManager.existsProfile(new Integer((String)params.get("id")).intValue()))
			return new CtrlCommandResult(false, "the profile to delete does not exist in database.");

		if (mysqlUserManager.deleteProfile(new Integer((String)params.get("id")).intValue())) {
			return new CtrlCommandResult(true, "profile successfully deleted.");
		} else {
			return new CtrlCommandResult(false, "profile not deleted.");
		}
	}
	
	/**
	 * delete all profiles from MySQL database.
	 * @return CtrlCommandResult
	 */
	public CtrlCommandResult runCtrlCmdMysqlDeleteAllProfiles() {
		getMySQLUserManager();
		
		if (mysqlUserManager.deleteAllProfiles()) {
			return new CtrlCommandResult(true, "all profiles successfully deleted.");
		} else {
			return new CtrlCommandResult(false, "all profiles not deleted.");
		}
	}
	
	/* ############################################################################################ */
	/* status-commands (profile)																	*/
	/* ############################################################################################ */
	
	/**
	 * append all profiles to the xml string buffer
	 * @param xb
	 */
	public void runStatusCmdMysqlProfiles(XmlStringBuffer xb) throws RemoteException {
		getMySQLUserManager();
		
		xb.appendElement("mysql-profiles");
		Iterator iterator = mysqlUserManager.getProfiles().iterator();
		while (iterator.hasNext()) xmlFormatProfile(xb, (Profile)iterator.next());
		xb.closeElement("mysql-profiles");
	} 
	
	/**
	 * append a formated profile to the xml string buffer
	 * @param xb
	 * @param profile
	 */
	private void xmlFormatProfile(XmlStringBuffer xb, Profile profile) {
		xb.appendElement("profile", "id", profile.getId());
		xb.appendAttr("profilename", profile.getProfileName());
		xb.endElement(true);
	}
	
	/* ############################################################################################ */
	/* ProxyPlugin																					*/
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
