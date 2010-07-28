package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.*;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 8, 2005
 * Time: 5:54:37 PM
 */
public class DefaultCache implements CacheHandler {

  protected ProxyLogger logger;
  protected Map pendingEcms = new HashMap();
  protected Map ecmMap = new LinkedHashMap();
  protected CacheListener listener;

  private long maxAge;
  private long maxCacheWait;
  private int maxWaitPercent;

  private int timeouts, instantHits, waitHits, remoteHits;

  public DefaultCache() {
    logger = ProxyLogger.getLabeledLogger(getClass().getName());
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    maxAge = xml.getTimeValue("cw-max-age", "s");
    String maxWaitStr = xml.getStringValue("max-cache-wait");
    if(maxWaitStr.endsWith("%")) {
      maxCacheWait = -1;
      maxWaitStr = maxWaitStr.substring(0, maxWaitStr.length() - 1);
      maxWaitPercent = Integer.parseInt(maxWaitStr);
      if(maxWaitPercent < 1 || maxWaitPercent > 100)
        throw new ConfigException(xml.getFullName(), "max-cache-wait", "Must be a time value or 1-100%");
    } else {
      maxWaitPercent = -1;
      maxCacheWait = xml.getTimeValue("max-cache-wait", "s");
    }
  }

  public long getMaxCacheWait(long maxCwWait) {
    if(maxCacheWait == -1) return (long)((maxWaitPercent / 100.0) * maxCwWait);
    else return Math.min(maxCwWait, maxCacheWait);
  }

  public void start() {
    timeouts = 0;
    instantHits = 0;
    waitHits = 0;
  }

  public CamdNetMessage processRequest(int successFactor, CamdNetMessage request, boolean alwaysWait, long maxCwWait) {
    long start = System.currentTimeMillis();
    long maxWait = getMaxCacheWait(maxCwWait);

    synchronized(this) {
      
      boolean waited = false;
      long delay;

      if(!ecmMap.containsKey(request)) {

        if(alwaysWait) {
          // no cached or pending transaction but cache-only mode is set, so assume another proxy will provide cw
          if(!pendingEcms.containsKey(request)) addRequest(request, alwaysWait);
        }
      
        while(pendingEcms.containsKey(request)) {

          String origin = ((CamdNetMessage)pendingEcms.get(request)).getOriginAddress();

          if(origin != null && origin.equals(request.getRemoteAddress())) {
            // System.out.println("Skipping cache lock for " + request.hashCodeStr()  + " (cache origin: " + origin +
            //    " client ip: " + request.getRemoteAddress() + ")");
            break;
          }

          try {
            wait(500);
          } catch(InterruptedException e) {
            break;
          }
          delay = System.currentTimeMillis() - start;
          logger.finest("Waited for " + request.hashCodeStr() + " in cache: " + delay + " ms");
          waited = true;
          if(delay >= maxWait) {
            break;
          }
        }
      }

      request.setCacheTime(request.getCacheTime() + (System.currentTimeMillis() - start));

      if(ecmMap.containsKey(request)) {
        CamdNetMessage reply = new CamdNetMessage((CamdNetMessage)ecmMap.get(request)); // return copy
        if(request.getServiceId() != 0) reply.setServiceId(request.getServiceId());
        if(waited) {
          waitHits++;
          reply.setInstant(false);
        } else {
          instantHits++;
          reply.setInstant(true);
        }
        if(reply.getOriginAddress() != null) remoteHits++;
        return reply;
      } else {

        if(waited) {
          pendingEcms.remove(request);
          timeouts++;
          request.setTimeOut(true);

          // todo
          // System.err.println(new Date() + " - " + Thread.currentThread().getName() + " - Cache timeout waiting (" + request.getCacheTime() + ") request: " + request.hashCodeStr() + " " + request.getDataLength() + " [" + request.getServiceId() + "]");
          return null;
        }

        addRequest(request, alwaysWait);
      }
      return null;
    }

  }

