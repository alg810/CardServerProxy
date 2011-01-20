package com.bowman.cardserv.mysql;

import java.util.Set;


/**
 * a simple user object.
 * 
 * @author DonCarlo
 * @since 14.12.2010
 */
public class User {

	/* user informations */
	private int id;
	private String username;
	private String password;
	private String displayName;
	private int maxConnections;
	private String ipMask;
	private String email;
	private Set allowedProfiles;
	private boolean isEnabled;
	private boolean isAdmin;
	private boolean isDebug;
	private boolean mapExcluded;
	
	public User(String username, String password, String displayName, int maxConnections, String ipMask, 
			String email, Set allowedProfiles, boolean isEnabled, boolean isAdmin, boolean isDebug, boolean mapExcluded) {
		this(-1, username, password, displayName, maxConnections, ipMask, email, allowedProfiles, isEnabled, isAdmin, isDebug, mapExcluded);
	}
	
	public User(int id, String username, String password, String displayName, int maxConnections, String ipMask, 
			String email, boolean isEnabled, boolean isAdmin, boolean isDebug, boolean mapExcluded) {
		this(id, username, password, displayName, maxConnections, ipMask, email, null, isEnabled, isAdmin, isDebug, mapExcluded);
	}
	
	public User(int id, String username, String password, String displayName, int maxConnections, String ipMask, 
			String email, Set allowedProfiles, boolean isEnabled, boolean isAdmin, boolean isDebug, boolean mapExcluded) {
		this.id = id;
		this.username = username;
		this.password = password;
		this.displayName = displayName;
		this.maxConnections = maxConnections;
		this.ipMask = ipMask;
		this.email = email;
		this.allowedProfiles = allowedProfiles;
		this.isEnabled = isEnabled;
		this.isAdmin = isAdmin;
		this.isDebug = isDebug;
		this.mapExcluded = mapExcluded;
	}
	
	/**
	 * @return the id
	 */
	public int getId() {
		return id;
	}

	/**
	 * @return the displayName
	 */
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * @return the maxConnections
	 */
	public int getMaxConnections() {
		return maxConnections;
	}

	/**
	 * @return the ipMask
	 */
	public String getIpMask() {
		return ipMask;
	}

	/**
	 * @return the email
	 */
	public String getEmail() {
		return email;
	}

	/**
	 * @return the allowedProfiles
	 */
	public Set getAllowedProfiles() {
		return allowedProfiles;
	}

	/**
	 * @return the isEnabled
	 */
	public boolean isEnabled() {
		return isEnabled;
	}

	/**
	 * @return the isAdmin
	 */
	public boolean isAdmin() {
		return isAdmin;
	}

	/**
	 * @return the isDebug
	 */
	public boolean isDebug() {
		return isDebug;
	}

	/**
	 * @return the mapExcluded
	 */
	public boolean isMapExcluded() {
		return mapExcluded;
	}

	/**
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * @return the password
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * @param allowedProfiles the allowedProfiles to set
	 */
	public void setAllowedProfiles(Set allowedProfiles) {
		this.allowedProfiles = allowedProfiles;
	}

	public String toString() {
		return "username: " + username + " " +
			"password: " + password + " " +
			"displayName: " + displayName + " " +
			"maxConnections: " + maxConnections + " " +
			"ipMask: " + ipMask + " " +
			"email: " + email + " " +
			"isEnabled: " + isEnabled + " " +
			"isAdmin: " + isAdmin + " " +
			"isDebug: " + isDebug + " " +
			"mapExcluded: " + mapExcluded + " "
			;
	}
	
}
