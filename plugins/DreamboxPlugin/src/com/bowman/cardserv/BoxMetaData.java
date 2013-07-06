package com.bowman.cardserv;

import java.io.Serializable;
import java.security.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Apr 10, 2009
 * Time: 5:44:58 AM
 */
public class BoxMetaData implements Serializable {
    
  private String macAddr, user;
  private String boxId, tag;
  private int interval;
  private long createTimeStamp, lastCheckinTimeStamp;

  private transient int tunnelPort;
  private transient BoxOperation pendingOperation;
  private transient Map executedOperations;
  private transient FileUploadPermission uploadPermissions;

  private Properties boxProperties = new Properties();

  public BoxMetaData(String macAddr, String user, String seed, String salt) {
    this.macAddr = macAddr;
    this.user = user;

    this.boxId = generateBoxId(macAddr, user, seed, salt);
    this.createTimeStamp = System.currentTimeMillis();
  }

  public String getMacAddr() {
    return macAddr;
  }

  public String getUser() {
    return user;
  }

  public String getBoxId() {
    return boxId;
  }

  public int getTunnelPort() {
    return tunnelPort;
  }

  public void setTunnelPort(int tunnelPort) {
    this.tunnelPort = tunnelPort;
  }

  public String getTag() {
    return tag;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  public BoxOperation getPendingOperation() {
    return pendingOperation;
  }

  public void setPendingOperation(BoxOperation pendingOperation) {
    this.pendingOperation = pendingOperation;
  }

  public void runPendingOperation() {
    if(pendingOperation != null) {
      if(executedOperations == null) executedOperations = new LinkedHashMap();
      executedOperations.put(new Integer(pendingOperation.getId()), pendingOperation);
      pendingOperation = null;
    }
  }

  public long getCreateTimeStamp() {
    return createTimeStamp;
  }

  public long getLastCheckinTimeStamp() {
    return lastCheckinTimeStamp;
  }

  public int getInterval() {
    return interval;
  }

  public void checkin(int interval) {
    this.interval = interval;
    this.lastCheckinTimeStamp = System.currentTimeMillis();
  }

  public void setProperty(String name, String value) {     
    boxProperties.setProperty(name, value);
    if("uptime".equals(name) && value != null)
      if(value.indexOf(", load average: ") != -1) {
        String load = value.split(", load average: ")[1];
        boxProperties.setProperty("load", load);
        String[] n = load.split(", ");
        if(n.length == 3)
          if(!n[0].startsWith("0") && !n[1].startsWith("0") && !n[2].startsWith("0")) {
            boxProperties.setProperty("cpu-warn", "true");
          } else boxProperties.setProperty("cpu-warn", "false");
      }
  }

  public String getProperty(String name) {
    return boxProperties.getProperty(name);
  }

  public Properties getProperties() {
    return boxProperties;
  }

  public BoxOperation getOperation(int opId) {
    if(executedOperations == null) return null;
    else return (BoxOperation)executedOperations.get(new Integer(opId));
  }

  public int getOperationCount() {
    if(executedOperations == null) return 0;
    else return executedOperations.size();
  }

  public Collection getOperations() {
    if(executedOperations == null) return null;
    else return executedOperations.values();
  }

  public int clearOperations() {
    int count = 0;
    if(executedOperations != null) {
      count = executedOperations.size();
      executedOperations.clear();
    }
    return count;
  }

  public boolean isActive() {
    return (System.currentTimeMillis() - lastCheckinTimeStamp) < (interval * 2000);
  }

  public boolean isUploadAllowed(String fileName) {
    if(uploadPermissions == null) return false;
    else return uploadPermissions.contains(fileName);
  }

  public void setFileUploadEntry(FileUploadPermission fue) {
    this.uploadPermissions = fue;
  }

  public String getUploadPath(String fileName) {
    if(uploadPermissions == null) return null;
    else return uploadPermissions.getPath(fileName);
  }

  protected static String generateBoxId(String macAddr, String user, String seed, String salt) {
    seed = Integer.toString(Integer.parseInt(seed), 16);
    while(seed.length() < 8) seed = '0' + seed;
    return seed + hexMD5Hash(macAddr + user + (salt==null?"":salt));
  }

  private static String hexMD5Hash(String text) {
    try {
      MessageDigest md5Digest = MessageDigest.getInstance("MD5");
      md5Digest.reset();
      md5Digest.update(text.getBytes());
      byte[] md5 = md5Digest.digest();
      StringBuffer sb = new StringBuffer();
      String byteStr;
      for(int i = 0; i < md5.length; i++) {
        byteStr = Integer.toString((0xff & md5[i]), 16);
        if(byteStr.length() == 1) sb.append("0");
        sb.append(byteStr);
      }
      return sb.toString();
    } catch(NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
    return null;
  }


}
