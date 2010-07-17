package com.bowman.cardserv.util;

import com.bowman.xml.*;
import com.bowman.cardserv.ConfigException;

import java.io.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 8, 2005
 * Time: 4:36:20 PM
 */
public class ProxyXmlConfig {

  private static ProxyXmlTracer tracer;
  private static Map timeSuffixes;

  static {
    if("true".equalsIgnoreCase(System.getProperty("com.bowman.cardserv.util.tracexmlcfg")))
      tracer = new ProxyXmlTracer();
    
    timeSuffixes = new HashMap();
    timeSuffixes.put("m", new Integer(60 * 1000));
    timeSuffixes.put("s", new Integer(1000));
    timeSuffixes.put("ms", new Integer(1));
  }

  private void trace(String name, Object def, String typeInfo) {
    if(tracer != null) {
      StackTraceElement caller = getCallerStackFrame();
      if(caller != null) {
        if(typeInfo == null) tracer.trace(parentPath + "/" + this.name + "/" + name, null, null, null, caller);
        else tracer.trace(parentPath + "/" + this.name, name, def, typeInfo, caller);
      }
    }
  }

  private StackTraceElement getCallerStackFrame() {
    StackTraceElement[] stackTrace = new Throwable().getStackTrace();
    int index = 0;
    while(index < stackTrace.length && !stackTrace[index].getClassName().equals(getClass().getName())) index++;
    while(index < stackTrace.length && stackTrace[index].getClassName().equals(getClass().getName())) index++;
    if(index > 3) return null;
    return index < stackTrace.length?stackTrace[index]:null;
  }

  private XMLConfig currentConfig, overrides;
  private String name, fullName, parentPath;
  private Properties stringOverrides;
  
  public ProxyXmlConfig(XMLConfig xml) {
    this.currentConfig = xml;
    this.name = currentConfig.getName();

    StringBuffer sb = new StringBuffer();
    String[] parents = currentConfig.getParentNames();
    for(int i = 0; i < parents.length; i++) sb.append('/').append(parents[i]);
    parentPath = sb.toString();

    this.fullName = name + " (" + parentPath + ")";
  }

  public ProxyXmlConfig(String name, String parentPath) {
    this.fullName = name + " (" + parentPath + ")";
  }

  public ProxyXmlConfig(XMLConfig xml, ProxyXmlConfig overrides) {
    this(xml);
    setOverrides(overrides);
  }

  public void setOverrides(ProxyXmlConfig overrides) {
    if(overrides == null) this.overrides = null;
    else this.overrides = overrides.currentConfig;
  }

  public void setStringOverride(String key, String value) {
    if(stringOverrides == null) stringOverrides = new Properties();
    stringOverrides.put(key, value);
  }

  public String getFullName() {
    return fullName;
  }

  public String getName() {
    return name;
  }

  public String getParentPath() {
    return parentPath;
  }

  private XMLConfig getSubConfigInternal(String s) {
    if(overrides != null) {
      XMLConfig x = overrides.getSubConfig(s);
      if(x != null) return x;
    }
    if(currentConfig == null) return null;
    else return currentConfig.getSubConfig(s);
  }

  public ProxyXmlConfig getSubConfig(String s) throws ConfigException {    

    trace(s, null, null);

    XMLConfig subConf = getSubConfigInternal(s);
    if(subConf == null) throw new ConfigException(fullName, "Required element missing: " + s);
    return new ProxyXmlConfig(subConf);
  }

  public Iterator getMultipleSubConfigs(String s) {

    if(s != null) trace(s, null, null);

    if(currentConfig == null) return null;
    Enumeration en;
    if(s != null ) en = currentConfig.getMultipleSubConfigs(s);
    else en = currentConfig.getAllSubConfigs();
    if(en == null) return null;
    List temp = new ArrayList();
    while(en.hasMoreElements()) temp.add(new ProxyXmlConfig((XMLConfig)en.nextElement()));
    return temp.iterator();
  }

