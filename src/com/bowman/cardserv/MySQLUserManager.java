package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.MySQLConstants;
import com.bowman.cardserv.mysql.UserCacheManager;
import com.bowman.cardserv.mysql.User;
import com.bowman.cardserv.util.ProxyLogger;
import com.bowman.cardserv.util.ProxyXmlConfig;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A simple mysql usermanager to get the user informations directly from a mysql 
 * database without having to load them over external xml sources.
 * For the database communication it uses the MySQL Connectors/J Driver (mysql-connector-java-5.1.XX-bin.jar)
 * found on "http://dev.mysql.com/downloads/connector/j/".
 * 
 * @author DonCarlo
 * @since 04.12.2010
 */
public class MySQLUserManager extends XmlUserManager {

	private static final String DEFAULT_DBHOST = "127.0.0.1";
	private static final int DEFAULT_DBPORT = 3306;
	private static final String DEFAULT_DBNAME = "cardserverproxy";
		
	private ProxyLogger logger = null;
	private UserCacheManager cacheManager = null;
	
	public MySQLUserManager() {
	    logger = ProxyLogger.getLabeledLogger(getClass().getName());
	}

	public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
		try {
			super.configUpdated(xml);
		} catch (ConfigException e) {
			logger.throwing(e);
		}
		
		if (cacheManager != null)
			cacheManager.interrupt();

