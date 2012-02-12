package com.bowman.cardserv;

import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.util.TimedAverageList;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2011-07-13
 * Time: 06:38
 */
public class SourceCacheEntry implements Comparable {

  static final Map owCw = Collections.synchronizedMap(new LinkedHashMap());
  static final Map recurringOwCw = Collections.synchronizedMap(new LinkedHashMap());

  int updateCount, abortCount, duplicateCount, overwriteCount;
  String sourceStr, ipStr, label;

  private TimedAverageList discards = new TimedAverageList(ServiceCacheEntry.WINDOW_SIZE);
  private MessageCacheMap overwrites = new MessageCacheMap(ServiceCacheEntry.WINDOW_SIZE * 1000);

  public SourceCacheEntry(String sourceStr) {
    this.sourceStr = sourceStr;
    this.ipStr = sourceStr.substring(2);
    this.label = "?";
  }

  public static String getSourceStr(CamdNetMessage request) {
    if(request.getRemoteAddress() == null && request.getOriginAddress() == null) return null;
    if(request.getRemoteAddress() == null) return "R:" + request.getOriginAddress(); // from remote cache
    else return "L:" + request.getRemoteAddress(); // from local traffic
  }

  public String toString() {
    return sourceStr;
  }

  public boolean isLocal() {
    return sourceStr.startsWith("L");
  }

  public boolean reportOverWrite(ServiceCacheEntry entry, CamdNetMessage newRequest, CamdNetMessage oldReply, CamdNetMessage newReply) {
    if(oldReply.hasZeroDcw() || newReply.hasZeroDcw()) {
      if(oldReply.equalsSingleDcw(newReply)) return false; // don't consider these overwrites
    }
    overwriteCount++;
    overwrites.put(newRequest, new ReplyTuple(newReply, oldReply));

    if(countOwCw(newReply) ||  countOwCw(oldReply)) {
      System.out.println("ecm: " + newRequest.hashCodeStr() + " " + DESUtil.bytesToString(newRequest.getCustomData()));
      System.out.println(entry + " " + overwrites.get(newRequest) + "\n");
    }

    return true;
  }

  static boolean countOwCw(CamdNetMessage rep) {
    Integer i = (Integer)owCw.get(rep);
    if(i == null) owCw.put(rep, new Integer(1));
    else {
      i = new Integer(i.intValue() + 1);
      owCw.put(rep, i);
      if(i.intValue() > 2) {
        recurringOwCw.put(rep, i);

        System.out.println("Recurring overwrite cw: [" + CaProfile.getKeyStr(rep.getNetworkId(), rep.getCaId()) + "] "  + DESUtil.bytesToString(rep.getCustomData()) + " (" + i + ")");
        return true;
      }
    }
    return false;
  }

  public void reportDuplicate(ServiceCacheEntry entry, CamdNetMessage newRequest, CamdNetMessage oldReply) {
    if(sourceStr.equals(getSourceStr(oldReply))) {
      duplicateCount++;
      // System.out.println("Dupe: " + entry + " " + (newRequest.getTimeStamp() - oldReply.getTimeStamp()));
    } else {
      discards.addRecord((int)(newRequest.getTimeStamp() - oldReply.getTimeStamp())); // measure lateness
      // System.out.println("Discard: " + entry + " " + (newRequest.getTimeStamp() - oldReply.getTimeStamp()));
    }
  }

  public int compareTo(Object o) {
    return sourceStr.compareTo(((SourceCacheEntry)o).sourceStr);
  }

  public boolean equals(Object o) {
    if(this == o) return true;
    if(o == null || getClass() != o.getClass()) return false;
    SourceCacheEntry that = (SourceCacheEntry)o;
    if(!sourceStr.equals(that.sourceStr)) return false;
    return true;
  }

  public int hashCode() {
    return sourceStr.hashCode();
  }

  static class ReplyTuple {
    CamdNetMessage newReply, oldReply;

    ReplyTuple(CamdNetMessage newReply, CamdNetMessage oldReply) {
      this.newReply = newReply;
      this.oldReply = oldReply;
    }

    public String toString() {
      return "\nold: " + oldReply.toDebugString() + "\nnew: " + newReply.toDebugString();
    }
  }
}
