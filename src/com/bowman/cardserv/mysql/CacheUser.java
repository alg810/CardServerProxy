package com.bowman.cardserv.mysql;

/**
 * A simple user cache object which keeps the cached data as an User object and the 
 * informations needed to refresh or dispose this cache entry.
 * 
 * @author DonCarlo
 * @since 14.12.2010
 */
public class CacheUser {
	
	private final long expireTime = 60000;
	private final double refreshAheadFactor = 0.75;
	private final double refreshTime = expireTime * refreshAheadFactor;
	private long lastUsedTimestamp = 0;
	private long expireTimestamp = 0;
	private double refreshTimestamp = 0;
	private User user = null;
	
	public CacheUser(User user) {
		setUser(user);
	}
	
	public boolean isExpired() {
		return System.currentTimeMillis() > expireTimestamp;
	}
	
	public boolean needsRefresh() {
		return (lastUsedTimestamp > refreshTimestamp) && (lastUsedTimestamp < expireTimestamp);
	}
	
	public User getUser() {
		lastUsedTimestamp = System.currentTimeMillis();
		return user;
	}
	
	public void setUser(User user) {
		expireTimestamp = System.currentTimeMillis() + expireTime;
		refreshTimestamp = expireTimestamp - refreshTime;
		this.user = user;
	}
	
	public String getIdentifier() {
		return user.getUsername();
	}
}
