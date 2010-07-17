package com.bowman.cardserv.util;

import com.bowman.cardserv.ConfigException;

import java.util.logging.*;
import java.util.*;
import java.lang.reflect.Field;
import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 15, 2005
 * Time: 11:22:13 AM
 */
public class ProxyLogger {

  public static final String LOG_BASE = "com.bowman.cardserv";
  private static ConsoleHandler console;

  public static ProxyLogger getProxyLogger(String name) {
    return new ProxyLogger(Logger.getLogger(name));
  }

  private Logger logger;
  private FileHandler fileHandler;
  private String label;

  private ProxyLogger(Logger logger) {
    this.logger = logger;
  }

  public void throwing(String msg, Throwable throwable, Level level) {
    StackTraceElement caller = getCallerStackFrame();
    String sourceClass = caller==null?"<unknown>":caller.getClassName();
    String sourceMethod = caller==null?"<unknown>":caller.getMethodName();
    if(msg == null) msg = "THROW";      
    if(logger.isLoggable(level)) {
      LogRecord lr = new LogRecord(level, msg);
      lr.setSourceClassName(sourceClass);
      lr.setSourceMethodName(sourceMethod);
      lr.setThrown(throwable);
      lr.setParameters(new Object[] {label});
      logger.log(lr);
    }
  }

  public void throwing(String msg, Throwable throwable) {
    throwing(msg, throwable, CustomFormatter.isDebug()?Level.WARNING:Level.FINER);
  }

  public void throwing(Throwable throwable) {
    throwing(null, throwable);
  }

  public void severe(String msg, Throwable throwable) {
    throwing(msg, throwable, Level.SEVERE);
  }

  protected void logb(Level l, String msg) { // ensure caller class/method is meaningful, despite this wrapper class
    StackTraceElement caller = getCallerStackFrame();
    String sourceClass = caller==null?"<unknown>":caller.getClassName();
    String sourceMethod = caller==null?"<unknown>":caller.getMethodName();
    logger.logp(l, sourceClass, sourceMethod, msg, label);
  }

  public void severe(String msg) {
    logb(Level.SEVERE, msg);
  }

  public void warning(String msg) {
    logb(Level.WARNING, msg);
  }

  public void info(String msg) {
    logb(Level.INFO, msg);
  }

  public void fine(String msg) {
    logb(Level.FINE, msg);
  }

  public void finer(String msg) {
    logb(Level.FINER, msg);
  }

  public void finest(String msg) {
    logb(Level.FINEST, msg);
  }

  public void setLevel(String level) throws ConfigException {
    logger.setLevel(getLogLevel(level));
    console.setLevel(getLogLevel(level));
  }

  public void setSilent(boolean silent) {
    logger.removeHandler(console);
    if(!silent) logger.addHandler(console);
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public void close() {
    if(fileHandler != null) fileHandler.close();
  }

  public Logger getWrappedLogger() {
    return logger;
  }

  private StackTraceElement getCallerStackFrame() {
    StackTraceElement[] stackTrace = new Throwable().getStackTrace();
    int index = 0;
    while(index < stackTrace.length && !stackTrace[index].getClassName().equals(getClass().getName())) index++;
    while(index < stackTrace.length && stackTrace[index].getClassName().equals(getClass().getName())) index++;
    return index < stackTrace.length ? stackTrace[index] : null;
  }

  private static Map levelMap = new HashMap();
  static {
    Field[] fields = Level.class.getFields();
    for(int i = 0; i < fields.length; i++) {
      if(fields[i].getType() == Level.class)
        try {
          levelMap.put(fields[i].getName().toUpperCase(), fields[i].get(null));
        } catch(IllegalAccessException e) {
          e.printStackTrace();
        }
    }
  }

  public static ProxyLogger getLabeledLogger(String name) {
    return getLabeledLogger(name, null);
  }

  public static ProxyLogger getLabeledLogger(String name, String label) {
    ProxyLogger logger = getProxyLogger(name);
    logger.logger.setUseParentHandlers(true);
    // user classname minus package as label as default
    if(label == null && name.indexOf('.') > -1) label = name.substring(name.lastIndexOf('.') + 1);
    logger.setLabel(label);
    return logger;
  }

  public static Level getLogLevel(String level) throws ConfigException {
    if(!levelMap.containsKey(level.toUpperCase()))
      throw new ConfigException("log-level", "Illegal log-level '" + level + "'  " + levelMap.keySet() + " are supported");
    return (Level)levelMap.get(level.toUpperCase());
  }

  public static ProxyLogger getFileLogger(String name, File logFile, String level, int count, int limit, boolean skipLead)
      throws ConfigException, IOException
  {
    Level l = getLogLevel(level);
    ProxyLogger logger = getProxyLogger(name);
    if(count < 1 || limit < 1) logger.fileHandler = new FileHandler(logFile.getAbsolutePath(), true);
    else logger.fileHandler = new FileHandler(logFile.getAbsolutePath(), limit * 1024, count, true);
    CustomFormatter formatter = new CustomFormatter();
    formatter.setSkipLead(skipLead);
    logger.fileHandler.setFormatter(formatter);
    logger.logger.addHandler(logger.fileHandler);
    logger.logger.setUseParentHandlers(false);
    logger.logger.setLevel(l);
    return logger;
  }

  public static void initConsole(String level) throws ConfigException {
    console = new ConsoleHandler();
    console.setFormatter(new CustomFormatter());
    console.setLevel(getLogLevel(level));
  }

  public static void initFormatter(boolean debug, boolean hideIPs) {
    CustomFormatter.setDebug(debug);
    CustomFormatter.setHideIPs(hideIPs);
    CustomFormatter.setLogBase(LOG_BASE);
  }
}
