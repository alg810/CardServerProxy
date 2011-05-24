package com.bowman.cardserv.interfaces;

public interface MySQLConstants {
	
	public static final int DEFAULT_SKIP_ROWS = 0;
	public static final int DEFAULT_NUM_ROWS = Integer.MAX_VALUE;
	
	// database tables
	public static final String 
		DBT_USERS = "users",
		DBT_PROFILES = "profiles",
		DBT_USERS_HAS_PROFILES = "users_has_profiles";
	
	// users table columns
	public static final String 
		DBC_USERS_ID = "id",
		DBC_USERS_USERNAME = "username",
		DBC_USERS_PASSWORD = "password",
		DBC_USERS_DISPLAYNAME = "displayname",
		DBC_USERS_IPMASK = "ipmask",
		DBC_USERS_MAXCONNECTIONS = "maxconnections",
		DBC_USERS_ENABLED = "enabled",
		DBC_USERS_DEBUG = "debug",
		DBC_USERS_ADMIN = "admin",
		DBC_USERS_MAIL = "email",
		DBC_USERS_MAPEXCLUDE = "mapexclude",
		// extra profile field
		DBC_USERS_PROFILES = "profiles";
	
	// profiles table columns
	public static final String 
		DBC_PROFILES_ID = "id",
		DBC_PROFILES_PROFILENAME = "profilename";
	
	// users_has_profiles table columns
	public static final String 
		DBC_USERS_HAS_PROFILES_USERS_ID = "users_id",
		DBC_USERS_HAS_PROFILES_PROFILES_ID = "profiles_id";
	
	// prepared Statements
	public static final String
		PS_GET_USER_BY_NAME = 
			"SELECT " + DBT_USERS + ".*, GROUP_CONCAT(" + DBT_PROFILES + "." + DBC_PROFILES_PROFILENAME + " SEPARATOR ' ') AS " + DBC_USERS_PROFILES +
			" FROM " + DBT_USERS_HAS_PROFILES +
			" RIGHT JOIN " + DBT_USERS + " ON (" + DBT_USERS + "." + DBC_USERS_ID + " = " + DBT_USERS_HAS_PROFILES + "." + DBC_USERS_HAS_PROFILES_USERS_ID + ")" +
			" LEFT JOIN " + DBT_PROFILES + " ON (" + DBT_PROFILES + "." + DBC_PROFILES_ID + " = " + DBT_USERS_HAS_PROFILES + "." + DBC_USERS_HAS_PROFILES_PROFILES_ID + ")" +
			" WHERE " + DBT_USERS + "." + DBC_USERS_USERNAME + " = ?",
		PS_ADD_USER =
			"INSERT INTO " + DBT_USERS + " (" +
				DBC_USERS_USERNAME + ", " + DBC_USERS_PASSWORD + ", " + DBC_USERS_DISPLAYNAME + ", " +
				DBC_USERS_IPMASK + ", " + DBC_USERS_MAXCONNECTIONS + ", " + DBC_USERS_ENABLED + ", " +
				DBC_USERS_DEBUG + ", " + DBC_USERS_ADMIN + ", " + DBC_USERS_MAIL + ", " + DBC_USERS_MAPEXCLUDE + 
			") VALUES(?,?,?,?,?,?,?,?,?,?)",
		PS_EDIT_USER =
			"UPDATE " + DBT_USERS + " SET " + 
				DBC_USERS_USERNAME + " = ?, " + DBC_USERS_PASSWORD + " = ?, " + DBC_USERS_DISPLAYNAME + " = ?, " + 
				DBC_USERS_IPMASK + " = ?, " + DBC_USERS_MAXCONNECTIONS + " = ?, " + DBC_USERS_ENABLED + " = ?, " + 
				DBC_USERS_DEBUG + " = ?, " + DBC_USERS_ADMIN + " = ?, " + DBC_USERS_MAIL + " = ?, " + DBC_USERS_MAPEXCLUDE + " = ?" + 
			" WHERE " + DBC_USERS_ID + " = ?",
		PS_DELETE_USER_BY_NAME =
			"DELETE FROM " + DBT_USERS + " WHERE " + DBC_USERS_USERNAME + " = ?",
		PS_DELETE_ALL_USERS =
			"DELETE FROM " + DBT_USERS + " WHERE NOT " + DBC_USERS_USERNAME + " = ?",
		PS_SET_USER_DEBUG =
			"UPDATE " + DBT_USERS + " SET " + DBC_USERS_DEBUG + " = ? WHERE " + DBC_USERS_USERNAME + " = ?",
		PS_GET_USERNAMES =
			"SELECT " + DBC_USERS_USERNAME + " FROM " + DBT_USERS + " LIMIT ?,?";
	
