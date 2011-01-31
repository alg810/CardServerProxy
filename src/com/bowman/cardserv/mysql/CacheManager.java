package com.bowman.cardserv.mysql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import com.bowman.cardserv.ConfigException;
import com.bowman.cardserv.util.ProxyLogger;

/**
 * The CacheManager is currently a simple "Read-Ahead" cache. Which means when a user connected once
 * the user and profile informations will be get from the database and loaded into the cache. As long as the 
 * informations are accessed a background thread refreshes the informations asynchronly. 
 *
 * This cache is not ment to keep all database users and profiles in a local cache. It is used to reduce the amount 
 * of database accesses by keeping the informations for a short periode of time in a local cache. 
 * 
 * @author DonCarlo
 * @since 14.12.2010
 */
public class CacheManager extends Thread {

	private Map userCache = Collections.synchronizedMap(new HashMap());
	private Map profileCache = Collections.synchronizedMap(new HashMap());

	private final long cleaningInterval = 5000;
	private ProxyLogger logger = null;
	private ConnectionPoolManager connectionPoolManager = null;


	public CacheManager(String databaseHost, int databasePort, String databaseName,
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

		if (existsUserTable() && isUserTableEmpty()) {
			logger.info("MySQL database is empty. Adding default user 'admin' with the password 'secret'.");
			addDefaultUserToDB();
		}

		this.setName("CacheManagerThread");
		this.setPriority(MIN_PRIORITY);
		this.start();
	}

