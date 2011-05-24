package com.bowman.cardserv.mysql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import com.bowman.cardserv.ConfigException;
import com.bowman.cardserv.interfaces.MySQLConstants;
import com.bowman.cardserv.util.ProxyLogger;

/**
 * The CacheManager is currently a simple "Read-Ahead" cache. Which means when a user connected once
 * the user and profile informations will be get from the database and loaded into the cache. As long as the 
 * informations are accessed a background thread refreshes the informations asynchronly. 
 *
 * This cache is not ment to keep all database users in a local cache. It is used to reduce the amount 
 * of database accesses by keeping the informations for a short periode of time in a local cache. 
 * 
 * @author DonCarlo
 * @since 14.12.2010
 */
public class UserCacheManager extends Thread {

	private Map userCache = new HashMap();

	private final long cleaningInterval = 5000;
	private ProxyLogger logger = null;
	private ConnectionPoolManager connectionPoolManager = null;


	public UserCacheManager(String databaseHost, int databasePort, String databaseName,
			String databaseUser, String databasePassword) throws ConfigException {
		this.logger = ProxyLogger.getLabeledLogger(getClass().getName());
		this.connectionPoolManager = new ConnectionPoolManager(
				databaseHost, databasePort, databaseName, databaseUser, databasePassword);

		// database prerequirements
		if (!existsUserTable()) {
			logger.info("creating new user table.");
			createUserTable();
		}

		if (!existsProfileTable()) {
			logger.info("creating new profile table.");
			createProfileTable();
		}

		if (!existsUsersHasProfilesTable()) {
			logger.info("creating new users_has_profiles table.");
			createUsersHasProfilesTable();
		}

		this.setName("CacheManagerThread");
		this.setPriority(MIN_PRIORITY);
		this.start();
	}

	/* ############################################################################################ */
	/* database prerequisites																		*/
	/* ############################################################################################ */

	/**
	 * create the "user" table in the mysql database.
	 */
	private void createUserTable() {
		createTable(0);
	}

	/**
	 * create the "profile" table in the mysql database.
	 */
	private void createProfileTable() {
		createTable(1);
	}

	/**
	 * create the "users_has_profiles" table in the mysql datbase.
	 */
	private void createUsersHasProfilesTable() {
		// the user and profile table must exist before this one can be created.
		if (existsUserTable() && existsProfileTable()) {
			createTable(2);
		}
	}

	/**
	 * create table
	 * @param table - 0 = users; 1 = profiles; 2 = users_has_profiles
	 */
	private void createTable(int table) {
		MySQLConnection mySQLConnection = null;

		try {
			mySQLConnection = connectionPoolManager.getMySQLConnection();
			switch (table) {
			case 0 :
				mySQLConnection.createUserTable();
				break;
			case 1 :
				mySQLConnection.createProfileTable();
				break;
			case 2 :
				mySQLConnection.createUsersHasProfilesTable();
				break;
			}
		} finally {
			mySQLConnection.returnConnection();
			connectionPoolManager.returnMySQLConnection(mySQLConnection);
		}
	}

	/**
	 * tests whether the "user" table exists in the mysql database.
	 * @return TRUE, when user table exists.
	 */
	private boolean existsUserTable() {
		return existsTable(0);
	}

	/**
	 * tests whether the "profile" table exists in the mysql database.
	 * @return TRUE, when profile table exists.
	 */
	private boolean existsProfileTable() {
		return existsTable(1);
	}

	/**
	 * tests whether the "users_has_profiles" table exists in the mysql database.
	 * @return TRUE, when users_has_profiles table exists.
	 */
	private boolean existsUsersHasProfilesTable() {
		return existsTable(2);
	}