  public Iterator getMultipleStrings(String s) {

    if(s != null) trace(s, null, null);

    if(currentConfig == null) return null;
    Enumeration en;
    en = currentConfig.getMultipleStrings(s);
    if(en == null) return null;
    List temp = new ArrayList();
    while(en.hasMoreElements()) temp.add(en.nextElement());
    return temp.iterator();
  }  

  private String getString(String s) {
    if(stringOverrides != null && stringOverrides.containsKey(s)) return stringOverrides.getProperty(s);
    if(overrides != null) {
      String o = overrides.getString(s);
      if(o != null) return o;
    }
    if(currentConfig == null) return null;
    else return currentConfig.getString(s);
  }

  public String getStringValue(String s, String def) throws ConfigException {

    trace(s, def, "String");

    String value = getString(s);
    if(value == null) value = def;
    if(value == null) throw new ConfigException(fullName, "Required value missing: " + s);
    return value;
  }

  public String getStringValue(String s) throws ConfigException {

    trace(s, null, "String");

    return getStringValue(s, null);
  }

  public String getContents() {   
    if(currentConfig == null) return null;
    else return currentConfig.getContents();
  }

  public int getPortValue(String s) throws ConfigException {

    trace(s, null, "Portnumber");

    int port = getIntValue(s);
    if(port < 1 || port >= 0xFFFF) throw new ConfigException(fullName, "Parameter '" + s + "' has an invalid port value: " + port);
    return port;
  }

  public int getPortValue(String s, int def) throws ConfigException {

    trace(s, new Integer(def), "Portnumber");

    int port = getIntValue(s, def);
    if(port == def && def == -1) return port;
    if(port < 1 || port >= 0xFFFF) throw new ConfigException(fullName, "Parameter '" + s + "' has an invalid port value: " + port);
    return port;
  }

  public int getIntValue(String s, int def) throws ConfigException {

    trace(s, new Integer(def), "Integer");

    try {
      String intStr = getString(s);
      int i = intStr == null ? def : Integer.parseInt(intStr);
      if(def != i && i < 0) throw new NumberFormatException(i + " must be positive");
      else return i;
    } catch (NumberFormatException e) {
      throw new ConfigException(fullName, "Parameter '" + s + "' has an invalid integer value: " + e.getMessage(), e);
    }
  }

  public int getIntValue(String s) throws ConfigException {

    trace(s, null, "Integer");

    try {
      int i = Integer.parseInt(getStringValue(s, null));
      if(i < 0) throw new NumberFormatException(i + " must be positive");
      else return i;
    } catch (NumberFormatException e) {
      throw new ConfigException(fullName, "Parameter '" + s + "' has an invalid integer value: " + e.getMessage(), e);
    }
  }

  public int getTimeValue(String s, int def, String defSuffix) throws ConfigException {

    trace(s, new Integer(def), "Time (defSuffix=" + defSuffix + ")");

    String timeStr = getStringValue(s, def==-1?null:String.valueOf(def));
    int mp;
    if(timeStr.endsWith("ms")) {
      mp = getTimeMp("ms");
      timeStr = timeStr.substring(0, timeStr.length() - 2);
    } else if(timeStr.endsWith("s")) {
      mp = getTimeMp("s");
      timeStr = timeStr.substring(0, timeStr.length() - 1);
    } else if(timeStr.endsWith("m")) {
      mp = getTimeMp("m");
      timeStr = timeStr.substring(0, timeStr.length() - 1);
    } else mp = getTimeMp(defSuffix);
    if(mp == -1) throw new ConfigException(fullName, "Invalid default suffix when retrieving '" + s + "': " + defSuffix);
    try {
      int t = Integer.parseInt(timeStr.trim()) * mp;
      if(t < 0) throw new NumberFormatException(t + " must be positive");
      else return t;
    } catch (NumberFormatException e) {
      throw new ConfigException(fullName, "Parameter '" + s + "' has an invald time value: " + e.getMessage() +
          "(supported suffixes are m, s and ms)", e);
    }
  }

