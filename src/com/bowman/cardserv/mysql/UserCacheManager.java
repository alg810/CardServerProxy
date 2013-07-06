package com.bowman.cardserv.mysql;

import com.bowman.cardserv.ConfigException;
import com.bowman.cardserv.interfaces.MySQLConstants;
import com.bowman.cardserv.util.ProxyLogger;

import java.sql.*;
import java.util.*;

/**
 * The CacheManager is currently a simple "Read-Ahead" cache. Which means when a user connected once
 * the user and profile informations will be get from the database and loaded into the cache. As long as the
 * informations are accessed a background thread refreshes the informations asynchronly.
 * <p/>
 * This cache is not ment to keep all database users in a local cache. It is used to reduce the amount
 * of database accesses by keeping the informations for a short periode of time in a local cache.
 *
 * @author DonCarlo
 * @since 14.12.2010
 */
public class UserCacheManager extends Thread {

  private static final long CLEANING_INTERVAL = 5000;

  private final Map userCache = new HashMap();

  private ProxyLogger logger = null;
  private ConnectionPoolManager connectionPoolManager = null;

  public UserCacheManager(String databaseHost, int databasePort, String databaseName, String databaseUser,
                          String databasePassword) throws ConfigException
  {
    this.logger = ProxyLogger.getLabeledLogger(getClass().getName());
    this.connectionPoolManager = new ConnectionPoolManager(databaseHost, databasePort, databaseName, databaseUser,
        databasePassword);

    // database prerequirements
    if(!existsUserTable()) {
      logger.info("creating new user table.");
      createUserTable();
    }

    if(!existsProfileTable()) {
      logger.info("creating new profile table.");
      createProfileTable();
    }

    if(!existsUsersHasProfilesTable()) {
      logger.info("creating new users_has_profiles table.");
      createUsersHasProfilesTable();
    }

    this.setName("CacheManagerThread");
    this.setPriority(MIN_PRIORITY);
    this.start();
  }

  private void createUserTable() {
    createTable(0);
  }

  private void createProfileTable() {
    createTable(1);
  }

  private void createUsersHasProfilesTable() {
    // the user and profile table must exist before this one can be created.
    if(existsUserTable() && existsProfileTable()) {
      createTable(2);
    }
  }

  private void createTable(int table) {
    MySQLConnection mySQLConnection = null;

    try {
      mySQLConnection = connectionPoolManager.getMySQLConnection();
      switch(table) {
        case 0:
          mySQLConnection.createUserTable();
          break;
        case 1:
          mySQLConnection.createProfileTable();
          break;
        case 2:
          mySQLConnection.createUsersHasProfilesTable();
          break;
      }
    } finally {
      mySQLConnection.returnConnection();
      connectionPoolManager.returnMySQLConnection(mySQLConnection);
    }
  }

  private boolean existsUserTable() {
    return existsTable(0);
  }

  private boolean existsProfileTable() {
    return existsTable(1);
  }

  private boolean existsUsersHasProfilesTable() {
    return existsTable(2);
  }

  private boolean existsTable(int table) {
    boolean result = false;
    MySQLConnection mySQLConnection = null;

    try {
      mySQLConnection = connectionPoolManager.getMySQLConnection();
      switch(table) {
        case 0:
          result = mySQLConnection.existsUserTable();
          break;
        case 1:
          result = mySQLConnection.existsProfileTable();
          break;
        case 2:
          result = mySQLConnection.existsUsersHasProfilesTable();
          break;
      }
    } finally {
      mySQLConnection.returnConnection();
      connectionPoolManager.returnMySQLConnection(mySQLConnection);
    }
    return result;
  }

  public String getDatabaseHost() {
    return connectionPoolManager.getDataSource().getServerName();
  }

  public String getDatabaseName() {
    return connectionPoolManager.getDataSource().getDatabaseName();
  }

  public int getDatabasePort() {
    return connectionPoolManager.getDataSource().getPort();
  }

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