	/**
	 * tests whether a table exists.
	 * @param table - 0 = users; 1 = profiles; 2 = users_has_profiles
	 * @return TRUE, when table exists.
	 */
	private boolean existsTable(int table) {
		boolean result = false;
		MySQLConnection mySQLConnection = null;

		try {
			mySQLConnection = connectionPoolManager.getMySQLConnection();
			switch (table) {
			case 0 :
				result = mySQLConnection.existsUserTable();
				break;
			case 1 :
				result = mySQLConnection.existsProfileTable();
				break;
			case 2 :
				result = mySQLConnection.existsUsersHasProfilesTable();
				break;
			}
		} finally {
			mySQLConnection.returnConnection();
			connectionPoolManager.returnMySQLConnection(mySQLConnection);
		}
		return result;
	}

	/* ############################################################################################ */
	/* database informations																		*/
	/* ############################################################################################ */

	/**
	 * the hostname or ip address the MySQL server is reached. 
	 * @return host
	 */
	public String getDatabaseHost() {
		return connectionPoolManager.getDataSource().getServerName();
	}

	/**
	 * the database name which is used for the tables.
	 * @return database name
	 */
	public String getDatabaseName() {
		return connectionPoolManager.getDataSource().getDatabaseName();
	}

	/**
	 * the port the MySQL database listens on.
	 * @return port
	 */
	public int getDatabasePort() {
		return connectionPoolManager.getDataSource().getPort();
	}

	/**
	 * the username used to connect to the database.
	 * @return username
	 */
	public String getDatabaseUser() {
		return connectionPoolManager.getDataSource().getUser();
	}

	private Set stringToSet(String tokens) throws SQLException {
		Set set = new HashSet();
		for(StringTokenizer st = new StringTokenizer(tokens); st.hasMoreTokens(); ) {
			set.add(st.nextToken());
		}
		return set;
	}
	
	/* ############################################################################################ */
	/* users database operations																	*/
	/* ############################################################################################ */

	/**
	 * if the user information are not already in the cache, it must be fetched from the database.
	 * This function gets all informations for the given user with only one database statement, parses
	 * them from the returned resultSet, makes an User object out of it and puts it into the local cache.
	 * When there is no data for the passed user then nothing will be done.
	 * 
	 * @param username - describes which data should be get from database.
	 * @return User object - if the user does not exist in database null will be returned.
	 */
	private User getUserFromDB(String userName) {
		ResultSet resultSet = null;
		MySQLConnection mySQLConnection = null;
		User user = null;

		try {
			mySQLConnection = connectionPoolManager.getMySQLConnection();
			if (mySQLConnection.getUser(userName)) {
				resultSet = mySQLConnection.getResultSet();
				if (resultSet.next() && resultSet.getString(MySQLConstants.DBC_USERS_USERNAME) != null) {
					user = new User(
						resultSet.getInt(MySQLConstants.DBC_USERS_ID),
						resultSet.getString(MySQLConstants.DBC_USERS_USERNAME),
						resultSet.getString(MySQLConstants.DBC_USERS_PASSWORD),
						resultSet.getString(MySQLConstants.DBC_USERS_DISPLAYNAME),
						resultSet.getInt(MySQLConstants.DBC_USERS_MAXCONNECTIONS),
						resultSet.getString(MySQLConstants.DBC_USERS_IPMASK),
						resultSet.getString(MySQLConstants.DBC_USERS_MAIL),
						resultSet.getString(MySQLConstants.DBC_USERS_PROFILES) == null ? Collections.EMPTY_SET : 
							stringToSet(resultSet.getString(MySQLConstants.DBC_USERS_PROFILES)),
						resultSet.getBoolean(MySQLConstants.DBC_USERS_ENABLED),
						resultSet.getBoolean(MySQLConstants.DBC_USERS_ADMIN),
						resultSet.getBoolean(MySQLConstants.DBC_USERS_DEBUG),
						resultSet.getBoolean(MySQLConstants.DBC_USERS_MAPEXCLUDE)
					);
				}
			}
		} catch (SQLException e) {
			logger.warning("(getUserFromDB) Failed to parse ResultSet: " + e);
		} finally {
			mySQLConnection.returnConnection();
			connectionPoolManager.returnMySQLConnection(mySQLConnection);
		}
		return user;
	}

