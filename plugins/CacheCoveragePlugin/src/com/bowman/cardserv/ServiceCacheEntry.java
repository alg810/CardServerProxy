package com.bowman.cardserv;

import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.util.TimedAverageList;

import java.util.*;

/**
* Created by IntelliJ IDEA.
* User: bowman
* Date: 2011-07-02
* Time: 20:20
*/
public class ServiceCacheEntry implements Comparable {

  static final int TOLERANCE = 2000;
  static final int WINDOW_SIZE = 60; // one min sliding window for averages and backlog
  static final byte[] ZERO_CW = DESUtil.stringToBytes("00 00 00 00 00 00 00 00");

  CamdNetMessage request, reply;
  TvService ts;

  private int updateCount, abortCount, duplicateCount, overwriteCount;
  private int continuityCount, continuityErrors, continuityErrorsTotal;
  private int multiple;
  private long lastInterval, expectedInterval, timeOffset = -1;

  private TimedAverageList intervals = new TimedAverageList(WINDOW_SIZE), variances = new TimedAverageList(WINDOW_SIZE);
  private MessageCacheMap backLog = new MessageCacheMap(WINDOW_SIZE * 1000);
  private Map sources = new TreeMap();
  private CacheCoverageMap parent;

  ServiceCacheEntry(TvService ts, CamdNetMessage request, long expectedInterval, CacheCoverageMap parent) {
    this.ts = ts;
    this.expectedInterval = expectedInterval;
    this.parent = parent;
    this.request = request;
  }

  public long getAge() {
    return System.currentTimeMillis() - request.getTimeStamp();
  }

  public int getContinuityErrors() {
    return continuityErrors;
  }

  public int getContinuityErrorsTotal() {
    return continuityErrorsTotal + continuityErrors;
  }

  public int getDuplicateCount() {
    return duplicateCount;
  }

  public int getOverwriteCount() {
    return overwriteCount;
  }

  public int getUpdateCount() {
    return updateCount;
  }

  public int getAbortCount() {
    return abortCount;
  }

  public int getMultiple() {
    return multiple;
  }

  public long getLastInterval() {
    return lastInterval;
  }

  public Set getSources(boolean current) {
    if(!current) return sources.keySet();
    else {
      long window = WINDOW_SIZE * 1000, now = System.currentTimeMillis();
      Set currentSet = new TreeSet();
      SourceCacheEntry source; Long timeStamp;
      for(Iterator iter = sources.keySet().iterator(); iter.hasNext(); ) {
        source = (SourceCacheEntry)iter.next();
        timeStamp = (Long)sources.get(source);
        if(now - timeStamp.longValue() < window) currentSet.add(source);
      }
      return currentSet;
    }
  }

  public boolean isExpired() {
    int avg = getAvgInterval();
    return isExpired(TOLERANCE + (avg==-1?expectedInterval:avg));
  }

  public boolean isExpired(long maxAge) {
    return getAge() > maxAge;
  }

  public long getTimeOffset() {
    return timeOffset;
  }

