package com.bowman.cardserv;

import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.util.TimedAverageList;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2011-07-13
 * Time: 06:38
 */
public class SourceCacheEntry implements Comparable {

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
    if(request.getRemoteAddress() == null) return "R:" + request.getOriginAddress(); // from remote cache
    else return "L:" + request.getRemoteAddress(); // from local traffic
  }

  public String toString() {
    return sourceStr;
  }

  public boolean isLocal() {
    return sourceStr.startsWith("L");
  }

  public void reportOverWrite(ServiceCacheEntry entry, CamdNetMessage newRequest, CamdNetMessage oldReply, CamdNetMessage newReply) {
    overwriteCount++;
    overwrites.put(newRequest, new ReplyTuple(newReply, oldReply));
    System.out.println(entry + " " + overwrites);
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


  static class ReplyTuple {
    CamdNetMessage newReply, oldReply;

    ReplyTuple(CamdNetMessage newReply, CamdNetMessage oldReply) {
      this.newReply = newReply;
      this.oldReply = oldReply;
    }

    public String toString() {
      return "\n" + DESUtil.bytesToString(oldReply.getCustomData()) + "\n" + DESUtil.bytesToString(newReply.getCustomData());
    }
  }
}