	/**
	 * add an new user to the MySQL database.
	 * @return id for the added profile - if adding failed -1 gets returned
	 */
	private int addUserToDB(String userName, String password, String displayName, String ipmask,
			int maxConnections, boolean enabled, boolean debug, boolean admin, 
			String mail, boolean mapExcluded) {
		ResultSet resultSet = null;
		MySQLConnection mySQLConnection = null;
		int id = -1;

		try {
			mySQLConnection = connectionPoolManager.getMySQLConnection();
			if (mySQLConnection.addUser(userName, password, displayName, ipmask, maxConnections, 
					enabled, debug, admin, mail, mapExcluded)) {
				resultSet = mySQLConnection.getResultSet();
				if (resultSet.next()) {
					id = resultSet.getInt(1);
				}
			}
		} catch (SQLException e) {
			logger.warning("(addUserToDB) Failed to parse ResultSet: " + e);
		} finally {
			mySQLConnection.returnConnection();
			connectionPoolManager.returnMySQLConnection(mySQLConnection);
		}
		return id;
	}

	/**
	 * edit an existing MySQL database user.
	 * @return TRUE when user was successfully edited
	 */
	private boolean editUserInDB(int id, String userName, String password, String displayName, String ipmask,
			int maxConnections, boolean enabled, boolean debug, boolean admin, 
			String mail, boolean mapExcluded) {
		MySQLConnection mySQLConnection = null;
		boolean result = false;
		
		try {
			mySQLConnection = connectionPoolManager.getMySQLConnection();
			result = mySQLConnection.editUser(
					id, 
					userName, 
					password, 
					displayName, 
					ipmask, 
					maxConnections, 
					enabled, 
					debug, 
					admin, 
					mail, 
					mapExcluded
				);
		} finally {
			mySQLConnection.returnConnection();
			connectionPoolManager.returnMySQLConnection(mySQLConnection);
		}
		return result;
	}

	/**
	 * delete specified user from the database and the local cache.
	 * @param username - the user to delete.
	 * @return TRUE when user was successfully deleted
	 */
	private boolean deleteUserFromDB(String userName) {
		MySQLConnection mySQLConnection = null;
		boolean result = false;
		
		try {
			mySQLConnection = connectionPoolManager.getMySQLConnection();
			result = mySQLConnection.deleteUserByName(userName);
		} finally {
			mySQLConnection.returnConnection();
			connectionPoolManager.returnMySQLConnection(mySQLConnection);
		}
		return result;
	}

	/**
	 * delete all users from the database.
	 * @param skipUserName - the user which should not be deleted.
	 * @return TRUE when all users except the specified one were successfully deleted
	 */
	private boolean deleteAllUsersFromDB(String skipUserName) {
		MySQLConnection mySQLConnection = null;
		boolean result = false;
		
		try {
			mySQLConnection = connectionPoolManager.getMySQLConnection();
			result = mySQLConnection.deleteAllUsers(skipUserName);
		} finally {
			mySQLConnection.returnConnection();
			connectionPoolManager.returnMySQLConnection(mySQLConnection);
		}
		return result;
	}

	/**
	 * set user debug value in the database.
	 * @param userName - specifies which user gets changed
	 * @param debug - enable/disable debug
	 * @return TRUE when updating the user was successfull
	 */
	private boolean setUserDebugInDB(String userName, boolean debug) {
		MySQLConnection mySQLConnection = null;
		boolean result = false;
		
		try {
			mySQLConnection = connectionPoolManager.getMySQLConnection();
			result = mySQLConnection.setUserDebug(userName, debug);
		} finally {
			mySQLConnection.returnConnection();
			connectionPoolManager.returnMySQLConnection(mySQLConnection);
		}
		return result;
	}

