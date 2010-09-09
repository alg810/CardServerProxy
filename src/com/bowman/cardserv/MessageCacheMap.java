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

  private long maxAge;
  private StaleEntryListener listener;

  public MessageCacheMap(long maxAge) {
    this.maxAge = maxAge;
  }

  protected boolean removeEldestEntry(Map.Entry eldest) {
    CamdNetMessage msg = (CamdNetMessage)eldest.getKey();
    if(System.currentTimeMillis() - msg.getTimeStamp() > maxAge) {
      if(listener != null) listener.onRemoveStale(msg);
      return true;
    } else return false;
  }

  public void setStaleEntryListener(StaleEntryListener listener) {
    this.listener = listener;
  }

  public void setMaxAge(long maxAge) {
    if(maxAge < this.maxAge) {
      long now = System.currentTimeMillis();
      CamdNetMessage msg;
      for(Iterator iter = keySet().iterator(); iter.hasNext(); ) {
        msg = (CamdNetMessage)iter.next();
        if(now - msg.getTimeStamp() > maxAge) iter.remove();
      }
    }
    this.maxAge = maxAge;
  }
}
