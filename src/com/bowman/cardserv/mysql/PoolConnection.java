package com.bowman.cardserv.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;

/**
 * A simple database connection object.
 * 
 * @author DonCarlo
 * @since 10.12.2010
 */
public class PoolConnection {

	private final String DB_USER_TABLE = "users";
	private final String DB_PROFILE_TABLE = "profiles";
	private final String DB_USERS_HAS_PROFILES_TABLE = "users_has_profiles";
	
	private Connection connection;
	private long inactivityTimestamp;
	private MysqlConnectionPoolDataSource dataSource;
	private PreparedStatement
		ps_getAll,
		ps_getUserInfo, 
		ps_getUserNames, 
		ps_userExists, 
		ps_getUsersCount,
		ps_setUserDebug,
		ps_existsTable,
		ps_addUser,
		ps_editUser,
		ps_deleteUser,
		ps_addProfile,
		ps_deleteProfile,
		ps_getProfileInfo,
		ps_getProfileNames,
		ps_addUserProfile,
		ps_deleteAllUserProfiles,
		ps_getUserProfiles
	;
	
	private final String CREATE_USER_TABLE_STMT = 
			"CREATE TABLE IF NOT EXISTS " + DB_USER_TABLE + " (" +
			"`" + DBColumnType.ID + "` INT(10) UNSIGNED NOT NULL AUTO_INCREMENT, " +
			"`" + DBColumnType.USERNAME + "` VARCHAR(30) NOT NULL, " +
			"`" + DBColumnType.PASSWORD + "` VARCHAR(30), " +
			"`" + DBColumnType.DISPLAYNAME + "` VARCHAR(50), " +
			"`" + DBColumnType.IPMASK + "` VARCHAR(30), " +
			"`" + DBColumnType.MAXCONNECTIONS + "` TINYINT UNSIGNED, " +
			"`" + DBColumnType.ENABLED + "` TINYINT(1), " +
			"`" + DBColumnType.DEBUG + "` TINYINT(1), " +
			"`" + DBColumnType.ADMIN + "` TINYINT(1), " +
			"`" + DBColumnType.MAIL + "` VARCHAR(30), " +
			"`" + DBColumnType.MAPEXCLUDE + "` TINYINT(1), " +
			"PRIMARY KEY (`" + DBColumnType.ID + "`), " +
			"UNIQUE (`" + DBColumnType.USERNAME + "` ASC) " +
			") ENGINE = InnoDB DEFAULT CHARACTER SET = latin1"
	;
	
	private final String CREATE_PROFILE_TABLE_STMT = 
		"CREATE TABLE IF NOT EXISTS " + DB_PROFILE_TABLE + " (" +
		"`" + DBColumnType.ID + "` INT(10) UNSIGNED NOT NULL AUTO_INCREMENT, " +
		"`" + DBColumnType.PROFILENAME + "` VARCHAR(60) NOT NULL, " +
		"PRIMARY KEY (`" + DBColumnType.ID + "`), " +
		"UNIQUE (`" + DBColumnType.PROFILENAME + "` ASC) " +
		") ENGINE = InnoDB DEFAULT CHARACTER SET = latin1"
	;
	
