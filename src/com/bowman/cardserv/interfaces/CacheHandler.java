package com.bowman.cardserv.interfaces;

import com.bowman.cardserv.CamdNetMessage;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 8, 2005
 * Time: 4:15:27 PM
 */
public interface CacheHandler extends XmlConfigurable {

  void start();
  CamdNetMessage processRequest(int successFactor, CamdNetMessage request, boolean alwaysWait, long maxCwWait);
  boolean processReply(CamdNetMessage request, CamdNetMessage reply);
  CamdNetMessage peekReply(CamdNetMessage request);
  long getMaxCacheWait(long maxCwWait);
  boolean containsCaid(int caid);
  void processReplies(Map replies);

  Properties getUsageStats();
  void setListener(CacheListener listener);
  CacheListener getListener();
  void setMonitor(CacheListener monitor);
  CacheListener getMonitor();

}
