package com.bowman.cardserv.util;

import java.util.logging.*;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 15, 2005
 * Time: 10:33:37 AM
 */
public class CustomFormatter extends Formatter {

  private static SimpleDateFormat fmt = new SimpleDateFormat("yyMMdd HH:mm:ss.SSS");
  private static final int MB = 1024 * 1024;

  private static boolean debug = false, hideIPs = false;
  private static String logBase = "";

  private static boolean checkFileDescriptors = true;
  private static String jvmStats;
  private static long jvmStatsAge = System.currentTimeMillis();

  private boolean skipLead = false;

  public static void setLogBase(String lb) {
    logBase = lb;
  }

  public static void setDebug(boolean b) {
    debug = b;
  }

  public static void setHideIPs(boolean b) {
    hideIPs = b;
  }

  public static void setDateFormat(SimpleDateFormat sdf) {
    fmt = sdf;
  }

  public static boolean isDebug() {
    return debug;
  }

  private static String getJvmStats() {
    long now = System.currentTimeMillis();
    if(now - jvmStatsAge > 1000 || jvmStats == null) {
      Runtime rt = Runtime.getRuntime();
      StringBuffer sb = new StringBuffer();
      long tm = rt.totalMemory();
      long used = tm - rt.freeMemory();
      sb.append("H:").append(used / MB).append('/').append(tm / MB).append("M");
      sb.append(" TC:").append(Thread.activeCount());
      if(checkFileDescriptors) {
        try { // java6+ unix specific jmx info
          long openFd = UnixUtil.getOpenFileDescriptorCount();
          long maxFd = UnixUtil.getMaxFileDescriptorCount();
          if(openFd > 0 && maxFd > 0) sb.append(" FD:" ).append(openFd).append('/').append(maxFd);
        } catch(Throwable e) {
          checkFileDescriptors = false;
        }
      }
      // sb.append(" S:").append(SessionManager.getInstance().getSessionCount());
      jvmStats = sb.toString();
      jvmStatsAge = now;
    }
    return jvmStats;
  }

  public boolean isSkipLead() {
    return skipLead;
  }

  public void setSkipLead(boolean skipLead) {
    this.skipLead = skipLead;
  }

  public static String formatLabel(String sourceClass) {
    if(sourceClass.startsWith(logBase)) return sourceClass.substring(logBase.length() + 1);
    else return sourceClass;
  }

  public String format(LogRecord lr) {

    Object label = null;
    String sourceClass = lr.getSourceClassName();
    if(sourceClass.startsWith(logBase)) {
      sourceClass = sourceClass.substring(logBase.length() + 1);
      if(lr.getParameters() != null && lr.getParameters().length > 0) label = lr.getParameters()[0];
    } else label = sourceClass;

    StringBuffer sb = new StringBuffer();

    if(!skipLead) {
      sb.append(fmt.format(new Date(lr.getMillis())));
      sb.append(' ').append(lr.getLevel().getName());
      if(lr.getThrown() != null) sb.append(" [").append(lr.getThrown().toString()).append(']');
      sb.append(" -> ");
      if(label != null) { 
        sb.append(label);
        sb.append(" <- ");
      }
    }
    sb.append(lr.getMessage());

    if(debug) {
      sb.append("\n\t DEBUG -> [").append(getJvmStats()).append("] ");
      sb.append('"').append(Thread.currentThread().getName()).append('"');      
      sb.append(" in ").append(sourceClass).append(' ').append(lr.getSourceMethodName()).append("()");
    }
    if(lr.getThrown() != null) sb.append('\n').append(getStackTrace(lr.getThrown()));
    else if(debug) sb.append('\n');
    sb.append('\n');    
    return sb.toString();
  }

  private static String getStackTrace(Throwable t) {
    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    sw.flush();
    return sw.getBuffer().toString();
  }

  public static String formatAddress(String addr) {
    if(!hideIPs || addr == null || addr.startsWith("0.") || addr.indexOf('.') == -1) return addr;
    else {
      boolean isIp = true;
      try {
        if(Integer.parseInt(addr.substring(0, addr.indexOf('.'))) > 255) isIp = false;
      } catch (NumberFormatException nfe) {
        isIp = false;
      }
      if(isIp) return "xxx.xxx.xxx." + addr.substring(addr.lastIndexOf('.') + 1);
      else return addr.substring(0, addr.indexOf('.')) + ".xxx.xxx";
    }
  }

}