	public static final String
		PS_GET_PROFILE_BY_ID =
			"SELECT * FROM " + DBT_PROFILES + " WHERE " + DBC_PROFILES_ID + " = ?",
		PS_GET_PROFILE_BY_NAME =
			"SELECT * FROM " + DBT_PROFILES + " WHERE " + DBC_PROFILES_PROFILENAME + " = ?",
		PS_GET_PROFILES =
			"SELECT * FROM " + DBT_PROFILES,
		PS_ADD_PROFILE =
			"INSERT INTO " + DBT_PROFILES + " (" + DBC_PROFILES_PROFILENAME + ") VALUES(?)",
		PS_EDIT_PROFILE =
			"UPDATE " + DBT_PROFILES + " SET " + DBC_PROFILES_PROFILENAME + " = ?" + " WHERE " + DBC_PROFILES_ID + " = ?",
		PS_DELETE_PROFILE =
			"DELETE FROM " + DBT_PROFILES + " WHERE " + DBC_PROFILES_ID + " = ?",
		PS_DELETE_ALL_PROFILES =
			"DELETE FROM " + DBT_PROFILES,
		PS_ADD_USER_PROFILES =
			"INSERT INTO " + DBT_USERS_HAS_PROFILES + " (" + DBC_USERS_HAS_PROFILES_USERS_ID + "," +
			DBC_USERS_HAS_PROFILES_PROFILES_ID +") VALUES(?,?)";
	
/**,	PS_GET_ALL_PROFILES = 
			"SELECT * FROM " + DBT_PROFILES,
		PS_GET_PROFILE_BY_NAME =
			"SELECT * FROM " + DBT_PROFILES + " WHERE " + DBC_PROFILES_PROFILENAME + " = ?",
*/

	public static final String
		PS_EXISTS_TABLE =
			"SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_name = ?";
	
	public static final String
		PS_ADD_USER_PROFILE =
			"INSERT INTO " + DBT_USERS_HAS_PROFILES + " (" + 
				DBC_USERS_HAS_PROFILES_USERS_ID + ", " + DBC_USERS_HAS_PROFILES_PROFILES_ID + 
			") VALUES(?,?)",
		PS_DELETE_USER_PROFILES =
			"DELETE FROM " + DBT_USERS_HAS_PROFILES + " WHERE " + DBC_USERS_HAS_PROFILES_USERS_ID + " = ?";
	
	// table creation stmts
	public static final String CREATE_USER_TABLE_STMT = 
		"CREATE TABLE IF NOT EXISTS " + DBT_USERS + " (" +
		"`" + DBC_USERS_ID + "` INT(10) UNSIGNED NOT NULL AUTO_INCREMENT, " +
		"`" + DBC_USERS_USERNAME + "` VARCHAR(30) NOT NULL, " +
		"`" + DBC_USERS_PASSWORD + "` VARCHAR(30), " +
		"`" + DBC_USERS_DISPLAYNAME + "` VARCHAR(50), " +
		"`" + DBC_USERS_IPMASK + "` VARCHAR(30), " +
		"`" + DBC_USERS_MAXCONNECTIONS + "` TINYINT UNSIGNED, " +
		"`" + DBC_USERS_ENABLED + "` TINYINT(1), " +
		"`" + DBC_USERS_DEBUG + "` TINYINT(1), " +
		"`" + DBC_USERS_ADMIN + "` TINYINT(1), " +
		"`" + DBC_USERS_MAIL + "` VARCHAR(30), " +
		"`" + DBC_USERS_MAPEXCLUDE + "` TINYINT(1), " +
		"PRIMARY KEY (`" + DBC_USERS_ID + "`), " +
		"UNIQUE (`" + DBC_USERS_USERNAME + "` ASC) " +
		") ENGINE = InnoDB DEFAULT CHARACTER SET = latin1"
	;

	public static final String CREATE_PROFILE_TABLE_STMT = 
		"CREATE TABLE IF NOT EXISTS " + DBT_PROFILES + " (" +
		"`" + DBC_PROFILES_ID + "` INT(10) UNSIGNED NOT NULL AUTO_INCREMENT, " +
		"`" + DBC_PROFILES_PROFILENAME + "` VARCHAR(60) NOT NULL, " +
		"PRIMARY KEY (`" + DBC_PROFILES_ID + "`), " +
		"UNIQUE (`" + DBC_PROFILES_PROFILENAME + "` ASC) " +
		") ENGINE = InnoDB DEFAULT CHARACTER SET = latin1"
	;

	public static final String CREATE_USERS_HAS_PROFILES_TABLE_STMT = 
		"CREATE TABLE IF NOT EXISTS `" + DBT_USERS_HAS_PROFILES + "` (" +
		"`" + DBC_USERS_HAS_PROFILES_USERS_ID + "` INT(10) UNSIGNED NOT NULL, " +
		"`" + DBC_USERS_HAS_PROFILES_PROFILES_ID + "` INT(10) UNSIGNED NOT NULL, " +
		"PRIMARY KEY (`" + DBC_USERS_HAS_PROFILES_USERS_ID + "`, `" + DBC_USERS_HAS_PROFILES_PROFILES_ID + "`), " +
		"INDEX `fk_users_has_profiles_profiles1` (`" + DBC_USERS_HAS_PROFILES_PROFILES_ID + "` ASC), " +
		"CONSTRAINT `fk_users_has_profiles_users` " +
			"FOREIGN KEY (`" + DBC_USERS_HAS_PROFILES_USERS_ID + "` ) " +
			"REFERENCES `" + DBT_USERS + "` (`" + DBC_USERS_ID + "` ) " +
			"ON DELETE CASCADE " +
			"ON UPDATE CASCADE, " +
		"CONSTRAINT `fk_users_has_profiles_profiles1` " +
			"FOREIGN KEY (`" + DBC_USERS_HAS_PROFILES_PROFILES_ID + "` ) " +
			"REFERENCES `" + DBT_PROFILES + "` (`" + DBC_PROFILES_ID + "` ) " +
			"ON DELETE CASCADE " +
			"ON UPDATE CASCADE" +
		") ENGINE = InnoDB DEFAULT CHARACTER SET = latin1"
	;

}