	/**
	 * returns all userNames which are stored in the database as an sorted ArrayList.
	 * @return all usernames
	 */
	private List getUserNamesFromDB(int skipRows, int numRows) {
		List userNames = null;
		ResultSet resultSet = null;
		MySQLConnection mySQLConnection = null;
		
		try {
			mySQLConnection = connectionPoolManager.getMySQLConnection();
			if (mySQLConnection.getUserNames(skipRows, numRows)) {
				userNames = new  ArrayList();
				resultSet = mySQLConnection.getResultSet();
				while (resultSet.next()) userNames.add(resultSet.getString(MySQLConstants.DBC_USERS_USERNAME));
			}
		} catch (SQLException e) {
			logger.warning("(getUserNamesFromDB) Failed to parse ResultSet: " + e);
		} finally {
			mySQLConnection.returnConnection();
			connectionPoolManager.returnMySQLConnection(mySQLConnection);
		}

		return userNames;
	}

	/**
	 * add profile assignments to user.
	 * @param userId - defines the user
	 * @param profileIds - profiles to add
	 * @return TRUE when adding the user profiles was successfull
	 */
	private boolean addUserProfilesToDB	(int userId, Set profileIds) {
		MySQLConnection mySQLConnection = null;
		boolean result = false;
		try {
			mySQLConnection = connectionPoolManager.getMySQLConnection();
			result = mySQLConnection.addUserProfiles(userId, profileIds);
		} finally {
			mySQLConnection.returnConnection();
			connectionPoolManager.returnMySQLConnection(mySQLConnection);
		}
		return result;
	}
	
	/**
	 * delete all profile assignments to the user.
	 * @param userId
	 * @return TRUE when deleting the userprofiles was successfull
	 */
	private boolean deleteUserProfilesFromDB (int userId) {
		MySQLConnection mySQLConnection = null;
		boolean result = false;
		try {
			mySQLConnection = connectionPoolManager.getMySQLConnection();
			result = mySQLConnection.deleteUserProfiles(userId);
		} finally {
			mySQLConnection.returnConnection();
			connectionPoolManager.returnMySQLConnection(mySQLConnection);
		}
		return result;
	}
	
	/* ############################################################################################ */
	/* profiles database operations																	*/
	/* ############################################################################################ */
	
	/**
	 * Return profile specified through id from database.
	 * @param id - profileName (String) or id (Integer)
	 * @return wanted profile
	 */
	private Profile getProfileFromDB(Object id) {
		Profile profile = null;
		ResultSet resultSet = null;
		MySQLConnection mySQLConnection = null;

		try {
			mySQLConnection = connectionPoolManager.getMySQLConnection();
			if (id instanceof Integer ? mySQLConnection.getProfile(((Integer)id).intValue()) : 
				mySQLConnection.getProfile((String)id)) {
				resultSet = mySQLConnection.getResultSet();
				if (resultSet.next()) {
					profile = new Profile(
						resultSet.getInt(MySQLConstants.DBC_PROFILES_ID),
						resultSet.getString(MySQLConstants.DBC_PROFILES_PROFILENAME)
					);
				}
			}
		} catch (SQLException e) {
			logger.warning("(getProfileFromDB) Failed to parse ResultSet: " + e);
		} finally {
			mySQLConnection.returnConnection();
			connectionPoolManager.returnMySQLConnection(mySQLConnection);
		}
		return profile;
	}
	
	/**
	 * Return all profiles from database as a List containing Profiles.
	 * @return all profiles
	 */
	private List getProfilesFromDB() {
		List profiles = null;
		ResultSet resultSet = null;
		MySQLConnection mySQLConnection = null;

		try {
			mySQLConnection = connectionPoolManager.getMySQLConnection();
			if (mySQLConnection.getProfiles()) {
				profiles = new  ArrayList();
				resultSet = mySQLConnection.getResultSet();
				while (resultSet.next()) {
					profiles.add(
						new Profile(
							resultSet.getInt(MySQLConstants.DBC_PROFILES_ID),
							resultSet.getString(MySQLConstants.DBC_PROFILES_PROFILENAME)
						)
					);
				}
			}
		} catch (SQLException e) {
			logger.warning("(getProfilesFromDB) Failed to parse ResultSet: " + e);
		} finally {
			mySQLConnection.returnConnection();
			connectionPoolManager.returnMySQLConnection(mySQLConnection);
		}
		return profiles;
	}
	