	private final String CREATE_USERS_HAS_PROFILES_TABLE_STMT = 
		"CREATE TABLE IF NOT EXISTS `" + DB_USERS_HAS_PROFILES_TABLE + "` (" +
		"`" + DBColumnType.USERS_ID + "` INT(10) UNSIGNED NOT NULL, " +
		"`" + DBColumnType.PROFILES_ID + "` INT(10) UNSIGNED NOT NULL, " +
		"PRIMARY KEY (`" + DBColumnType.USERS_ID + "`, `" + DBColumnType.PROFILES_ID + "`), " +
		"INDEX `fk_users_has_profiles_profiles1` (`" + DBColumnType.PROFILES_ID + "` ASC), " +
		"CONSTRAINT `fk_users_has_profiles_users` " +
	    	"FOREIGN KEY (`" + DBColumnType.USERS_ID + "` ) " +
	    	"REFERENCES `" + DB_USER_TABLE + "` (`" + DBColumnType.ID + "` ) " +
	    	"ON DELETE CASCADE " +
	    	"ON UPDATE CASCADE, " +
	    "CONSTRAINT `fk_users_has_profiles_profiles1` " +
		    "FOREIGN KEY (`" + DBColumnType.PROFILES_ID + "` ) " +
		    "REFERENCES `" + DB_PROFILE_TABLE + "` (`" + DBColumnType.ID + "` ) " +
		    "ON DELETE CASCADE " +
		    "ON UPDATE CASCADE" +
		") ENGINE = InnoDB DEFAULT CHARACTER SET = latin1"
	;
	
	/**
	 * Create a simple database connection object for the connection pool. It includes the database
	 * connection and some prepared statements.
	 * @param dataSource
	 * @throws ClassNotFoundException
	 * @throws SQLException
	 */
	public PoolConnection(MysqlConnectionPoolDataSource dataSource) throws ClassNotFoundException, SQLException {
		this.dataSource = dataSource;
		initialize();
	}
	
	/**
	 * initialize the newly created PoolConnection object by getting a new database
	 * connection and setting up some prepared statements.
	 * @throws ClassNotFoundException
	 * @throws SQLException
	 */
	private void initialize() throws ClassNotFoundException, SQLException {
		establishConnection();
		setPreparedStatements();
	}
	
