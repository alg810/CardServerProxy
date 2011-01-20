package com.bowman.cardserv.mysql;

/**
 * @author DonCarlo
 * @since 13.12.2010
 */
public class PreparedStatementType implements Comparable {
    private final String name;
    
    public static final PreparedStatementType GET_ALL = new PreparedStatementType("get_all");
    public static final PreparedStatementType GET_USER_INFO = new PreparedStatementType("get_user_info");
    public static final PreparedStatementType GET_USERNAMES = new PreparedStatementType("get_usernames");
    public static final PreparedStatementType GET_USER_EXISTS = new PreparedStatementType("get_user_exists");
    public static final PreparedStatementType GET_USERS_COUNT = new PreparedStatementType("get_user_count");
    public static final PreparedStatementType SET_USER_DEBUG = new PreparedStatementType("set_user_debug");
    public static final PreparedStatementType EXISTS_USER_TABLE = new PreparedStatementType("exists_user_table");
    public static final PreparedStatementType ADD_USER = new PreparedStatementType("add_user");
    public static final PreparedStatementType EDIT_USER = new PreparedStatementType("edit_user");
    public static final PreparedStatementType DELETE_USER = new PreparedStatementType("delete_user");
    public static final PreparedStatementType ADD_PROFILE = new PreparedStatementType("add_profile");
    public static final PreparedStatementType EDIT_PROFILE = new PreparedStatementType("edit_profile");
    public static final PreparedStatementType DELETE_PROFILE = new PreparedStatementType("delete_profile");
    public static final PreparedStatementType GET_PROFILENAMES = new PreparedStatementType("get_profilenames");
    public static final PreparedStatementType ADD_USER_PROFILE = new PreparedStatementType("add_user_profile");
    
    // Ordinal of next suit to be created
    private static int nextOrdinal = 0;
   
    // Assign an ordinal to this suit
    public final int ordinal = nextOrdinal++;
    
    private PreparedStatementType(String name){
        this.name =name;
    }
    public String toString(){
        return name;
    }
    public int compareTo(Object o){
       return ordinal -((PreparedStatementType)o).ordinal;
    }
}