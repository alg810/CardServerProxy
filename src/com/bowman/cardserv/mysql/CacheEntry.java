package com.bowman.cardserv.mysql;

/**
 * A simple profile cache object which keeps the cached data and the 
 * informations needed to refresh or dispose this cache entry.
 * 
 * @author DonCarlo
 * @since 29.01.2011
 */
public class CacheEntry {

	private final long expireTime = 60000;
	private final double refreshAheadFactor = 0.75;
	private final double refreshTime = expireTime * refreshAheadFactor;
	private long lastUsedTimestamp = 0;
	private long expireTimestamp = 0;
	private double refreshTimestamp = 0;
	private Object data = null;
	
	public CacheEntry(Object data) {
		setData(data);
	}
	
	public boolean isExpired() {
		return System.currentTimeMillis() > expireTimestamp;
	}
	
	public boolean needsRefresh() {
		return (lastUsedTimestamp > refreshTimestamp) && (lastUsedTimestamp < expireTimestamp);
	}
	
	public Object getData() {
		lastUsedTimestamp = System.currentTimeMillis();
		return data;
	}
	
	public void setData(Object data) {
		expireTimestamp = System.currentTimeMillis() + expireTime;
		refreshTimestamp = expireTimestamp - refreshTime;
		this.data = data;
	}
}