	/**
	 * creates several preparedStatements for faster and cleaner database
	 * querys.
	 * @throws SQLException
	 */
	private void setPreparedStatements() throws SQLException {
		ps_getAll = connection.prepareStatement(
				"SELECT " + DBColumnType.ALL + " FROM " + DB_USER_TABLE);
		ps_getUserInfo = connection.prepareStatement(
				"SELECT " + DBColumnType.ALL + " FROM " + DB_USER_TABLE + " WHERE " + DBColumnType.USERNAME + " = ?");
		ps_getUserNames = connection.prepareStatement(
				"SELECT " + DBColumnType.USERS + " FROM " + DB_USER_TABLE);
		ps_userExists = connection.prepareStatement(
				"SELECT " + DBColumnType.COUNT + " FROM " + DB_USER_TABLE + " WHERE " + DBColumnType.USERNAME + " = ?");
		ps_getUsersCount = connection.prepareStatement(
				"SELECT " + DBColumnType.COUNT + " FROM " + DB_USER_TABLE);
		ps_setUserDebug = connection.prepareStatement(
				"UPDATE " + DB_USER_TABLE + " SET " + DBColumnType.DEBUG + " = ? WHERE " + DBColumnType.USERNAME + " = ?");
		ps_existsTable = connection.prepareStatement(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_name = ?");
		ps_addUser = connection.prepareStatement(
				"INSERT INTO " + DB_USER_TABLE + " (" +
				DBColumnType.USERNAME + ", " +
				DBColumnType.PASSWORD + ", " +
				DBColumnType.DISPLAYNAME + ", " +
				DBColumnType.IPMASK + ", " +
				DBColumnType.MAXCONNECTIONS + ", " +
				DBColumnType.ENABLED + ", " +
				DBColumnType.DEBUG + ", " +
				DBColumnType.ADMIN + ", " +
				DBColumnType.MAIL + ", " +
				DBColumnType.MAPEXCLUDE + ") VALUES(?,?,?,?,?,?,?,?,?,?)",
				Statement.RETURN_GENERATED_KEYS);
		ps_editUser = connection.prepareStatement(
				"UPDATE " + DB_USER_TABLE + " SET " + 
					DBColumnType.PASSWORD + " = ?, " + 
					DBColumnType.DISPLAYNAME + " = ?, " + 
					DBColumnType.IPMASK + " = ?, " + 
					DBColumnType.MAXCONNECTIONS + " = ?, " + 
					DBColumnType.ENABLED + " = ?, " + 
					DBColumnType.DEBUG + " = ?, " + 
					DBColumnType.ADMIN + " = ?, " + 
					DBColumnType.MAIL + " = ?, " + 
					DBColumnType.MAPEXCLUDE + " = ?" + 
					" WHERE " + DBColumnType.USERNAME + " = ?"
				);
		ps_deleteUser = connection.prepareStatement(
				"DELETE FROM " + DB_USER_TABLE + " WHERE " + DBColumnType.USERNAME + " = ?");
		ps_addProfile = connection.prepareStatement(
				"INSERT INTO " + DB_PROFILE_TABLE + " (" +
					DBColumnType.PROFILENAME + ") VALUES(?)",
					Statement.RETURN_GENERATED_KEYS);
		ps_deleteProfile = connection.prepareStatement(
				"DELETE FROM " + DB_PROFILE_TABLE + " WHERE " + DBColumnType.PROFILENAME + " = ?");
		ps_getProfileInfo = connection.prepareStatement(
				"SELECT " + DBColumnType.ALL + " FROM " + DB_PROFILE_TABLE + " WHERE " + DBColumnType.PROFILENAME + " = ?");
		ps_getProfileNames = connection.prepareStatement(
				"SELECT " + DBColumnType.PROFILENAMES + " FROM " + DB_PROFILE_TABLE);
		ps_addUserProfile = connection.prepareStatement(
				"INSERT INTO " + DB_USERS_HAS_PROFILES_TABLE + " (" +
					DBColumnType.USERS_ID + ", " +
					DBColumnType.PROFILES_ID + ") VALUES(?,?)"
				);
		ps_deleteAllUserProfiles = connection.prepareStatement(
				"DELETE FROM " + DB_USERS_HAS_PROFILES_TABLE + " WHERE " + DBColumnType.USERS_ID + " = ?");
		ps_getUserProfiles = connection.prepareStatement(
				"SELECT GROUP_CONCAT(p.profilename SEPARATOR ' ') " +
				"FROM users_has_profiles uhp, profiles p WHERE p.id = uhp.profiles_id AND users_id = ?"
				);
	}
	
	/**
	 * "loads" the database driver and creates a database connection
	 * based on the dataSource Object.
	 * @throws ClassNotFoundException
	 * @throws SQLException
	 */
	private void establishConnection() throws ClassNotFoundException, SQLException {
		Class.forName("com.mysql.jdbc.Driver");
		connection = dataSource.getPooledConnection().getConnection();
	}
	
	/**
	 * sets the inactivity timestamp to the current time.
	 */
	private void setInactivityTimestamp() {
		inactivityTimestamp = System.currentTimeMillis();
	}
	
	/**
	 * close all created PreparedStatements and the database connection.
	 * @throws SQLException
	 */
	public void closeConnection() throws SQLException {
		if (ps_getAll != null)
			ps_getAll.close();
		
		if (ps_getUserInfo != null)
			ps_getUserInfo.close();
		
		if (ps_getUserNames != null)
			ps_getUserNames.close();

		if (ps_userExists != null)
			ps_userExists.close();
		
		if (ps_getUsersCount != null)
			ps_getUsersCount.close();

		if (ps_setUserDebug != null)
			ps_setUserDebug.close();
		
		if (ps_existsTable != null)
			ps_existsTable.close();
		
		if (ps_addUser != null)
			ps_addUser.close();
		
		if (ps_editUser != null)
			ps_editUser.close();
		
		if (ps_deleteUser != null)
			ps_deleteUser.close();
		
		if (ps_addProfile != null)
			ps_addProfile.close();
		
		if (ps_deleteProfile != null)
			ps_deleteProfile.close();
		
		if (ps_getProfileInfo != null)
			ps_getProfileInfo.close();
		
		if (ps_getProfileNames != null)
			ps_getProfileNames.close();
		
		if (ps_addUserProfile != null)
			ps_addUserProfile.close();
		
		if (ps_deleteAllUserProfiles != null)
			ps_deleteAllUserProfiles.close();
		
		if (ps_getUserProfiles != null)
			ps_getUserProfiles.close();
		
		if (connection != null)
			connection.close();
	}
	
	/** 
	 * tests whether the connection is still established and if there are 
	 * reported warnings.
	 * @return TRUE, when no warnings exists and the connections is still open.
	 * @throws SQLException
	 */
	public boolean isHealthy() throws SQLException {
		return !connection.isClosed() && connection.getWarnings() == null;
	}
	
	/**
	 * tests whether the PoolConnection is inactive.
	 * @param inactiveTime - time to check against
	 * @return TRUE, when this PoolConnection is inactive and not used.
	 */
	public boolean isInactive(long inactiveTime) {
		return System.currentTimeMillis() - inactivityTimestamp > inactiveTime;
	}
	
	/**
	 * executes the query for a PreparedStatement, clears the parametes and sets
	 * the inactiviy timestamp afterwards. 
	 * @param ps - PreparedStatement to execute the update for
	 * @return ResultSet containing the query informations.
	 * @throws SQLException
	 */
	private ResultSet executeQuery(PreparedStatement ps) throws SQLException {
		ResultSet result = ps.executeQuery();
		ps.clearParameters();
		setInactivityTimestamp();
		return result;
	}
	
	/**
	 * executes the update for a PreparedStatement, clears the parametes and sets
	 * the inactiviy timestamp afterwards.
	 * @param ps - PreparedStatement to execute the update for
	 * @throws SQLException
	 */
	private void executeUpdate(PreparedStatement ps) throws SQLException {
		ps.executeUpdate();
		ps.clearParameters();
		setInactivityTimestamp();
	}
	
	/**
	 * Returns the ID of the latest prepared statement execution.
	 * For this to work the prepared statement must have been executed
	 * with the parameter "Statement.RETURN_GENERATED_KEYS".
	 * @param ps
	 * @return ID of last query
	 * @throws SQLException
	 */
	private int getIDOfLastQuery(PreparedStatement ps) throws SQLException {
		ResultSet keys = ps.getGeneratedKeys();  
		keys.next();
		int id = keys.getInt(1);
		keys.close();
		return id;
	}
	
	/* ############################################################################################ */
	/* database                                                                       */
	/* ############################################################################################ */
	
	/**
	 * create the "user" table in the MySQL database. 
	 * @throws SQLException
	 */
	public void createUserTable() throws SQLException {
		Statement statement = connection.createStatement();
		statement.executeUpdate(CREATE_USER_TABLE_STMT);
		statement.close();
	}
	
	/**
	 * create the "profile" table in the MySQL database
	 * @throws SQLException
	 */
	public void createProfileTable() throws SQLException {
		Statement statement = connection.createStatement();
		statement.executeUpdate(CREATE_PROFILE_TABLE_STMT);
		statement.close();
	}
	
	/**
	 * create the "users_has_profiles" table in the MySQL datbase.
	 * @throws SQLException
	 */
	public void createUsersHasProfilesTable() throws SQLException {
		Statement statement = connection.createStatement();
		statement.executeUpdate(CREATE_USERS_HAS_PROFILES_TABLE_STMT);
		statement.close();
	}
	
	/**
	 * tests whether the "user" table exists in the MySQL database.
	 * @return TRUE, when user table exists.
	 * @throws SQLException
	 */
	public boolean existsUserTable() throws SQLException {
		return existsTable(dataSource.getDatabaseName(), DB_USER_TABLE);
	}
	
	/**
	 * tests whether the "profile" table exists in the MySQL database.
	 * @return TRUE, when profile table exists.
	 * @throws SQLException
	 */
	public boolean existsProfileTable() throws SQLException {
		return existsTable(dataSource.getDatabaseName(), DB_PROFILE_TABLE);
	}
	
	/**
	 * tests whether the "users_has_profiles" table exists in the MySQL database.
	 * @return TRUE, when users_has_profiles table exists.
	 * @throws SQLException
	 */
	public boolean existsUsersHasProfilesTable() throws SQLException {
		return existsTable(dataSource.getDatabaseName(), DB_USERS_HAS_PROFILES_TABLE);
	}
	
	/**
	 * tests whether a database table exists in the MySQL database.
	 * @param database - defines the database to look in.
	 * @param table - specifies the table to test.
	 * @return TRUE, when table exists.
	 * @throws SQLException
	 */
	private boolean existsTable(String database, String table) throws SQLException {
		ps_existsTable.setString(1, database);
		ps_existsTable.setString(2, table);
		ResultSet resultSet = ps_existsTable.executeQuery();
		boolean result = resultSet.next() && resultSet.getString(1).equalsIgnoreCase(table);
		ps_existsTable.clearParameters();
		resultSet.close();
		setInactivityTimestamp();
		return result;
	}
	
	/**
	 * tests whether the "user" table is empty.
	 * @return TRUE, when "user" table is empty.
	 * @throws SQLException
	 */
	public boolean isUserTableEmpty() throws SQLException {
		ResultSet resultSet = ps_getAll.executeQuery();
		boolean result = !resultSet.next();
		resultSet.close();
		ps_getAll.clearParameters();
		setInactivityTimestamp();
		return result;
	}
	
	/* ############################################################################################ */
	/* users (user table)                                                                           */
	/* ############################################################################################ */
	
	/**
	 * returns the information for one user
	 * @param username - specifies the user
	 * @return ResultSet containing the user informations.
	 * @throws SQLException
	 */
	public ResultSet getUserInfo(String username) throws SQLException {
		ps_getUserInfo.setString(1, username);
		return executeQuery(ps_getUserInfo);
	}
	
	/**
	 * return all user names.
	 * @return ResultSet containing all user names.
	 * @throws SQLException
	 */
	public ResultSet getUserNames() throws SQLException {
		return executeQuery(ps_getUserNames);
	}
	
	/**
	 * returns the number of users in the MySQL database.
	 * @return ResultSet containing the number of users.
	 * @throws SQLException
	 */
	public ResultSet getUsersCount() throws SQLException {
		return executeQuery(ps_getUsersCount);
	}
	
	/**
	 * add user to the MySQL database.
	 */
	public int addUser(String username, String password, String displayname, String ipmask,
			int maxconnections, boolean enabled, boolean debug, boolean admin, 
			String mail, boolean mapexcluded) throws SQLException {
		ps_addUser.setString(1, username);
		ps_addUser.setString(2, password);
		ps_addUser.setString(3, displayname);
		ps_addUser.setString(4, ipmask);
		ps_addUser.setInt(5, maxconnections);
		ps_addUser.setBoolean(6, enabled);
		ps_addUser.setBoolean(7, debug);
		ps_addUser.setBoolean(8, admin);
		ps_addUser.setString(9, mail);
		ps_addUser.setBoolean(10, mapexcluded);
		executeUpdate(ps_addUser);
		return getIDOfLastQuery(ps_addUser);  
	}
	
	/**
	 * edit user in the MySQL database.
	 * @throws SQLException
	 */
	public void editUser(String username, String password, String displayname, String ipmask,
			int maxconnections, boolean enabled, boolean debug, boolean admin, 
			String mail, boolean mapexcluded) throws SQLException {
		ps_editUser.setString(1, password);		
		ps_editUser.setString(2, displayname);
		ps_editUser.setString(3, ipmask);
		ps_editUser.setInt(4, maxconnections);
		ps_editUser.setBoolean(5, enabled);
		ps_editUser.setBoolean(6, debug);
		ps_editUser.setBoolean(7, admin);
		ps_editUser.setString(8, mail);
		ps_editUser.setBoolean(9, mapexcluded);
		ps_editUser.setString(10, username);
		executeUpdate(ps_editUser);
	}
	
	/**
	 * delete specified user from the MySQL database.
	 * @param username - user to delete
	 * @throws SQLException
	 */
	public void deleteUser(String username) throws SQLException {
		ps_deleteUser.setString(1, username);
		executeUpdate(ps_deleteUser);
	}
	
	/**
	 * tests whether the user exists in the MySQL database.
	 * @param user
	 * @return ResultSet containing the number of entries found for the username
	 * @throws SQLException
	 */
	public ResultSet userExists(String username) throws SQLException {
		ps_userExists.setString(1, username);
		return executeQuery(ps_userExists);
	}
	
	/**
	 * set user debug value in the database.
	 * @param username - specifies which user gets changed.
	 * @param debug enable/disable debug
	 * @throws SQLException
	 */
	public void setUserDebug(String username, boolean debug) throws SQLException {
		ps_setUserDebug.setBoolean(1, debug);
		ps_setUserDebug.setString(2, username);
		executeUpdate(ps_setUserDebug);
	}
	
	/* ############################################################################################ */
	/* profiles (profile table)                                                                     */
	/* ############################################################################################ */
	
	/**
	 * add profile to the MySQL database.
	 * @param profilename - profile to add
	 * @throws SQLException
	 */
	public int addProfile(String profileName) throws SQLException {
		ps_addProfile.setString(1, profileName);
		executeUpdate(ps_addProfile);
		return getIDOfLastQuery(ps_addProfile);  
	}
	
	/**
	 * delete profile from the MySQL database.
	 * @param profileName - profile to delete
	 * @throws SQLException
	 */
	public void deleteProfile(String profileName) throws SQLException {
		ps_deleteProfile.setString(1, profileName);
		executeUpdate(ps_deleteProfile);
	}
	
	/**
	 * return profile specified through the profileName parameter from the MySQL database.
	 * @param profileName - profile to get
	 * @return ResultSet containing profile informations
	 * @throws SQLException
	 */
	public ResultSet getProfileInfo(String profileName) throws SQLException {
		ps_getProfileInfo.setString(1, profileName);
		return executeQuery(ps_getProfileInfo);
	}
	
	/**
	 * returns all profile names.
	 * @return ResultSet containing all profile names.
	 * @throws SQLException
	 */
	public ResultSet getProfileNames() throws SQLException {
		return executeQuery(ps_getProfileNames);
	}
	
	/* ############################################################################################ */
	/* profiles/users (users_has_profiles table)                                                    */
	/* ############################################################################################ */
	
	/**
	 * add user profile relation to the MySQL database.
	 * @param userID - user to add
	 * @param profileID - profile to add for user
	 * @throws SQLException
	 */
	public void addUserProfile(int userID, int profileID) throws SQLException {
		ps_addUserProfile.setInt(1, userID);
		ps_addUserProfile.setInt(2, profileID);
		executeUpdate(ps_addUserProfile);
	}
	
	/**
	 * delete all user profile relations in the MySQL database.
	 * @param userID - entries related to the specified user
	 * @throws SQLException
	 */
	public void deleteAllUserProfiles(int userID) throws SQLException {
		ps_deleteAllUserProfiles.setInt(1, userID);
		executeUpdate(ps_deleteAllUserProfiles);
	}
	
	/**
	 * returns all profiles for the specified user.
	 * @param userID - specifies the user
	 * @return ResultSet containing profile ids.
	 * @throws SQLException
	 */
	public ResultSet getUserProfiles(int userID) throws SQLException {
		ps_getUserProfiles.setInt(1, userID);
		return executeQuery(ps_getUserProfiles);
	}	
}