  public synchronized boolean processReply(CamdNetMessage request, CamdNetMessage reply) {
    if(reply == null || reply.isEmpty()) {
      // System.err.println(new Date() + " - " + Thread.currentThread() + " - Got empty reply for cache request " + request.hashCodeStr() + ", " + request.getDataLength() + " [" + request.getServiceId() + "] " + request.getOriginAddress());
      removeRequest(request);
      notifyAll();
    } else {
      if(pendingEcms.containsKey(request)) {
        addReply(request, reply);
        logger.finer("Reply received: " + request.hashCodeStr() + " -> " + reply.hashCodeStr() +
            " (pending: " + pendingEcms.size() + ")");
        removeRequest(request);
        notifyAll();
        return true;
      }
      // todo
      // if(!contains(request)) System.err.println(new Date() + " - " + Thread.currentThread() + " - Cache request " + request.hashCodeStr() + " not found pending for reply: " + reply.hashCodeStr() + ", " + request.getDataLength() + " [" + request.getServiceId() + "] " + reply.getOriginAddress());
      addReply(request, reply);
    }
    return false;
  }

  public CamdNetMessage peekReply(CamdNetMessage request) {
    return (CamdNetMessage)ecmMap.get(request);
  }

  public Properties getUsageStats() {
    Properties p = new Properties();
    p.setProperty("pending-ecms", String.valueOf(pendingEcms.size()));
    p.setProperty("cached-ecms", String.valueOf(ecmMap.size()));
    p.setProperty("timeouts", String.valueOf(timeouts));
    p.setProperty("instant-hits", String.valueOf(instantHits));
    p.setProperty("wait-hits", String.valueOf(waitHits));
    if(remoteHits > 0) p.setProperty("remote-hits", String.valueOf(remoteHits));    
    return p;
  }

  protected synchronized boolean contains(CamdNetMessage request) {
    return ecmMap.containsKey(request);
  }

  protected synchronized boolean containsPending(CamdNetMessage request) {
    return pendingEcms.containsKey(request);
  }

  protected synchronized void addRequest(CamdNetMessage request, boolean alwaysWait) {
    CamdNetMessage oldRequest = (CamdNetMessage)pendingEcms.put(request, request);
    if(oldRequest == null && listener != null) listener.onRequest(request);
    /*
    if(!pendingEcms.add(request)) { // todo
      // System.err.println(new Date() + " - " + Thread.currentThread().getName() + " - Cache already had request: " + request.hashCodeStr() + " " + request.getDataLength() + " [" + request.getServiceId() + "]");
    } else {
      logger.finest("Added request: " + request.hashCodeStr());
    }
    */
  }

  protected synchronized void addReply(CamdNetMessage request, CamdNetMessage reply) {
    cleanOld();
    if(reply.isEmpty()) return; // bad reply = unable to decode
    CamdNetMessage oldReply = (CamdNetMessage)ecmMap.put(request, reply);
    if(oldReply != null) {
      logger.finer("Overwrote existing cached reply with: " + reply.hashCodeStr());
      if(reply.getOriginAddress() != null) logger.fine("Cache reply " + reply.hashCodeStr() + " late by: " +
        (reply.getTimeStamp() - oldReply.getTimeStamp()) + " ms");      
      if(!oldReply.equals(reply)) {
        logger.warning("Overwrote cache reply with different DCW! - Previous: " + oldReply.toDebugString() +
            " Current: " + reply.toDebugString());
      }
    } else if(listener != null) listener.onReply(request, reply);
  }

  protected void cleanOld() {
    long now = System.currentTimeMillis();
    int count = 0;
    CamdNetMessage msg;

    do {
      if(ecmMap.isEmpty()) break;
      msg = (CamdNetMessage)ecmMap.keySet().iterator().next();
      if(now - msg.getTimeStamp() > maxAge) {
        count++;
        ecmMap.remove(msg);
        removeRequest(msg);
      }
    } while(now - msg.getTimeStamp() > maxAge);

    if(pendingEcms.size() > 10) {
      List tmp = new ArrayList(pendingEcms.keySet());
      for(Iterator iter = tmp.iterator(); iter.hasNext(); ) {
        msg = (CamdNetMessage)iter.next();
        if(now - msg.getTimeStamp() > maxAge) removeRequest(msg);
      }
    }

    logger.finest("Cleared " + count + " old entries, " + ecmMap.size() + " entries left, maxAge: " + maxAge);
  }

  protected void removeRequest(CamdNetMessage request) {
    pendingEcms.remove(request);
  }

  public void setListener(CacheListener listener) {
    this.listener = listener;
  }

}