  public int getTimeValue(String s, String defSuffix) throws ConfigException {        
    return getTimeValue(s, -1, defSuffix);
  }

  private static int getTimeMp(String suffix) {
    Integer mp = (Integer)timeSuffixes.get(suffix);
    if(mp == null) return -1;
    else return mp.intValue();
  }

  public String getFileValue(String s, boolean create) throws ConfigException {

    trace(s, null, "File (create=" + create + ")");

    return getFileValue(s, null, create, false);
  }

  public String getFileValue(String s, String def, boolean create) throws ConfigException {

    trace(s, def, "File (create=" + create + ")");

    return getFileValue(s, def, create, false);
  }

  public String getFileValue(String s, boolean create, boolean dir) throws ConfigException {

    trace(s, null, "File (create=" + create + " dir=" + dir + ")");

    return getFileValue(s, null, create, dir);
  }

  public String getFileValue(String s, String def, boolean create, boolean dir) throws ConfigException {

    trace(s, def, "File (create=" + create + " dir=" + dir + ")");

    String fileName = getStringValue(s, def);
    File file = new File(fileName);
    File parentDir = file.getParentFile();
    if(!create) {
      if(file.exists() && file.canRead()) {
        if(dir && !file.isDirectory())
          throw new ConfigException(fullName, "Not a directory: " + file.getAbsolutePath());
        // ok
      } else throw new ConfigException(fullName, "Unable to find/read from: " + file.getAbsolutePath());
    } else {
      if(parentDir != null) {
        if(!parentDir.exists()) parentDir.mkdirs();
        if(!parentDir.exists()) throw new ConfigException(fullName, "Unable to create directory: " + parentDir.getAbsolutePath());
      }
      if(file.exists()) {
        if(file.canRead() && file.canWrite()) {
          if(dir && !file.isDirectory())
            throw new ConfigException(fullName, "Not a directory: " + file.getAbsolutePath());
          // ok
        } else throw new ConfigException(fullName, "Unable to read/write: " + file.getAbsolutePath());
      } else {
        try {
          if(!dir) {
            if(!file.createNewFile()) throw new IOException();
          } else {
            if(!file.mkdirs()) throw new IOException();
          }
        } catch(IOException e) {
           throw new ConfigException(fullName, "Unable to create " + (dir?"dir":"file") + ": " + file.getAbsolutePath(), e);
        }
      }
    }
    return fileName;
  }

  public byte[] getBytesValue(String s) throws ConfigException {

    trace(s, null, "Bytes");

    String byteStr = getStringValue(s, null);
    String[] parts = byteStr.split(" ");
    byte[] bytes = new byte[parts.length];
    for(int i = 0; i < parts.length; i++) {
      if(parts[i].length() != 2)
        throw new ConfigException(fullName, "Bad byte value '" + parts[i] + "' in '" + s + "' (must be 2 digit hex value)");
      try {
        int tmp = Integer.parseInt(parts[i], 16);
        if(tmp < 0 || tmp > 255) throw new NumberFormatException();
        bytes[i] = (byte)(tmp & 0xFF);
      } catch (NumberFormatException e) {
        throw new ConfigException(fullName, "Bad byte value '" + parts[i] + "' in '" + s + "' (must be 00-FF)");
      }
    }
    return bytes;
  }

  public static Set getIntTokens(String param, String list) throws ConfigException {
    Set result = new HashSet();
    String[] tokens = list.split(" ");
    for(int i = 0; i < tokens.length; i++) {
      try {
        result.add(new Integer(Integer.parseInt(tokens[i], 16)));
      } catch (NumberFormatException e) {
        throw new ConfigException(param, "Bad hex integer value: " + e.getMessage());
      }
    }
    return result;
  }

  public Properties toProperties() {
    if(currentConfig == null) return new Properties();
    else return currentConfig.flatten();
  }

  public String toString() {
    return toProperties().toString();
  }

}
