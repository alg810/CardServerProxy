package com.bowman.cardserv.mysql;

/**
 * Because java 1.4 does not contain enum types i created my own.
 * 
 * @author DonCarlo
 * @since 04.12.2010
 */
public class DBColumnType implements Comparable {
    private final String name;
    
    // used for the users table
    public static final DBColumnType USERNAME = new DBColumnType("username");
    public static final DBColumnType PASSWORD = new DBColumnType("password");
    public static final DBColumnType DISPLAYNAME = new DBColumnType("displayname");
    public static final DBColumnType IPMASK = new DBColumnType("ipmask");
    public static final DBColumnType MAXCONNECTIONS = new DBColumnType("maxconnections");
    public static final DBColumnType ENABLED = new DBColumnType("enabled");
    public static final DBColumnType DEBUG = new DBColumnType("debug");
    public static final DBColumnType ADMIN = new DBColumnType("admin");
    public static final DBColumnType MAIL = new DBColumnType("email");
    public static final DBColumnType MAPEXCLUDE = new DBColumnType("mapexclude");
    public static final DBColumnType USERS = new DBColumnType("GROUP_CONCAT(username SEPARATOR ' ')");
    // used for the profiles table
    public static final DBColumnType PROFILENAME = new DBColumnType("profilename");
    public static final DBColumnType PROFILENAMES = new DBColumnType("GROUP_CONCAT(profilename SEPARATOR ' ')");
    // used for the users_has_profiles table
    public static final DBColumnType USERS_ID = new DBColumnType("users_id");
    public static final DBColumnType PROFILES_ID = new DBColumnType("profiles_id");
    // some global ones
    public static final DBColumnType ID = new DBColumnType("id");
    public static final DBColumnType ALL = new DBColumnType("*");
    public static final DBColumnType COUNT = new DBColumnType("COUNT(*)");
    
    // Ordinal of next suit to be created
    private static int nextOrdinal = 0;
   
    // Assign an ordinal to this suit
    private final int ordinal = nextOrdinal++;
    
    private DBColumnType(String name){
        this.name =name;
    }
    public String toString(){
        return name;
    }
    public int compareTo(Object o){
       return ordinal -((DBColumnType)o).ordinal;
    }
}