	/**
	 * add a new profile to the MySQL database.
	 * @param profileName - profile to add
	 * @return id for the added profile - if adding failed -1 gets returned
	 */
	private int addProfileToDB(String profileName) {
		ResultSet resultSet = null;
		MySQLConnection mySQLConnection = null;
		int id = -1;
		
		try {
			mySQLConnection = connectionPoolManager.getMySQLConnection();
			if (mySQLConnection.addProfile(profileName)) {
				resultSet = mySQLConnection.getResultSet();
				if (resultSet.next()) {
					id = resultSet.getInt(1);
				}
			}
		} catch (SQLException e) {
			logger.warning("(addProfileToDB) Failed to parse ResultSet: " + e);
		} finally {
			mySQLConnection.returnConnection();
			connectionPoolManager.returnMySQLConnection(mySQLConnection);
		}
		return id;
	}

	/**
	 * edit an existing profile in the MySQL database.
	 * @param id - defines the profilge to edit
	 * @param profileName - new profile name
	 * @return TRUE when profile was successfully edited
	 */
	private boolean editProfileInDB(int id, String profileName) {
		MySQLConnection mySQLConnection = null;
		boolean result = false;

		try {
			mySQLConnection = connectionPoolManager.getMySQLConnection();
			result = mySQLConnection.editProfile(id, profileName);
		} finally {
			mySQLConnection.returnConnection();
			connectionPoolManager.returnMySQLConnection(mySQLConnection);
		}
		return result;
	}

	/**
	 * delete specified profile from the MySQL database.
	 * @param id - profile to delete
	 * @return TRUE when profile was successfully deleted
	 */
	private boolean deleteProfileFromDB(int id) {
		MySQLConnection mySQLConnection = null;
		boolean result = false;
		
		try {
			mySQLConnection = connectionPoolManager.getMySQLConnection();
			result = mySQLConnection.deleteProfile(id);
		} finally {
			mySQLConnection.returnConnection();
			connectionPoolManager.returnMySQLConnection(mySQLConnection);
		}
		return result;
	}

	/**
	 * delete all profiles from the MySQL database.
	 * @return TRUE when all profiles were successfully deleted
	 */
	private boolean deleteAllProfilesFromDB() {
		MySQLConnection mySQLConnection = null;
		boolean result = false;
		
		try {
			mySQLConnection = connectionPoolManager.getMySQLConnection();
			result = mySQLConnection.deleteAllProfiles();
		} finally {
			mySQLConnection.returnConnection();
			connectionPoolManager.returnMySQLConnection(mySQLConnection);
		}
		return result;
	}

	/* ############################################################################################ */
	/* cache operations																				*/
	/* ############################################################################################ */

	/**
	 * returns the User object out of the local cache.
	 * @param userName - specifies which user to get.
	 * @return User - when not found null
	 */
	private User getUserFromCache(String userName) {
		synchronized(userCache) {
			if (userCache.containsKey(userName))
				return (User)((CacheEntry) userCache.get(userName)).getData();
		}
		return null;
	}
	
	/**
	 * add the specified user to the cache by makeing a cache object out of it.
	 * @param user - user to add to the cache.
	 */
	private void putUserIntoCache(User user) {
		CacheEntry cacheUser = new CacheEntry(user);
		synchronized(userCache) {
			userCache.put(user.getUserName(), cacheUser);
		}
	}

	/**
	 * delete user from the cache.
	 * @param userName
	 */
	private boolean deleteUserFromCache(String userName) {
		synchronized(userCache) {
			if (userCache.containsKey(userName)) {
				userCache.remove(userName);
				return true;
			} else return false;
		}
	}
	
