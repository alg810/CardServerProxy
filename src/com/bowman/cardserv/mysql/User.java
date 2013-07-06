package com.bowman.cardserv.mysql;

import java.util.Set;

/**
 * a simple user object.
 * 
 * @author DonCarlo
 * @since 14.12.2010
 */
public class User {

	private int id;
	private String userName, password, displayName;
	private int maxConnections;
	private String ipMask, email;
	private Set allowedProfiles;
	private boolean isEnabled, isAdmin, isDebug, mapExcluded;
	
	public User(String userName, String password, String displayName, int maxConnections, String ipMask, String email,
              Set allowedProfiles, boolean isEnabled, boolean isAdmin, boolean isDebug, boolean mapExcluded)
  {
		this(-1, userName, password, displayName, maxConnections, ipMask, email, allowedProfiles, isEnabled, isAdmin, isDebug, mapExcluded);
	}
	
	public User(int id, String userName, String password, String displayName, int maxConnections, String ipMask, String email,
              boolean isEnabled, boolean isAdmin, boolean isDebug, boolean mapExcluded)
  {
		this(id, userName, password, displayName, maxConnections, ipMask, email, null, isEnabled, isAdmin, isDebug, mapExcluded);
	}
	
	public User(int id, String userName, String password, String displayName, int maxConnections, String ipMask,
              String email, Set allowedProfiles, boolean isEnabled, boolean isAdmin, boolean isDebug, boolean mapExcluded)
  {
		this.id = id;
		this.userName = userName;
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

	public int getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public int getMaxConnections() {
		return maxConnections;
	}

	public String getIpMask() {
		return ipMask;
	}

	public String getEmail() {
		return email;
	}

	public Set getAllowedProfiles() {
		return allowedProfiles;
	}

	public boolean isEnabled() {
		return isEnabled;
	}

	public boolean isAdmin() {
		return isAdmin;
	}

	public boolean isDebug() {
		return isDebug;
	}

	public boolean isMapExcluded() {
		return mapExcluded;
	}

	public String getUserName() {
		return userName;
	}

	public String getPassword() {
		return password;
	}

	public void setAllowedProfiles(Set allowedProfiles) {
		this.allowedProfiles = allowedProfiles;
	}

	public String toString() {
		return "username: " + userName + " " +
			"password: " + password + " " +
			"displayName: " + displayName + " " +
			"maxConnections: " + maxConnections + " " +
			"ipMask: " + ipMask + " " +
			"email: " + email + " " +
			"isEnabled: " + isEnabled + " " +
			"isAdmin: " + isAdmin + " " +
			"isDebug: " + isDebug + " " +
			"mapExcluded: " + mapExcluded + " ";
	}	
}
