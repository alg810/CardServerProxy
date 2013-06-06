package com.bowman.cardserv;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2011-07-01
 * Time: 22:03
 */
public class CacheCoverageMap extends LinkedHashMap {

  private long maxAge;
  protected long windowStart; // arbitrary point in time
  protected String key;
  protected boolean analyzeOverwrites, analyzeContinuity;

  public CacheCoverageMap(String key, long maxAge) {
    this.key = key;
    this.maxAge = maxAge;
  }

  protected boolean removeEldestEntry(Map.Entry eldest) {
    CamdNetMessage msg = ((ServiceCacheEntry)eldest.getValue()).request;
    return msg == null || System.currentTimeMillis() - msg.getTimeStamp() > maxAge;
  }

}