  private User getUserFromDB(String userName) {
    ResultSet resultSet;
    MySQLConnection mySQLConnection = null;
    User user = null;

    try {
      mySQLConnection = connectionPoolManager.getMySQLConnection();
      if(mySQLConnection.getUser(userName)) {
        resultSet = mySQLConnection.getResultSet();
        if(resultSet.next() && resultSet.getString(MySQLConstants.DBC_USERS_USERNAME) != null) {
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
    } catch(SQLException e) {
      logger.warning("(getUserFromDB) Failed to parse ResultSet: " + e);
    } finally {
      mySQLConnection.returnConnection();
      connectionPoolManager.returnMySQLConnection(mySQLConnection);
    }
    return user;
  }

  private int addUserToDB(String userName, String password, String displayName, String ipmask, int maxConnections,
                          boolean enabled, boolean debug, boolean admin, String mail, boolean mapExcluded)
  {
    ResultSet resultSet;
    MySQLConnection mySQLConnection = null;
    int id = -1;

    try {
      mySQLConnection = connectionPoolManager.getMySQLConnection();
      if(mySQLConnection.addUser(userName, password, displayName, ipmask, maxConnections, enabled, debug, admin, mail,
          mapExcluded))
      {
        resultSet = mySQLConnection.getResultSet();
        if(resultSet.next()) id = resultSet.getInt(1);
      }
    } catch(SQLException e) {
      logger.warning("(addUserToDB) Failed to parse ResultSet: " + e);
    } finally {
      mySQLConnection.returnConnection();
      connectionPoolManager.returnMySQLConnection(mySQLConnection);
    }
    return id;
  }

  private boolean editUserInDB(int id, String userName, String password, String displayName, String ipmask, int maxConnections,
                               boolean enabled, boolean debug, boolean admin, String mail, boolean mapExcluded)
  {
    MySQLConnection mySQLConnection = null;
    boolean result = false;

    try {
      mySQLConnection = connectionPoolManager.getMySQLConnection();
      result = mySQLConnection.editUser(id,userName, password, displayName, ipmask, maxConnections, enabled, debug,
          admin, mail, mapExcluded);
    } finally {
      mySQLConnection.returnConnection();
      connectionPoolManager.returnMySQLConnection(mySQLConnection);
    }
    return result;
  }

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

  private List getUserNamesFromDB(int skipRows, int numRows) {
    List userNames = null;
    ResultSet resultSet;
    MySQLConnection mySQLConnection = null;

    try {
      mySQLConnection = connectionPoolManager.getMySQLConnection();
      if(mySQLConnection.getUserNames(skipRows, numRows)) {
        userNames = new ArrayList();
        resultSet = mySQLConnection.getResultSet();
        while(resultSet.next()) userNames.add(resultSet.getString(MySQLConstants.DBC_USERS_USERNAME));
      }
    } catch(SQLException e) {
      logger.warning("(getUserNamesFromDB) Failed to parse ResultSet: " + e);
    } finally {
      mySQLConnection.returnConnection();
      connectionPoolManager.returnMySQLConnection(mySQLConnection);
    }

    return userNames;
  }

  private boolean addUserProfilesToDB(int userId, Set profileIds) {
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

  private boolean deleteUserProfilesFromDB(int userId) {
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

  private Profile getProfileFromDB(Object id) {
    Profile profile = null;
    ResultSet resultSet;
    MySQLConnection mySQLConnection = null;

    try {
      mySQLConnection = connectionPoolManager.getMySQLConnection();
      if(id instanceof Integer ? mySQLConnection.getProfile(((Integer)id).intValue()) : mySQLConnection.getProfile((String)id)) {
        resultSet = mySQLConnection.getResultSet();
        if(resultSet.next()) {
          profile = new Profile(resultSet.getInt(MySQLConstants.DBC_PROFILES_ID),
              resultSet.getString(MySQLConstants.DBC_PROFILES_PROFILENAME));
        }
      }
    } catch(SQLException e) {
      logger.warning("(getProfileFromDB) Failed to parse ResultSet: " + e);
    } finally {
      mySQLConnection.returnConnection();
      connectionPoolManager.returnMySQLConnection(mySQLConnection);
    }
    return profile;
  }

  private List getProfilesFromDB() {
    List profiles = null;
    ResultSet resultSet;
    MySQLConnection mySQLConnection = null;

    try {
      mySQLConnection = connectionPoolManager.getMySQLConnection();
      if(mySQLConnection.getProfiles()) {
        profiles = new ArrayList();
        resultSet = mySQLConnection.getResultSet();
        while(resultSet.next()) {
          profiles.add(new Profile(resultSet.getInt(MySQLConstants.DBC_PROFILES_ID),
                  resultSet.getString(MySQLConstants.DBC_PROFILES_PROFILENAME)));
        }
      }
    } catch(SQLException e) {
      logger.warning("(getProfilesFromDB) Failed to parse ResultSet: " + e);
    } finally {
      mySQLConnection.returnConnection();
      connectionPoolManager.returnMySQLConnection(mySQLConnection);
    }
    return profiles;
  }

  private int addProfileToDB(String profileName) {
    ResultSet resultSet;
    MySQLConnection mySQLConnection = null;
    int id = -1;

    try {
      mySQLConnection = connectionPoolManager.getMySQLConnection();
      if(mySQLConnection.addProfile(profileName)) {
        resultSet = mySQLConnection.getResultSet();
        if(resultSet.next()) id = resultSet.getInt(1);
      }
    } catch(SQLException e) {
      logger.warning("(addProfileToDB) Failed to parse ResultSet: " + e);
    } finally {
      mySQLConnection.returnConnection();
      connectionPoolManager.returnMySQLConnection(mySQLConnection);
    }
    return id;
  }

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

  private User getUserFromCache(String userName) {
    synchronized(userCache) {
      if(userCache.containsKey(userName))
        return (User)((CacheEntry)userCache.get(userName)).getData();
    }
    return null;
  }

  private void putUserIntoCache(User user) {
    CacheEntry cacheUser = new CacheEntry(user);
    synchronized(userCache) {
      userCache.put(user.getUserName(), cacheUser);
    }
  }

  private boolean deleteUserFromCache(String userName) {
    synchronized(userCache) {
      if(userCache.containsKey(userName)) {
        userCache.remove(userName);
        return true;
      } else return false;
    }
  }

  private void updateCacheEntries() {
    synchronized(userCache) {
      Iterator iterator = userCache.keySet().iterator();
      while(iterator.hasNext()) {
        CacheEntry cacheUser = (CacheEntry)userCache.get(iterator.next());
        if(cacheUser.needsRefresh()) {
          cacheUser.setData(getUserFromDB(((User)cacheUser.getData()).getUserName()));
          logger.fine("(updateCacheEntries) refreshed user: " + ((User)cacheUser.getData()).getUserName() + " from cache!");
        } else if(cacheUser.isExpired()) {
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

  public User getUser(String userName) {
    User user = getUserFromCache(userName);
    if(user != null) return user;
    user = getUserFromDB(userName);
    if(user != null) putUserIntoCache(user);
    return user;
  }

  public boolean addUser(String userName, String password, String displayName, String ipmask, int maxConnections,
                         boolean enabled, boolean debug, boolean admin, String mail, boolean mapExcluded,
                         Set allowedProfileIds)
  {
    int userId = addUserToDB(userName, password, displayName, ipmask, maxConnections, enabled, debug, admin, mail, mapExcluded);
    return (userId != -1) && addUserProfilesToDB(userId, allowedProfileIds);
  }

  public boolean editUser(int id, String userName, String password, String displayName, String ipmask, int maxConnections,
                          boolean enabled, boolean debug, boolean admin, String mail, boolean mapExcluded,
                          Set allowedProfileIds)
  {
    return editUserInDB(id, userName, password, displayName, ipmask, maxConnections, enabled, debug, admin, mail, mapExcluded) &&
            deleteUserProfilesFromDB(id) && addUserProfilesToDB(id, allowedProfileIds) && deleteUserFromCache(userName);
  }

  public boolean deleteUser(String userName) {
    return deleteUserFromDB(userName) && deleteUserFromCache(userName);
  }

  public boolean deleteAllUsers(String skipUserName) {
    if(deleteAllUsersFromDB(skipUserName)) {
      clearUserCache();
      return true;
    } else return false;
  }

  public boolean importUsers(Set users) {
    // temp profile cache
    List profiles = getProfiles();
    // iterate through all user
    for(Iterator iterUser = users.iterator(); iterUser.hasNext(); ) {
      User user = (User)iterUser.next();
      // when user doesn't exist in db yet add it
      if(getUser(user.getUserName()) == null) {

        int userId = addUserToDB(user.getUserName(), user.getPassword(), user.getDisplayName(), user.getIpMask(),
            user.getMaxConnections(), user.isEnabled(), user.isDebug(), user.isAdmin(), user.getEmail(),
            user.isMapExcluded());

        // only if the new use is added care about his profiles
        if(userId != -1) {
          Set profileIds = new HashSet();
          for(Iterator iterProfile = user.getAllowedProfiles().iterator(); iterProfile.hasNext(); ) {
            String profileName = (String)iterProfile.next();
            Profile profile = null;

            for(Iterator iterTempProfiles = profiles.iterator(); iterTempProfiles.hasNext(); ) {
              Profile tmpProfile = (Profile)iterTempProfiles.next();
              if(tmpProfile.getProfileName().equalsIgnoreCase(profileName)) {
                profile = tmpProfile;
                break;
              }
            }

            if(profile == null) {
              int profileId = addProfileToDB(profileName);
              if(profileId != -1) {
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

  public void setDebug(String userName, boolean debug) {
    if(setUserDebugInDB(userName, debug)) deleteUserFromCache(userName);
  }

  public List getUserNames(int skipRows, int numRows) {
    List userNames = getUserNamesFromDB(skipRows, numRows);
    return userNames != null ? userNames : Collections.EMPTY_LIST;
  }

  public Profile getProfile(int id) {
    return getProfileFromDB(new Integer(id));
  }

  public Profile getProfile(String profileName) {
    return getProfileFromDB(profileName);
  }

  public List getProfiles() {
    return getProfilesFromDB();
  }

  public boolean addProfile(String profileName) {
    return addProfileToDB(profileName) != -1;
  }

  public boolean editProfile(int id, String profileName) {
    boolean result = editProfileInDB(id, profileName);
    clearUserCache();
    return result;
  }

  public boolean deleteProfile(int id) {
    boolean result = deleteProfileFromDB(id);
    clearUserCache();
    return result;
  }

  public boolean deleteAllProfiles() {
    boolean result = deleteAllProfilesFromDB();
    clearUserCache();
    return result;
  }

  public void run() {
    try {
      while(!interrupted()) {
        sleep(CLEANING_INTERVAL);
        updateCacheEntries();
      }
    } catch(InterruptedException e) {
      clearUserCache();
      if(connectionPoolManager != null) connectionPoolManager.interrupt();
      logger.info("CacheManager interrupted!");
    }
  }
}