	/* ############################################################################################ */
	/* database prerequisites                                                                       */
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
		PoolConnection poolConnection = null;

		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			switch (table) {
			case 0 :
				poolConnection.createUserTable();
				break;
			case 1 :
				poolConnection.createProfileTable();
				break;
			case 2 :
				poolConnection.createUsersHasProfilesTable();
				break;
			}
		} catch (SQLException e) {
			logger.warning("(createTable) Failed to create the table : " + e);
		} finally {
			connectionPoolManager.returnPoolConnection(poolConnection);
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
		PoolConnection poolConnection = null;

		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			switch (table) {
			case 0 :
				result = poolConnection.existsUserTable();
				break;
			case 1 :
				result = poolConnection.existsProfileTable();
				break;
			case 2 :
				result = poolConnection.existsUsersHasProfilesTable();
				break;
			}
		} catch (SQLException e) {
			logger.warning("(existsTable) Failed to query the existance of table : " + e);
		} finally {
			connectionPoolManager.returnPoolConnection(poolConnection);
		}
		return result;
	}

	/**
	 * tests whether the "user" table is empty.
	 * @return TRUE, when "user" table is empty.
	 */
	private boolean isUserTableEmpty() {
		boolean result = false;
		PoolConnection poolConnection = null;

		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			result = poolConnection.isUserTableEmpty();
		} catch (SQLException e) {
			logger.warning("(isUserTableEmpty) Failed to query emptyness of user table : " + e);
		} finally {
			connectionPoolManager.returnPoolConnection(poolConnection);
		}
		return result;
	}

	/**
	 * close the specified ResultSet to free the ressources.
	 * @param resultSet - ResultSet to close
	 */
	private void closeResultSet(ResultSet resultSet) {
		try {
			if (resultSet != null) {
				resultSet.close();
				resultSet = null;
			}
		} catch (Exception e) {
			logger.warning("(closeResultSet) error while closing resultSet: " + e);
		}
	}

	/**
	 * converts a String separated with whitespaces to an sorted ArrayList
	 * @param resultSet
	 * @param column
	 * @return
	 * @throws SQLException
	 */
	private ArrayList stringToArrayList(ResultSet resultSet, String column) throws SQLException {
		ArrayList al = new ArrayList();
		if (resultSet.next() && resultSet.getString(column) != null) {
			for(StringTokenizer st = new StringTokenizer(resultSet.getString(column)); st.hasMoreTokens(); ) {
				al.add(st.nextToken());
			}
		}
		Collections.sort(al);
		return al;
	}

	/* ############################################################################################ */
	/* users                                                                                        */
	/* ############################################################################################ */

	/**
	 * if the user information are not already in the cache, it must be fetched from the database.
	 * This function gets all informations for the given user with only one database statement, parses
	 * them from the returned resultSet, makes an User object out of it and puts it into the local cache.
	 * When there is no data for the passed user then nothing will be done.
	 * 
	 * @param username - describes which data should be get from database.
	 * @param addToCache - should the user be added to the local cache?
	 * @return User object - if the user does not exist in database null will be returned.
	 */
	private User getUserFromDB(String username, boolean addToCache) {
		ResultSet resultSet = null;
		PoolConnection poolConnection = null;
		User result = null;

		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			resultSet = poolConnection.getUserInfo(username);
			if (resultSet.next()) {
				logger.fine("(getUserFromDB) getting user: " + username + " from database.");
				result = new User(
						resultSet.getInt(DBColumnType.ID.toString()),
						resultSet.getString(DBColumnType.USERNAME.toString()),
						resultSet.getString(DBColumnType.PASSWORD.toString()),
						resultSet.getString(DBColumnType.DISPLAYNAME.toString()),
						resultSet.getInt(DBColumnType.MAXCONNECTIONS.toString()),
						resultSet.getString(DBColumnType.IPMASK.toString()),
						resultSet.getString(DBColumnType.MAIL.toString()),
						resultSet.getBoolean(DBColumnType.ENABLED.toString()),
						resultSet.getBoolean(DBColumnType.ADMIN.toString()),
						resultSet.getBoolean(DBColumnType.DEBUG.toString()),
						resultSet.getBoolean(DBColumnType.MAPEXCLUDE.toString())
				);
				closeResultSet(resultSet);
				resultSet = poolConnection.getUserProfiles(result.getId());
				result.setAllowedProfiles(new HashSet(stringToArrayList(resultSet, "GROUP_CONCAT(p.profilename SEPARATOR ' ')")));
				if (addToCache) putUserIntoCache(result);
			}
		} catch (SQLException e) {
			logger.warning("(getUserFromDB) Failed to query the database for user: " + username + " - " + e);
		} finally {
			closeResultSet(resultSet);
			connectionPoolManager.returnPoolConnection(poolConnection);
		}
		return result;
	}

	/**
	 * add a default user with admin rights to the database.
	 */
	private void addDefaultUserToDB() {
		addUserToDB("admin", "secret", "admin", "*", 1, true, false, true, "", false, "");
	}

	/**
	 * add an new user to the MySQL database.
	 */
	public void addUserToDB(String username, String password, String displayname, String ipmask,
			int maxconnections, boolean enabled, boolean debug, boolean admin, 
			String mail, boolean mapexcluded, String allowedProfiles) {
		PoolConnection poolConnection = null;

		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			int userID = poolConnection.addUser(username, password, displayname, ipmask, maxconnections, 
					enabled, debug, admin, mail, mapexcluded);

			for(StringTokenizer st = new StringTokenizer(allowedProfiles); st.hasMoreTokens(); ){
				String currentProfile = st.nextToken();
				if (getProfile(currentProfile) == null) poolConnection.addProfile(currentProfile);
				poolConnection.addUserProfile(userID, getProfile(currentProfile).getId());
			}
		} catch (SQLException e) {
			logger.warning("(addUserToDB) Failed to add user '" + username + "' to database. " + e);
		} finally {
			connectionPoolManager.returnPoolConnection(poolConnection);
		}
	}

	/**
	 * edit an existing MySQL database user.
	 */
	public void editUserInDB(String username, String password, String displayname, String ipmask,
			int maxconnections, boolean enabled, boolean debug, boolean admin, 
			String mail, boolean mapexcluded, String allowedProfiles) {
		PoolConnection poolConnection = null;

		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			poolConnection.editUser(username, password, displayname, ipmask, maxconnections, 
					enabled, debug, admin, mail, mapexcluded);

			poolConnection.deleteAllUserProfiles(getUser(username).getId());
			for(StringTokenizer st = new StringTokenizer(allowedProfiles); st.hasMoreTokens(); ){
				poolConnection.addUserProfile(getUser(username).getId(), getProfile(st.nextToken()).getId());
			}
			delUserFromCache(username);
		} catch (SQLException e) {
			logger.warning("(editUserInDB) Failed to edit user '" + username + "'. " + e);
		} finally {
			connectionPoolManager.returnPoolConnection(poolConnection);
		}
	}

	/**
	 * delete specified user from the database and the local cache.
	 * @param username - the user to delete.
	 */
	public void deleteUserFromDB(String username) {
		PoolConnection poolConnection = null;
		
		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			poolConnection.deleteUser(username);
			delUserFromCache(username);
		} catch (SQLException e) {
			logger.warning("(deleteUserFromDB) Failed to delete user '" + username + "' from database. " + e);
		} finally {
			connectionPoolManager.returnPoolConnection(poolConnection);
		}
	}

	/**
	 * delete all users from the database.
	 * @param username - the user which should not be deleted.
	 */
	public void deleteAllUsersFromDB(String username) {
		Iterator iterator = getUserNames().iterator();
		while (getUserNames().iterator().hasNext()) {
			String currentUser = (String)iterator.next();
			if (!currentUser.equals(username)) deleteUserFromDB(currentUser);
		}
	}

	/**
	 * returns all usernames which are stored in the database as an sorted ArrayList.
	 * @return all usersnames in the database
	 */
	public ArrayList getUserNames() {
		ArrayList userNames = new ArrayList();
		ResultSet resultSet = null;
		PoolConnection poolConnection = null;
		
		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			resultSet = poolConnection.getUserNames();
			userNames = stringToArrayList(resultSet, DBColumnType.USERS.toString());
		} catch (SQLException e) {
			logger.warning("(getUserNames) Failed to query the database: " + e);
		} finally {
			closeResultSet(resultSet);
			connectionPoolManager.returnPoolConnection(poolConnection);
		}

		return userNames;
	}

	/**
	 * set user debug value in the database.
	 * @param username - specifies which user gets changed
	 * @param debug - enable/disable debug
	 */
	public void setUserDebug(String username, boolean debug) {
		PoolConnection poolConnection = null;
		
		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			poolConnection.setUserDebug(username, debug);
		} catch (SQLException e) {
			logger.warning("(setUserDebug) Failed to set debug for user " + username + " : " + e);
		} finally {
			connectionPoolManager.returnPoolConnection(poolConnection);
		}
	}

	/* ############################################################################################ */
	/* database informations                                                                        */
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

	/* ############################################################################################ */
	/* profiles                                                                                     */
	/* ############################################################################################ */

	/**
	 * if the profile information are not already in the cache, it must be fetched from the database.
	 * This function gets all informations for the given profile with only one database statement, parses
	 * them from the returned resultSet, makes an Profile object out of it and puts it into the local cache.
	 * When there is no data for the passed profile then nothing will be done.
	 * 
	 * @param profileName - describes which data should be get from database.
	 * @param addToCache - should the user be added to the local cache?
	 * @return Profile object - if the profile does not exist in database null will be returned.
	 */
	private Profile getProfileFromDB(String profileName, boolean addToCache) {
		ResultSet resultSet = null;
		PoolConnection poolConnection = null;
		Profile result = null;

		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			resultSet = poolConnection.getProfileInfo(profileName);
			if (resultSet.next()) {
				logger.fine("(getProfileFromDB) getting profile: " + profileName + " from database.");
				result = new Profile(
						resultSet.getInt(DBColumnType.ID.toString()),
						resultSet.getString(DBColumnType.PROFILENAME.toString())
				);
				if (addToCache) putProfileIntoCache(result);
			}
		} catch (SQLException e) {
			logger.warning("(getProfileFromDB) Failed to query the database for profile: " + profileName + " - " + e);
		} finally {
			closeResultSet(resultSet);
			connectionPoolManager.returnPoolConnection(poolConnection);
		}
		return result;
	}

	/**
	 * add a new profile to the MySQL database.
	 * @param profileName - profile to add
	 */
	public void addProfileToDB(String profileName) {
		PoolConnection poolConnection = null;
		
		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			poolConnection.addProfile(profileName);
		} catch (SQLException e) {
			logger.warning("(addProfileToDB) Failed to add profile '" + profileName + "' to database. " + e);
		} finally {
			connectionPoolManager.returnPoolConnection(poolConnection);
		}
	}

	/**
	 * delete all profiles from the MySQL database.
	 */
	public void deleteAllProfilesFromDB() {
		Iterator iterator = getProfileNames().iterator();
		while (iterator.hasNext()) {
			deleteProfileFromDB((String)iterator.next());
		}
	}

	/**
	 * delete specified profile from the MySQL database and the local cache.
	 * @param profileName - profile to delete
	 */
	public void deleteProfileFromDB(String profileName) {
		PoolConnection poolConnection = null;
		
		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			poolConnection.deleteProfile(profileName);
			
			// TODO - only delete cache entries which are affected from this change
			clearCache();
		} catch (SQLException e) {
			logger.warning("(deleteProfileFromDB) Failed to delete profile '" + profileName + "' from database. " + e);
		} finally {
			connectionPoolManager.returnPoolConnection(poolConnection);
		}
	}

	/**
	 * returns all profile names as a sorted ArrayList.
	 * @return profile names
	 */
	public ArrayList getProfileNames() {
		ArrayList profileNames = new ArrayList();
		ResultSet resultSet = null;
		PoolConnection poolConnection = null;
		
		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			resultSet = poolConnection.getProfileNames();
			profileNames = stringToArrayList(resultSet, DBColumnType.PROFILENAMES.toString());
		} catch (SQLException e) {
			logger.warning("(getProfileNames) Failed to query the database: " + e);
		} finally {
			closeResultSet(resultSet);
			connectionPoolManager.returnPoolConnection(poolConnection);
		}

		return profileNames;
	}

	/* ############################################################################################ */
	/* cache operations                                                                             */
	/* ############################################################################################ */

	/**
	 * returns the User object specified through the identifier username out of the local cache.
	 * if the cache doesn't contain this object, get the user from the database and it it to the cache.
	 * @param username - specifies which user to get.
	 * @return User object - when not found null
	 */
	public User getUser(String username) {
		synchronized(userCache) {
			if (userCache.containsKey(username))
				return (User)((CacheEntry) userCache.get(username)).getData();
		}
		return getUserFromDB(username, true);
	}

	/**
	 * returns the Profile object specified through the identifier profileName out of the local cache.
	 * if the cache doesn't contain this object, get the profile from the database and it it to the cache.
	 * @param profileName - specifies which profile to get.
	 * @return Profile object - when not found null
	 */
	public Profile getProfile(String profileName) {
		synchronized(profileCache) {
			if (profileCache.containsKey(profileName))
				return (Profile)((CacheEntry) profileCache.get(profileName)).getData();
		}
		return getProfileFromDB(profileName, true);
	}

	/**
	 * add the specified profile to the cache by makeing a cache object out of it.
	 * @param profile - profile to add to the cache.
	 */
	private void putProfileIntoCache(Profile profile) {
		CacheEntry cacheProfile = new CacheEntry(profile);
		synchronized(profileCache) {
			profileCache.put(((Profile)cacheProfile.getData()).getProfileName(), cacheProfile);
		}
	}

	/**
	 * add the specified user to the cache by makeing a cache object out of it.
	 * @param user - user to add to the cache.
	 */
	private void putUserIntoCache(User user) {
		CacheEntry cacheUser = new CacheEntry(user);
		synchronized(userCache) {
			userCache.put(((User)cacheUser.getData()).getUsername(), cacheUser);
		}
	}

	/**
	 * delete user identified through the user-name from the cache.
	 * @param userName
	 */
	private void delUserFromCache(String userName) {
		synchronized(userCache) {
			if (userCache.containsKey(userName))
				userCache.remove(userName);
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
					cacheUser.setData(getUserFromDB(((User)cacheUser.getData()).getUsername(), false));
					logger.fine("(updateCacheEntries) refreshed user: " + ((User)cacheUser.getData()).getUsername() + " from cache!");
				} else if (cacheUser.isExpired()) {
					iterator.remove();
					logger.fine("(updateCacheEntries) removed user: " + ((User)cacheUser.getData()).getUsername() + " from cache!");
				}
			}
		}
		
		synchronized (profileCache) {
			Iterator iterator = profileCache.keySet().iterator();
			while (iterator.hasNext()) {
				CacheEntry cacheProfile = (CacheEntry) profileCache.get(iterator.next());
				if (cacheProfile.needsRefresh()) {
					cacheProfile.setData(getProfileFromDB(((Profile)cacheProfile.getData()).getProfileName(), false));
					logger.fine("(updateCacheEntries) refreshed profile: " + ((Profile)cacheProfile.getData()).getProfileName() + " from cache!");
				} else if (cacheProfile.isExpired()) {
					iterator.remove();
					logger.fine("(updateCacheEntries) removed profile: " + ((Profile)cacheProfile.getData()).getProfileName() + " from cache!");
				}
			}
		}
	}

	/**
	 * removes all CacheUser and CacheProfile objects from the local caches.
	 */
	private void clearCache() {
		synchronized(userCache) {
			userCache.clear();
			logger.fine("(clearCache) removed all users from cache!");
		}
		synchronized(profileCache) {
			profileCache.clear();
			logger.fine("(clearCache) removed all profiles from cache!");
		}
	}

	/**
	 * deletes all cache entries and disposes the ConnectionPoolManager
	 */
	private void disposeCacheManager() {
		clearCache();
		if (connectionPoolManager != null)
			connectionPoolManager.interrupt();
	}

	public void run() {
		try {
			while(!interrupted()) {
				sleep(cleaningInterval);
				updateCacheEntries();
			}
		} catch (InterruptedException e) {
			disposeCacheManager();
			logger.info("CacheManager interrupted!");
		}
	}
}