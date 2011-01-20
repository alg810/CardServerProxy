package com.bowman.cardserv.mysql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
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

	private HashMap userCache = new HashMap();
	private HashMap profileCache = new HashMap();
	
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
			logger.warning("MySQL database is empty. Adding default user 'admin' with the password 'secret'.");
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
		PoolConnection poolConnection = null;
		
		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			poolConnection.createUserTable();
		} catch (SQLException e) {
			logger.warning("(createUserTable) Failed to create the user table : " + e);
		} finally {
			connectionPoolManager.returnPoolConnection(poolConnection);
		}
	}
	
	/**
	 * create the "profile" table in the mysql database.
	 */
	private void createProfileTable() {
		PoolConnection poolConnection = null;
		
		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			poolConnection.createProfileTable();
		} catch (SQLException e) {
			logger.warning("(createProfileTable) Failed to create the profile table : " + e);
		} finally {
			connectionPoolManager.returnPoolConnection(poolConnection);
		}
	}
	
	/**
	 * create the "users_has_profiles" table in the mysql datbase.
	 */
	private void createUsersHasProfilesTable() {
		// the user and profile table must exist before this one can be created.
		if (existsUserTable() && existsProfileTable()) {
			PoolConnection poolConnection = null;
		
			try {
				poolConnection = connectionPoolManager.getPoolConnection();
				poolConnection.createUsersHasProfilesTable();
			} catch (SQLException e) {
				logger.warning("(createUsersHasProfilesTable) Failed to create the users_has_profiles table : " + e);
			} finally {
				connectionPoolManager.returnPoolConnection(poolConnection);
			}
		} else {
			// TODO add exception
		}
	}
	
	/**
	 * tests whether the "user" table exists in the mysql database.
	 * @return TRUE, when user table exists.
	 */
	private boolean existsUserTable() {
		boolean result = false;
		PoolConnection poolConnection = null;
		
		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			result = poolConnection.existsUserTable();
		} catch (SQLException e) {
			logger.warning("(existsUserTable) Failed to query the existance of user table : " + e);
		} finally {
			connectionPoolManager.returnPoolConnection(poolConnection);
		}
		return result;
	}
	
	/**
	 * tests whether the "profile" table exists in the mysql database.
	 * @return TRUE, when profile table exists.
	 */
	private boolean existsProfileTable() {
		boolean result = false;
		PoolConnection poolConnection = null;
		
		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			result = poolConnection.existsProfileTable();
		} catch (SQLException e) {
			logger.warning("(existsProfileTable) Failed to query the existance of profile table : " + e);
		} finally {
			connectionPoolManager.returnPoolConnection(poolConnection);
		}
		return result;
	}
	
	/**
	 * tests whether the "users_has_profiles" table exists in the mysql database.
	 * @return TRUE, when users_has_profiles table exists.
	 */
	private boolean existsUsersHasProfilesTable() {
		boolean result = false;
		PoolConnection poolConnection = null;
		
		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			result = poolConnection.existsUsersHasProfilesTable();
		} catch (SQLException e) {
			logger.warning("(existsUsersHasProfilesTable) Failed to query the existance of users_has_profiles table : " + e);
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
	
	/* ############################################################################################ */
	/* some universal database operations                                                           */
	/* ############################################################################################ */
	
	/**
	 * universal function to send query to database.
	 * @param pst - describes which PreparedStatement should be used for they query.
	 * @param obj - result object
	 * @param column - specifies which data is going to be returned
	 * @param info - some additional info needed for the query. e.g. username
	 * @return obj with the wanted database info
	 */
	private Object dbQueryHelper(PreparedStatementType pst, Object obj, DBColumnType column, String info) {
		Object result = new String();
		ResultSet resultSet = null;
		PoolConnection poolConnection = null;
		
		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			if (pst == PreparedStatementType.GET_USERNAMES) {
				resultSet = poolConnection.getUserNames();
			} else if (pst == PreparedStatementType.GET_PROFILENAMES) {
				resultSet = poolConnection.getProfileNames();
			}
			if (resultSet.next() && resultSet.getObject(column.toString()) != null) {
				result = resultSet.getObject(column.toString()).equals(new String()) ? obj : resultSet.getObject(column.toString());
			}
		} catch (SQLException e) {
			logger.warning("(dbQueryHelper) column: " + column + " info: " + info + " Failed to query the database: " + e);
		} finally {
			closeResultSet(resultSet);
        	connectionPoolManager.returnPoolConnection(poolConnection);
		}
		return result;
	}
	
	/**
	 * send changes to database.
	 * @param pst - describes the type which prepared Statement is used to query the database 
	 * @param user - describes which user gets updated
	 * @param value - new value for the described user
	 */
	private void updateRowInDB(PreparedStatementType pst, String user, Object value) {
		PoolConnection poolConnection = null;
		
		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			if (pst == PreparedStatementType.SET_USER_DEBUG) { 
				poolConnection.setUserDebug(user, (Boolean)value);
			}
		} catch (SQLException e) {
			logger.warning("(updateRowInDB) Failed to update row in database for " + user + " : " + e);
		} finally {
			connectionPoolManager.returnPoolConnection(poolConnection);
		}
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
				// create new user object with informations from database
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
				Set allowedProfiles = new HashSet();
				if (resultSet.next() && resultSet.getObject("GROUP_CONCAT(p.profilename SEPARATOR ' ')") != null) {
					for(StringTokenizer st = new StringTokenizer(resultSet.getString("GROUP_CONCAT(p.profilename SEPARATOR ' ')")); st.hasMoreTokens(); ){
						allowedProfiles.add(st.nextToken());
					}
				}
				result.setAllowedProfiles(allowedProfiles);
				
				if (addToCache)
					putUserIntoCache(result);
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
			poolConnection.addUser(username, password, displayname, ipmask, maxconnections, 
					enabled, debug, admin, mail, mapexcluded);
			// get the newly created user
			int userID = getUserFromDB(username, false).getId();
			for(StringTokenizer st = new StringTokenizer(allowedProfiles); st.hasMoreTokens(); ){
				String currentProfile = st.nextToken();
				// when profile does not exist, add it, mainly used for the xml import
				if (getProfile(currentProfile) == null) {
					poolConnection.addProfile(currentProfile);
				}
				if (getProfile(currentProfile) != null) {
					int profileID = getProfile(currentProfile).getId();
					// add to users_has_profiles table
					poolConnection.addUserProfile(userID, profileID);
				}
			}

		} catch (SQLException e) {
			logger.warning("(addUserToDB) Failed to add user '" + username + "' to database. " + e);
		} finally {
			connectionPoolManager.returnPoolConnection(poolConnection);
		}
	}	
	
	/**
	 * add an new user to the MySQL database.
	 * @param user - User object
	 */
	public void addUserToDB(User user) {
		PoolConnection poolConnection = null;
		
		try {
			poolConnection = connectionPoolManager.getPoolConnection();
			poolConnection.addUser(user.getUsername(), user.getPassword(), user.getDisplayName(), user.getIpMask(),
					user.getMaxConnections(), user.isEnabled(), user.isDebug(), user.isAdmin(), user.getEmail(), user.isMapExcluded());
			// get the newly created user
			int userID = getUserFromDB(user.getUsername(), false).getId();
			Iterator iterator = user.getAllowedProfiles().iterator();
			while(iterator.hasNext()){
				String currentProfile = (String) iterator.next();
				// when profile does not exist, add it, mainly used for the xml import
				if (getProfile(currentProfile) == null) {
					poolConnection.addProfile(currentProfile);
				}
				if (getProfile(currentProfile) != null) {
					int profileID = getProfile(currentProfile).getId();
					// add to users_has_profiles table
					poolConnection.addUserProfile(userID, profileID);
				}
			}

		} catch (SQLException e) {
			logger.warning("(addUserToDB) Failed to add user '" + user.getUsername() + "' to database. " + e);
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

			int userID = getUserFromDB(username, false).getId();
			poolConnection.deleteAllUserProfiles(userID);
			for(StringTokenizer st = new StringTokenizer(allowedProfiles); st.hasMoreTokens(); ){
				String currentProfile = st.nextToken();
				int profileID = getProfile(currentProfile).getId();
				// add to users_has_profiles table
				poolConnection.addUserProfile(userID, profileID);
			}
			synchronized(userCache) {
				if (userCache.containsKey(username))
					userCache.remove(username);
			}
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
			synchronized(userCache) {
				if (userCache.containsKey(username))
					userCache.remove(username);
			}
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
		ArrayList userNames = getUserNames();
		Iterator iterator = userNames.iterator();
		while (iterator.hasNext()) {
			String currentUser = (String)iterator.next();
			if (!currentUser.equals(username)) {
				deleteUserFromDB(currentUser);
			}
		}
	}
	
	/**
	 * returns all usernames which are stored in the database as an sorted ArrayList.
	 * @return all usersnames in the database
	 */
	public ArrayList getUserNames() {
	    ArrayList userNames = new ArrayList();
	    // get all database user
		for(StringTokenizer st = new StringTokenizer((String) dbQueryHelper(PreparedStatementType.GET_USERNAMES, 
				new String(), DBColumnType.USERS, null)); st.hasMoreTokens(); ) {
			userNames.add(st.nextToken());
		}
		Collections.sort(userNames);
		return userNames;
	}

	/**
	 * set user debug value in the database.
	 * @param username - specifies which user gets changed
	 * @param debug - enable/disable debug
	 */
	public void setUserDebug(String username, boolean debug) {
		updateRowInDB(PreparedStatementType.SET_USER_DEBUG , username, new Boolean(debug));
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
				// create new profile object with informations from database
				result = new Profile(
						resultSet.getInt(DBColumnType.ID.toString()),
						resultSet.getString(DBColumnType.PROFILENAME.toString())
				);
				if (addToCache)
					putProfileIntoCache(result);
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
		ArrayList profileNames = getProfileNames();
		Iterator iterator = profileNames.iterator();
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
	    // get all database profiles
		for(StringTokenizer st = new StringTokenizer((String) dbQueryHelper(PreparedStatementType.GET_PROFILENAMES, 
				new String(), DBColumnType.PROFILENAMES, null)); st.hasMoreTokens(); ) {
			profileNames.add(st.nextToken());
		}
		Collections.sort(profileNames);
		return profileNames;
	}

	/* ############################################################################################ */
	/* cache operations                                                                                     */
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
				return ((CacheUser) userCache.get(username)).getUser();
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
				return ((CacheProfile) profileCache.get(profileName)).getProfile();
		}
		return getProfileFromDB(profileName, true);
	}
	
	/**
	 * add the specified profile to the cache by makeing a cache object out of it.
	 * @param profile - profile to add to the cache.
	 */
	private void putProfileIntoCache(Profile profile) {
		CacheProfile cacheProfile = new CacheProfile(profile);
		synchronized(profileCache) {
			profileCache.put(cacheProfile.getIdentifier(), cacheProfile);
		}
	}

	/**
	 * add the specified user to the cache by makeing a cache object out of it.
	 * @param user - user to add to the cache.
	 */
	private void putUserIntoCache(User user) {
		CacheUser cacheUser = new CacheUser(user);
		synchronized(userCache) {
			userCache.put(cacheUser.getIdentifier(), cacheUser);
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
				CacheUser cacheUser = (CacheUser) userCache.get(iterator.next());
				if (cacheUser.needsRefresh()) {
					cacheUser.setUser(getUserFromDB(cacheUser.getUser().getUsername(), false));
					logger.fine("(updateCacheEntries) refreshed user: " + cacheUser.getUser().getUsername() + " from cache!");
				} else if (cacheUser.isExpired()) {
					iterator.remove();
					logger.fine("(updateCacheEntries) removed user: " + cacheUser.getUser().getUsername() + " from cache!");
				}
			}
		}
		
		synchronized (profileCache) {
			Iterator iterator = profileCache.keySet().iterator();
			while (iterator.hasNext()) {
				CacheProfile cacheProfile = (CacheProfile) profileCache.get(iterator.next());
				if (cacheProfile.needsRefresh()) {
					cacheProfile.setProfile(getProfileFromDB(cacheProfile.getProfile().getName(), false));
					logger.fine("(updateCacheEntries) refreshed profile: " + cacheProfile.getProfile().getName() + " from cache!");
				} else if (cacheProfile.isExpired()) {
					iterator.remove();
					logger.fine("(updateCacheEntries) removed profile: " + cacheProfile.getProfile().getName() + " from cache!");
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
