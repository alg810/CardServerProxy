package com.bowman.cardserv.util;

import com.sun.management.*;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Mar 10, 2009
 * Time: 5:39:39 PM
 */
public class UnixUtil {

  public static long getOpenFileDescriptorCount() {
    Object o = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
    if(o instanceof UnixOperatingSystemMXBean) return ((UnixOperatingSystemMXBean)o).getOpenFileDescriptorCount();
    else return -1;
  }

  public static long getMaxFileDescriptorCount() {
    Object o = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
    if(o instanceof UnixOperatingSystemMXBean) return ((UnixOperatingSystemMXBean)o).getMaxFileDescriptorCount();
    else return -1;
  }

  /*
  public static void dumpHeap(String fileName, boolean live) throws IOException {
    HotSpotDiagnosticMXBean mb = sun.management.ManagementFactory.getDiagnosticMXBean();
    if(mb != null) mb.dumpHeap(fileName, live);
  }
  */

}
