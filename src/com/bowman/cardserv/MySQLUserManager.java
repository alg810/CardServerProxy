package com.bowman.cardserv;

import com.bowman.cardserv.mysql.CacheManager;
import com.bowman.cardserv.mysql.User;
import com.bowman.cardserv.util.ProxyLogger;
import com.bowman.cardserv.util.ProxyXmlConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
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
	private CacheManager cacheManager = null;
	
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

		cacheManager = new CacheManager(
				xml.getSubConfig("mysql-database").getStringValue("dbhost", DEFAULT_DBHOST),
				xml.getSubConfig("mysql-database").getPortValue("dbport", DEFAULT_DBPORT),
				xml.getSubConfig("mysql-database").getStringValue("dbname", DEFAULT_DBNAME),
				xml.getSubConfig("mysql-database").getStringValue("dbuser"),
				xml.getSubConfig("mysql-database").getStringValue("dbpassword")
		);
	}
	
	public String[] getUserNames() {
	    ArrayList userNames = cacheManager.getUserNames();
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
			return cacheManager.getUser(user).getUsername();
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
			return cacheManager.getUser(user).getIpMask();
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
				return us.getUsername();
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
			cacheManager.setUserDebug(user, debug);
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
	/* add/edit/delete MySQL users                                                                  */
	/* ############################################################################################ */
	
	/**
	 * add a new user to the database.
	 */
	public void addUserToDB(String username, String password, String displayname, String ipmask,
			int maxconnections, boolean enabled,  boolean debug, boolean admin, 
			String mail, boolean mapexcluded, String allowedProfiles) {
		cacheManager.addUserToDB(
				username, password, displayname, ipmask, maxconnections, enabled, 
				debug, admin, mail, mapexcluded, allowedProfiles
		);
	}
	
	/**
	 * add a new user to the database.
	 * @param user - User object
	 */
	public void addUserToDB(User user) {
		String profiles = "";
		Iterator iterator = user.getAllowedProfiles().iterator();
		while(iterator.hasNext()){
			profiles += (String) iterator.next() + " ";
		}
		addUserToDB(user.getUsername(), user.getPassword(), user.getDisplayName(), user.getIpMask(),
				user.getMaxConnections(), user.isEnabled(),  user.isDebug(), user.isAdmin(), 
				user.getEmail(), user.isMapExcluded(), profiles);
	}
	
	/**
	 * edit an existing user entry by passing the new values.
	 */
	public void editUserInDB(String username, String password, String displayname, String ipmask, 
			int maxconnections, boolean enabled, boolean debug, boolean admin, String mail, 
			boolean mapexcluded, String allowedProfiles) {
		cacheManager.editUserInDB(username, password, displayname, ipmask, maxconnections, 
				enabled, debug, admin, mail, mapexcluded, allowedProfiles
		);
	}
	
	/**
	 * delete all users from the database.
	 * @param username - the user which should not be deleted.
	 *        This may be because the user is currently logged in and
	 *        therefore shouldn't be deleted.
	 */
	public void deleteAllUsersFromDB(String username) {
		cacheManager.deleteAllUsersFromDB(username);
	}
	
	/**
	 * delete specified user from the MySQL database.
	 * @param username - user to delete.
	 */
	public void deleteUserFromDB(String username) {
		cacheManager.deleteUserFromDB(username);
	}
	
	/**
	 * returns all usernames which are stored in the database as a string array.
	 * @return all usersnames in the database
	 */
	public String[] getMySQLUserNames() {
	    ArrayList userNames = cacheManager.getUserNames();
		Collections.sort(userNames);
		return (String[])userNames.toArray(new String[userNames.size()]);
	}
		
	/**
	 * tests whether the user with the specified username exists in the MySQL database.
	 * @param username - the username to check
	 * @return TRUE, when the user entry is in database
	 */
	public boolean existsUserInDatabase(String username) {
		return cacheManager.getUser(username) != null;
	}
	
	/**
	 * returns the user
	 * @param username
	 * @return User
	 */
	public User getMySQLUser(String username) {
		return cacheManager.getUser(username);
	}
	
	/* ############################################################################################ */
	/* database informations                                                                        */
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
	
	/* ############################################################################################ */
	/* profiles                                                                                     */
	/* ############################################################################################ */
	
	/**
	 * add a new profile to the mysql database.
	 * @param profileName - profile to add
	 */
	public void addProfileToDB(String profileName) {
		cacheManager.addProfileToDB(profileName);
	}
	
	/**
	 * delete all profiles from the MySQL database.
	 */
	public void deleteAllProfilesFromDB() {
		cacheManager.deleteAllProfilesFromDB();
	}
	
	/**
	 * delete specified profile from the MySQL database.
	 * @param profileName - profile to delete
	 */
	public void deleteProfileFromDB(String profileName) {
		cacheManager.deleteProfileFromDB(profileName);
	}
	
	/**
	 * returns all profile names as a string array.
	 * @return profile names
	 */
	public String[] getProfileNames() {
	    ArrayList profileNames = cacheManager.getProfileNames();
		Collections.sort(profileNames);
		return (String[])profileNames.toArray(new String[profileNames.size()]);
	}
	
	/**
	 * tests whether the profile exists in the MySQL database.
	 * @param profileName
	 * @return TRUE, when specified profile exists in database
	 */
	public boolean existsProfileInDatabase(String profileName) {
		return cacheManager.getProfile(profileName) != null;
	}
	
}