	/**
	 * when a cached object is expired it gets removed. When its still used
	 * and needs a refresh it gets updated asynchronly. This function gets called
	 * every "cleaningInterval" seconds to keep the caches updated.
	 */
	private void updateCacheEntries() {
		synchronized (userCache) {
			Iterator iterator = userCache.keySet().iterator();
			while (iterator.hasNext()) {
				CacheEntry cacheUser = (CacheEntry) userCache.get(iterator.next());
				if (cacheUser.needsRefresh()) {
					cacheUser.setData(getUserFromDB(((User)cacheUser.getData()).getUserName()));
					logger.fine("(updateCacheEntries) refreshed user: " + ((User)cacheUser.getData()).getUserName() + " from cache!");
				} else if (cacheUser.isExpired()) {
					iterator.remove();
					logger.fine("(updateCacheEntries) removed user: " + ((User)cacheUser.getData()).getUserName() + " from cache!");
				}
			}
		}
	}
	
	private void clearUserCache() {
		synchronized(userCache) {
			userCache.clear();
			logger.fine("(clearUserCache) removed all users from cache!");
		}
	}
	
	/* ############################################################################################ */
	/* public operations																			*/
	/* ############################################################################################ */
	
	/**
	 * Returns the user either from cache or database.
	 * @param userName - defines which user to get
	 * @return User if it exists or null if not.
	 */
	public User getUser(String userName) {
		User user = getUserFromCache(userName);
		if (user != null) return user;
		user = getUserFromDB(userName);
		if (user != null) putUserIntoCache(user);
		return user;		
	}
	
	/**
	 * Add a new user to the database
	 * @return TRUE if was successfully added
	 */
	public boolean addUser(String userName, String password, String displayName, String ipmask,
			int maxConnections, boolean enabled, boolean debug, boolean admin, 
			String mail, boolean mapExcluded, Set allowedProfileIds) {
		int userId = addUserToDB(userName, password, displayName, ipmask, maxConnections, 
				enabled, debug, admin, mail, mapExcluded);
		return (userId != -1) && addUserProfilesToDB(userId, allowedProfileIds);
	}
	
	/**
	 * Edit an existing user. 
	 * @return TRUE when it was successfully edited.
	 */
	public boolean editUser(int id, String userName, String password, String displayName, String ipmask,
			int maxConnections, boolean enabled, boolean debug, boolean admin, 
			String mail, boolean mapExcluded, Set allowedProfileIds) {
		return 
			editUserInDB(id, userName, password, displayName, ipmask, maxConnections, 
				enabled, debug, admin, mail, mapExcluded) && 
			deleteUserProfilesFromDB(id) &&
			addUserProfilesToDB(id, allowedProfileIds) &&
			deleteUserFromCache(userName);
	}
	
	/**
	 * Delete an existing user from database.
	 * @param userName
	 * @return TRUE when the user was successfully edited.
	 */
	public boolean deleteUser(String userName) {
		return deleteUserFromDB(userName) && deleteUserFromCache(userName);
	}
	
	/**
	 * Delete all existing user from database except the one currently
	 * logged in. 
	 * @param skipUserName
	 * @return TRUE when all users were successfully deleted.
	 */
	public boolean deleteAllUsers(String skipUserName) {
		if (deleteAllUsersFromDB(skipUserName)) {
			clearUserCache();
			return true;
		} else return false;
	}
	