  public boolean update(CamdNetMessage newRequest, CamdNetMessage newReply, SourceCacheEntry source) {
    long now = System.currentTimeMillis();
    sources.put(source, new Long(now));

    if(newReply == null || newReply.isEmpty()) {
      abortCount++;
      source.abortCount++;
      return false;
    }
    updateCount++;
    source.updateCount++;
    if(backLog.containsKey(newRequest) && !newReply.equals(backLog.get(newRequest))) {
      overwriteCount++; // overwritten with different dcw
      source.reportOverWrite(this, newRequest, (CamdNetMessage)backLog.get(newRequest), newReply);
      return false;
    }
    if(backLog.containsKey(newRequest)) {
      duplicateCount++;
      source.reportDuplicate(this, newRequest, (CamdNetMessage)backLog.get(newRequest));
      return false;
    }

    if(reply != null) {
      if(isOverlapping(reply.getCustomData(), newReply.getCustomData())) {
        setLastInterval(newRequest.getTimeStamp() - request.getTimeStamp());
        multiple = 0;
        continuityCount++;
      } else {
        int index = findOverlapInBacklog(newReply);
        if(index == -1) {
          // not continous, cant determine interval (i.e both cws changed - there are gaps between updates)
          if(getAge() > WINDOW_SIZE * 1000) resetContinuityErrors();
          else continuityErrors++;
          continuityCount = 0;

          /*
          long missed = ((getAge() + TOLERANCE) / expectedInterval) - 1;
          StringBuffer sb = new StringBuffer(this + " missed:" + missed + " (" + newReply.getConnectorName() + " " + source + ") - ");
          for(Iterator iter = backLog.values().iterator(); iter.hasNext(); )
            sb.append("[").append(DESUtil.bytesToString(((CamdNetMessage)iter.next()).getCustomData())).append("] ");

          System.out.println(backLog.size() + " " + sb);
          */

        } else {
          // continuity found in backlog, this is more than one service (or same service on different transponders/terrestrial transmitters, different ecm injectors etc)
          if(index > multiple && continuityErrors > 0) continuityErrors--; // last error wasn't an error
          multiple = index; // index from end of backlog indicates how many discrete cw continuities there are
          continuityCount++;

          // System.out.println(this + " " + multiple);
        }
      }
    }

    if(continuityCount * expectedInterval > 3600000) { // reset error counter if continous for 1h
      if(continuityErrors > 0) {
        resetContinuityErrors();
        // System.out.println(this + " Continous for " + continuityCount * expectedInterval);
      }
      continuityCount = 0;
    }

    request = newRequest;
    reply = newReply;
    backLog.put(newRequest, newReply);

    // estimate offset from window boundary
    if(multiple == 0) {
      if(parent.windowStart == 0 || now - parent.windowStart > expectedInterval) { // this is the first request in a new period
        timeOffset = 0;
        parent.windowStart = now;
      } else timeOffset = now - parent.windowStart;
    } else timeOffset = -1;

    return true;
  }

  private void resetContinuityErrors() {
    continuityErrorsTotal += continuityErrors;
    continuityErrors = 0;
  }

  private int findOverlapInBacklog(CamdNetMessage newReply) {
    long now = System.currentTimeMillis();
    CamdNetMessage[] oldReplies = (CamdNetMessage[])backLog.values().toArray(new CamdNetMessage[backLog.size()]);
    for(int i = 0; i < oldReplies.length; i++) {
      if(now - oldReplies[i].getTimeStamp() > (expectedInterval * 1.4)) continue; // too old, dont bother checking
      if(isOverlapping(oldReplies[i].getCustomData(), newReply.getCustomData())) {
        long interval = newReply.getTimeStamp() - oldReplies[i].getTimeStamp();
        if(Math.abs(expectedInterval - interval) < TOLERANCE) {
          // System.out.println(this + " - Found overlap at interval: " + interval + " (" + (oldReplies.length - i) + ")");
          setLastInterval(interval);
        } // else System.out.println(this + " - Overlap found but deviates too much: " + interval);
        return oldReplies.length - i;
      }
    }
    return -1;
  }

  public void setLastInterval(long interval) {
    lastInterval = interval;
    intervals.addRecord((int)lastInterval);
    variances.addRecord((int)Math.abs(expectedInterval - lastInterval));
  }

  public int getAvgInterval() {
    return intervals.getAverage(true);
  }

  public int getAvgVariance() {
    return variances.getAverage(true);
  }

  public Map getBackLog() {
    return backLog;
  }

  public static boolean isOverlapping(byte[] reply, byte[] other) {
    byte[] cw1 = new byte[8], cw2 = new byte[8];
    byte[] cw3 = new byte[8], cw4 = new byte[8];
    System.arraycopy(reply, 0, cw1, 0, 8);
    System.arraycopy(reply, 8, cw2, 0, 8);
    System.arraycopy(other, 0, cw3, 0, 8);
    System.arraycopy(other, 8, cw4, 0, 8);
    return Arrays.equals(cw1, cw3) || Arrays.equals(cw2, cw4);
  }


  public String toString() {
    return ts.getName() + ":" + getAvgInterval() + ":" + getAge() + ":" + continuityErrors;
  }

  public int compareTo(Object o) {
    return ts.toString().compareTo(((ServiceCacheEntry)o).ts.toString());
  }
}
