package com.bowman.cardserv.mysql;

/**
 * A simple profile cache object which keeps the cached data as an Profile object and the 
 * informations needed to refresh or dispose this cache entry.
 * 
 * @author DonCarlo
 * @since 30.12.2010
 */
public class CacheProfile {

	private final long expireTime = 60000;
	private final double refreshAheadFactor = 0.75;
	private final double refreshTime = expireTime * refreshAheadFactor;
	private long lastUsedTimestamp = 0;
	private long expireTimestamp = 0;
	private double refreshTimestamp = 0;
	private Profile profile = null;
	
	public CacheProfile(Profile profile) {
		setProfile(profile);
	}
	
	public boolean isExpired() {
		return System.currentTimeMillis() > expireTimestamp;
	}
	
	public boolean needsRefresh() {
		return (lastUsedTimestamp > refreshTimestamp) && (lastUsedTimestamp < expireTimestamp);
	}
	
	public Profile getProfile() {
		lastUsedTimestamp = System.currentTimeMillis();
		return profile;
	}
	
	public void setProfile(Profile profile) {
		expireTimestamp = System.currentTimeMillis() + expireTime;
		refreshTimestamp = expireTimestamp - refreshTime;
		this.profile = profile;
	}
	
	public String getIdentifier() {
		return profile.getName();
	}
}