	/**
	 * Import a Set of Users. The User objects contains also a list of profile-names
	 * which get added when they don't exist yet. So if all goes right, the users,
	 * the profiles and the user-profils relations get added.
	 * @param users
	 * @return TRUE when import was successfull
	 */
	public boolean importUsers(Set users) {
		// temp profile cache
		List profiles = getProfiles();
		// iterate through all user
		for(Iterator iterUser = users.iterator(); iterUser.hasNext(); ) {
			User user = (User)iterUser.next();
			// when user doesn't exist in db yet add it
			if (getUser(user.getUserName()) == null) {
				int userId = addUserToDB(
						user.getUserName(),
						user.getPassword(),
						user.getDisplayName(),
						user.getIpMask(),
						user.getMaxConnections(),
						user.isEnabled(),
						user.isDebug(),
						user.isAdmin(),
						user.getEmail(),
						user.isMapExcluded()
					);
				// only if the new use is added care about his profiles
				if (userId != -1) {
					Set profileIds = new HashSet();
					for (Iterator iterProfile = user.getAllowedProfiles().iterator(); iterProfile.hasNext(); ) {
						String profileName = (String)iterProfile.next();
						Profile profile = null;
						
						for (Iterator iterTempProfiles = profiles.iterator(); iterTempProfiles.hasNext(); ) {
							Profile tmpProfile = (Profile)iterTempProfiles.next();
							if (tmpProfile.getProfileName().equalsIgnoreCase(profileName)) {
								profile = tmpProfile;
								break;
							}
						}
						
						if ( profile == null ) {
							int profileId = addProfileToDB(profileName);
							if (profileId != -1) {
								profileIds.add(new Integer(profileId));
								profiles.add(getProfile(profileId));
							}
						} else profileIds.add(new Integer(profile.getId()));
					}
					// last step is to add the relations between user and profiles
					addUserProfilesToDB(userId, profileIds);
				}
			}
		}
		return true;
	}
	
	/**
	 * Sets the debug flag for a specific user.
	 * @param userName
	 * @param debug
	 */
	public void setDebug(String userName, boolean debug) {
		if (setUserDebugInDB(userName, debug)) deleteUserFromCache(userName);
	}
	
	/**
	 * Returns a list of userNames limited by skipRos and numRows.
	 * @param skipRows
	 * @param numRows
	 * @return List of userNames
	 */
	public List getUserNames(int skipRows, int numRows) {
		List userNames = getUserNamesFromDB(skipRows, numRows);
		return userNames != null ? userNames : Collections.EMPTY_LIST; 
	}
	
	/**
	 * Returns the Profile from database.
	 * @param id
	 * @return Profile
	 */
	public Profile getProfile(int id) {
		return getProfileFromDB(new Integer(id));
	}
	
	/**
	 * Returns the Profile from database.
	 * @param profileName
	 * @return Profile
	 */
	public Profile getProfile(String profileName) {
		return getProfileFromDB(profileName);
	}
	
	/**
	 * Returns all profiles as a List containing Profiles.
	 * @return all Profiles
	 */
	public List getProfiles() {
		return getProfilesFromDB();
	}
	
	/**
	 * add profile
	 * @param profileName
	 * @return TRUE when adding was successfull
	 */
	public boolean addProfile(String profileName) {
		return addProfileToDB(profileName) != -1;
	}
	
	/**
	 * edit profile
	 * @param id
	 * @param profileName
	 * @return TRUE when profile was successfully edited.
	 */
	public boolean editProfile(int id, String profileName) {
		boolean result = editProfileInDB(id, profileName);
		clearUserCache();
		return result;
	}
	
	/**
	 * delete profile
	 * @param id
	 * @return TRUE when profile was successfully deleted
	 */
	public boolean deleteProfile(int id) {
		boolean result = deleteProfileFromDB(id);
		clearUserCache();
		return result;
	}
	
	/**
	 * delete all profiles
	 * @return TRUE when delete was successfull
	 */
	public boolean deleteAllProfiles() {
		boolean result = deleteAllProfilesFromDB();
		clearUserCache();
		return result;
	}
	
	public void run() {
		try {
			while(!interrupted()) {
				sleep(cleaningInterval);
				updateCacheEntries();
			}
		} catch (InterruptedException e) {
			clearUserCache();
			if (connectionPoolManager != null) 
				connectionPoolManager.interrupt();
			logger.info("CacheManager interrupted!");
		}
	}
}