		cacheManager = new UserCacheManager(
				xml.getSubConfig("mysql-database").getStringValue("dbhost", DEFAULT_DBHOST),
				xml.getSubConfig("mysql-database").getPortValue("dbport", DEFAULT_DBPORT),
				xml.getSubConfig("mysql-database").getStringValue("dbname", DEFAULT_DBNAME),
				xml.getSubConfig("mysql-database").getStringValue("dbuser"),
				xml.getSubConfig("mysql-database").getStringValue("dbpassword")
		);
	}
	
	public String[] getUserNames() {
	    List userNames = getMySQLUserNames();
		// now run through the rest (simple and xml usermanager) and merge them
		String [] tmp = super.getUserNames();
		for (int i = 0; i < tmp.length; i++) {
			if (!userNames.contains(tmp[i])) {
				userNames.add(tmp[i]);
			}
		}
		Collections.sort(userNames);
		return (String[])userNames.toArray(new String[userNames.size()]);
	}

	public String getPassword(String user) {
		if(super.getPassword(user) != null) {
			return super.getPassword(user);
		} else {
			return cacheManager.getUser(user).getPassword();
		}
	}

	public String getUserName(String user) {
		if(super.getPassword(user) != null) {
			return super.getUserName(user);
		} else {
			return cacheManager.getUser(user).getUserName();
		}
	}

	public boolean authenticate(String user, String pass) {
		if(super.getPassword(user) != null) {
			return super.authenticate(user, pass);
		} else {
			return (cacheManager.getUser(user) != null) && cacheManager.getUser(user).getPassword().equals(pass);
		}
	}

	public int getMaxConnections(String user) {
		if(super.getPassword(user) != null) {
			return super.getMaxConnections(user);
		} else {
			return cacheManager.getUser(user).getMaxConnections();
		}
	}

	public String getIpMask(String user) {
		if(super.getPassword(user) != null) {
			return super.getIpMask(user);
		} else {
			return cacheManager.getUser(user).getIpMask().equals(new String("")) ? new String("*") : cacheManager.getUser(user).getIpMask();
		}
	}

	public String getEmailAddress(String user) {
		if(super.getPassword(user) != null) {
			return super.getEmailAddress(user);
		} else {
			return cacheManager.getUser(user).getEmail();
		}
	}

	public String getDisplayName(String user) {
		if(super.getPassword(user) != null) {
			return super.getDisplayName(user);
		} else {
			User us = cacheManager.getUser(user);
			if (us.getDisplayName().equals(new String())) {
				return us.getUserName();
			} else
				return us.getDisplayName();
		}
	}

	public Set getAllowedProfiles(String user) {
		if(super.getPassword(user) != null) {
			return super.getAllowedProfiles(user);
		} else {
			return cacheManager.getUser(user).getAllowedProfiles();
		}
	}

	public boolean isEnabled(String user) {
		if(super.getPassword(user) != null) {
			return super.isEnabled(user);
		} else {
			return cacheManager.getUser(user).isEnabled();
		}
	}

	public boolean isAdmin(String user) {
		if(super.getPassword(user) != null) {
			return super.isAdmin(user);
		} else {
			return cacheManager.getUser(user).isAdmin();
		}
	}

	public boolean exists(String user) {
		if(super.getPassword(user) != null) {
			return super.exists(user);
		} else {
			return cacheManager.getUser(user) != null;
		}
	}
	
	public boolean isMapExcluded(String user) {
		if(super.getPassword(user) != null) {
			return super.isMapExcluded(user);
		} else {
			return cacheManager.getUser(user).isMapExcluded();
		}
	}

	public boolean isDebug(String user) {
		if(super.getPassword(user) != null) {
			return super.isDebug(user);
		} else {
			return cacheManager.getUser(user).isDebug();
		}
	}

	public void setDebug(String user, boolean debug) {
		if(super.getPassword(user) != null) {
			super.setDebug(user, debug);
		} else {
			cacheManager.setDebug(user, debug);
		}
	}

	public int getUserCount() {
		return getUserNames().length;
	}
	
	public Set getAllowedServices(String user, String profile) {
		if(super.getPassword(user) != null) {
			return super.getAllowedServices(user, profile);
		} else {
			return null;
		}
	}

	public Set getBlockedServices(String user, String profile) {
		if(super.getPassword(user) != null) {
			return super.getBlockedServices(user, profile);
		} else {
			return null;
		}
	}

	public Set getAllowedConnectors(String user) {
		if(super.getPassword(user) != null) {
			return super.getAllowedConnectors(user);
		} else {
			return null;
		}
	}

	public int getAllowedEcmRate(String user) {
		if(super.getPassword(user) != null) {
			return super.getAllowedEcmRate(user);
		} else {
			return -1;
		}
	}
	
	/* ############################################################################################ */
	/* add/edit/delete MySQL users																	*/
	/* ############################################################################################ */
	
	/**
	 * returns all usernames which are stored in the database 
	 * limited with skipRows and numRows as a List.
	 * @param skipRows
	 * @param numRows
	 * @return usersnames
	 */
	public List getMySQLUserNames(int skipRows, int numRows) {
		return cacheManager.getUserNames(skipRows, numRows);
	}
	
	/**
	 * returns all usernames which are stored in the database as a List.
	 * @return all usersnames
	 */
	public List getMySQLUserNames() {
		return getMySQLUserNames(MySQLConstants.DEFAULT_SKIP_ROWS, MySQLConstants.DEFAULT_NUM_ROWS);
	}
	
	/**
	 * Returns the number of total Users in database
	 * @return user count
	 */
	public int getMySQLUserCount() {
		return getMySQLUserNames().size();
	}
	
	/**
	 * Returns user
	 * @param userName
	 * @return User
	 */
	public User getMySQLUser(String userName) {
		return cacheManager.getUser(userName);
	}
	
	/**
	 * tests whether the user exists.
	 * @param userName
	 * @return TRUE, when specified user exists
	 */
	public boolean existsMySQLUser(String userName) {
		return cacheManager.getUser(userName) != null;
	}

	/**
	 * add a new user to the database.
	 */
	public boolean addUser(String username, String password, String displayname, String ipmask,
			int maxconnections, boolean enabled,  boolean debug, boolean admin, 
			String mail, boolean mapexcluded, Set allowedProfileIds) {
		return cacheManager.addUser(
				username, password, displayname, ipmask, maxconnections, enabled, 
				debug, admin, mail, mapexcluded, allowedProfileIds
		);
	}
	
	/**
	 * edit an existing user entry by passing the new values.
	 */
	public boolean editUser(int id, String username, String password, String displayname, String ipmask, 
			int maxconnections, boolean enabled, boolean debug, boolean admin, String mail, 
			boolean mapexcluded, Set allowedProfileIds) {
		return cacheManager.editUser(id, username, password, displayname, ipmask, maxconnections, 
				enabled, debug, admin, mail, mapexcluded, allowedProfileIds);
	}
	
	/**
	 * delete specified user from the MySQL database.
	 * @param  - user to delete.
	 */
	public boolean deleteUser(String username) {
		return cacheManager.deleteUser(username);
	}
	
	/**
	 * delete all users from the database.
	 * @param  - the user which should not be deleted.
	 *        This may be because the user is currently logged in and
	 *        therefore shouldn't be deleted.
	 */
	public boolean deleteAllUsers(String skipUserName) {
		return cacheManager.deleteAllUsers(skipUserName);
	}
	
	/**
	 * Little helper to import a Set of user objects with profilen-ames
	 * instead of profile-ids. Profiles not in DB will be added to DB.
	 * @param users - set of users to import
	 * @return TRUE if import was successfull
	 */
	public boolean importUsers(Set users) {
		return cacheManager.importUsers(users);
	}
	
	/**
	 * tests whether the user with the specified username exists in the MySQL database.
	 * @param id - the user to check
	 * @return TRUE, when the user entry is in database
	 */
	/**public boolean existsUserInDatabase(String userName) {
		return cacheManager.getUser(userName) != null;
	}*/
	
	/* ############################################################################################ */
	/* profiles																						*/
	/* ############################################################################################ */

	/**
	 * Returns all profiles as a list
	 * @return all profiles
	 */
	public List getProfiles() {
		return cacheManager.getProfiles();
	}

	/**
	 * tests whether the profile exists.
	 * @param id
	 * @return TRUE, when specified profile exists
	 */
	public boolean existsProfile(int id) {
		return cacheManager.getProfile(id) != null;
	}

	/**
	 * tests whether the profile exists.
	 * @param profileName
	 * @return TRUE, when specified profile exists
	 */
	public boolean existsProfile(String profileName) {
		return cacheManager.getProfile(profileName) != null;
	}

	/**
	 * add a new profile to the mysql database.
	 * @param profileName - profile to add
	 * @return TRUE when adding profile was successfull
	 */
	public boolean addProfile(String profileName) {
		return cacheManager.addProfile(profileName);
	}

	/**
	 * edit an existing profile in the mysql database.
	 * @param id - profile to edit
	 * @param profileName - new profilename
	 * @return TRUE when editing profile was successfull
	 */
	public boolean editProfile(int id, String profileName) {
		return cacheManager.editProfile(id, profileName);
	}

	/**
	 * delete specified profile from the MySQL database.
	 * @param id - profile to delete
	 * @return TRUE when profile was successfully deleted.
	 */
	public boolean deleteProfile(int id) {
		return cacheManager.deleteProfile(id);
	}

	/**
	 * delete all profiles from the MySQL database.
	 * @return TRUE when all profiles were successfully deleted.
	 */
	public boolean deleteAllProfiles() {
		return cacheManager.deleteAllProfiles();
	}

	/* ############################################################################################ */
	/* database informations																		*/
	/* ############################################################################################ */

	/**
	 * the hostname or ip address the MySQL server is reached. 
	 * @return host
	 */
	public String getDatabaseHost() {
		return cacheManager.getDatabaseHost();
	}

	/**
	 * the database name which is used for the tables.
	 * @return database name
	 */
	public String getDatabaseName() {
		return cacheManager.getDatabaseName();
	}

	/**
	 * the port the MySQL database listens on.
	 * @return port
	 */
	public int getDatabasePort() {
		return cacheManager.getDatabasePort();
	}

	/**
	 * the username used to connect to the database.
	 * @return username
	 */
	public String getDatabaseUser() {
		return cacheManager.getDatabaseUser();
	}

}
