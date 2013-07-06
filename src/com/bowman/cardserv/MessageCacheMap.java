package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.StaleEntryListener;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Sep 9, 2010
 * Time: 3:15:35 PM
 */
public class MessageCacheMap extends LinkedHashMap {

  private long maxAge, eldestAge;
  private StaleEntryListener listener;

  public MessageCacheMap(long maxAge) {
    this.maxAge = maxAge;
  }

  protected boolean removeEldestEntry(Map.Entry eldest) {
    CamdNetMessage msg = null;
    if(eldest.getKey() instanceof CamdNetMessage) msg = (CamdNetMessage)eldest.getKey();
    else if(eldest.getValue() instanceof CamdNetMessage) msg = (CamdNetMessage)eldest.getValue();
    else {
      Set s = (Set)eldest.getValue();
      if(s != null && s.iterator() != null) msg = (CamdNetMessage)s.iterator().next();
    }
    if(msg == null) {
      eldestAge = -1;
      return true;
    }
    eldestAge = System.currentTimeMillis() - msg.getTimeStamp();
    if(eldestAge > maxAge) {
      if(listener != null) listener.onRemoveStale(msg);
      return true;
    } else return false;
  }

  public void setStaleEntryListener(StaleEntryListener listener) {
    this.listener = listener;
  }

  public long getMaxAge() {
    return maxAge;
  }

  public long getEldestAge() {
    return eldestAge;
  }

  public void setMaxAge(long maxAge) {
    if(maxAge < this.maxAge) {
      long now = System.currentTimeMillis();
      Object key; CamdNetMessage msg;
      for(Iterator iter = keySet().iterator(); iter.hasNext(); ) {
        key = iter.next();
        if(key instanceof CamdNetMessage) msg = (CamdNetMessage)key;
        else msg = (CamdNetMessage)get(key);
        if(now - msg.getTimeStamp() > maxAge) iter.remove();
      }
    }
    this.maxAge = maxAge;
  }
}
