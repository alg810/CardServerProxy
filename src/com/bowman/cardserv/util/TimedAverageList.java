package com.bowman.cardserv.util;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2007-feb-04
 * Time: 01:44:33
 */
public class TimedAverageList {

  private int maxAge, maxValue, minValue;
  private LinkedList list = new LinkedList();

  public TimedAverageList(int maxAge) {
    this.maxAge = maxAge * 1000;
  }

  public void addRecord(int i) {
    addRecord(System.currentTimeMillis(), i);
  }

  public synchronized void addRecord(long timeStamp, int i) {
    list.addLast(new TimedEntry(timeStamp, i));
    if(i < minValue || minValue == 0) minValue = i;
    if(i > maxValue || maxValue == 0) maxValue = i;
    removeOldest();
  }

  boolean removeOldest() {
    TimedEntry t = (TimedEntry)list.getFirst();
    if(System.currentTimeMillis() - t.timeStamp > maxAge) {
      list.removeFirst();
      return true;
    }
    return false;
  }

  public synchronized int getAverage(boolean current) {
    if(list.isEmpty()) return -1;
    int total = 0, count = 0;
    long now = System.currentTimeMillis();
    TimedEntry t;
    for(Iterator iter = list.iterator(); iter.hasNext();) {
      t = (TimedEntry)iter.next();
      if(t != null) {
        if(current) {
          if(now - t.timeStamp <= maxAge) {
            total += t.value; // only count recent enough values
            count++;
          }
        } else {
          total += t.value; // count all
          count++;
        }
      }
    }
    if(count == 0) return -1; // no current data
    else return total / count;
  }

  public synchronized int getTotal(boolean current) {
    if(list.isEmpty()) return 0;
    int total = 0;
    long now = System.currentTimeMillis();
    TimedEntry t;
    for(Iterator iter = list.iterator(); iter.hasNext();) {
      t = (TimedEntry)iter.next();
      if(t != null) {
        if(current) {
          if(now - t.timeStamp <= maxAge) total += t.value; // only count recent enough values
        } else total += t.value; // count all
      }
    }
    return total;  
  }

  public synchronized int getLowest() {
    if(list.isEmpty()) return -1;    
    TimedEntry t; int lowest = -1;
    for(Iterator iter = list.iterator(); iter.hasNext();) {
      t = (TimedEntry)iter.next();
      if(t != null) {
        if(lowest == -1 || t.value < lowest) lowest = t.value;
      }
    }
    int avg = getAverage(false);
    if(lowest < avg / 2) lowest = avg / 2;
    return lowest;
  }

  public int getMaxValue() {
    return maxValue;
  }

  public int getMinValue() {
    return minValue;
  }

  public synchronized void clear() {
    list.clear();
  }

  public synchronized int size(boolean current) {
    if(!current || list.isEmpty()) return list.size();
    else {
      long now = System.currentTimeMillis();
      int index = 0;
      TimedEntry t;
      for(Iterator iter = list.iterator(); iter.hasNext();) {
        t = (TimedEntry)iter.next();
        if(now - t.timeStamp <= maxAge) return list.size() - index;
        index++;
      }
      return 0;
    }
  }

  public synchronized int size(long maxAge) {
    if(list.isEmpty()) return 0;
    else {
      long now = System.currentTimeMillis();
      int count = 0;
      TimedEntry t;
      for(Iterator iter = list.iterator(); iter.hasNext();) {
        t = (TimedEntry)iter.next();
        if(now - t.timeStamp <= maxAge) count++;
      }
      return count;
    }
  }

  public int getMaxAge() {
    return maxAge;
  }

  public synchronized Set getCurrentSet(long maxAge) {
    Set current = new HashSet();
    if(list.isEmpty()) return current;
    else {
      long now = System.currentTimeMillis();
      TimedEntry t;
      for(Iterator iter = list.iterator(); iter.hasNext();) {
        t = (TimedEntry)iter.next();
        if(now - t.timeStamp <= maxAge) current.add(new Integer(t.value));
      }
      return current;
    }
  }

  private static class TimedEntry {
    long timeStamp;
    int value;

    private TimedEntry(long timeStamp, int value) {
      this.timeStamp = timeStamp;
      this.value = value;
    }
  }

}
