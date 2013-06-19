package com.bowman.cardserv;

import com.bowman.cardserv.util.ProxyXmlConfig;

import java.text.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2011-06-30
 * Time: 23:26
 */
public class AdvXmlUserManager extends XmlUserManager {

  static final SimpleDateFormat fmt = new SimpleDateFormat("dd-MM-yyyy");

  protected UserEntry parseUser(ProxyXmlConfig xml) throws ConfigException {
    AdvUserEntry aue = new AdvUserEntry(super.parseUser(xml)); // call XmlUserManager parsing and wrap user entry

    String edStr = null;
    try {
      edStr = xml.getStringValue("expire-date");
      aue.expireDate = fmt.parse(edStr).getTime();
    } catch(ConfigException e) {
      aue.expireDate = -1;
    } catch(ParseException e) {
      throw new ConfigException("Bad expire-date for user '" + aue.name + "': " + edStr + " (expected dd-mm-yyyy)");
    }
    String sdStr = null;
    try {
      sdStr = xml.getStringValue("start-date");
      aue.startDate = fmt.parse(sdStr).getTime();
    } catch(ConfigException e) {
      aue.startDate = -1;
    } catch(ParseException e) {
      throw new ConfigException("Bad start-date for user '" + aue.name + "': " + sdStr + " (expected dd-mm-yyyy)");
    }
    if(aue.startDate != -1 && aue.expireDate != -1)
      if(aue.startDate > aue.expireDate) throw new ConfigException("User '" + aue.name + "' start-date must be before expire-date.");

    try {
      aue.ecmRate = xml.getIntValue("ecm-rate");
    } catch(ConfigException e) {
      aue.ecmRate = -1;
    }

    try {
      aue.displayName = xml.getStringValue("display-name");
    } catch (ConfigException e) {}

    try {
      String allowedServices = xml.getStringValue("allowedServices");
      for(StringTokenizer st = new StringTokenizer(allowedServices); st.hasMoreTokens(); ) aue.allowedServices.add(st.nextToken());
    } catch (ConfigException e) {}

    try {
      String blockedServices = xml.getStringValue("blockedServices");
      for(StringTokenizer st = new StringTokenizer(blockedServices); st.hasMoreTokens(); ) aue.blockedServices.add(st.nextToken());
    } catch (ConfigException e) {}

    try {
      String allowedConnectors = xml.getStringValue("allowedConnectors");
      for(StringTokenizer st = new StringTokenizer(allowedConnectors); st.hasMoreTokens(); ) aue.allowedConnectors.add(st.nextToken());
    } catch (ConfigException e) {}

    return aue;
  }

  public boolean isEnabled(String user) {
    if(super.isEnabled(user)) {
      AdvUserEntry entry = (AdvUserEntry)getUser(user);
      if(entry == null) return true;
      if(entry.startDate == -1 && entry.expireDate == -1) return true;
      long now = System.currentTimeMillis();
      if(now < entry.startDate) return false; // start date not reached
      if(now > entry.expireDate) return false; // expire date passed
      return true;
    }
    return false;
  }

  public Set getAllowedServices(String user, String profile) {
    Set result = new HashSet();
    AdvUserEntry entry = (AdvUserEntry)getUser(user);
    if(entry != null && entry.allowedServices.isEmpty()) entry = (AdvUserEntry)defaultUser;
    if(entry != null && !entry.allowedServices.isEmpty()) {
      Iterator iterator = entry.allowedServices.iterator();
      while(iterator.hasNext()) {
        String service = (String) iterator.next();
        if (service.matches("^" + profile + ":[a-zA-Z0-9]*")) {
          result.add(new Integer(Integer.parseInt(service.replace(profile + ":", ""),16)));
        }
      }
    } else {
      result = null;
    }
    return result;
  } // return Set of Integer, null for all

  public Set getBlockedServices(String user, String profile) {
    Set result = new HashSet();
    AdvUserEntry entry = (AdvUserEntry)getUser(user);
    if(entry != null && entry.blockedServices.isEmpty()) entry = (AdvUserEntry)defaultUser;
    if(entry != null && !entry.blockedServices.isEmpty()) {
      Iterator iterator = entry.blockedServices.iterator();
      while(iterator.hasNext()) {
        String service = (String) iterator.next();
        if (service.matches("^" + profile + ":[a-zA-Z0-9]*")) {
          result.add(new Integer(Integer.parseInt(service.replace(profile + ":", ""),16)));
        }
      }
    } else {
      result = null;
    }
    return result;
  } // return Set of Integer, null for all

  public Set getAllowedConnectors(String user) {
    AdvUserEntry entry = (AdvUserEntry)getUser(user);
    if(entry != null && entry.allowedConnectors.isEmpty()) entry = (AdvUserEntry)defaultUser;
    if(entry != null && !entry.allowedConnectors.isEmpty()) {
      return entry.allowedConnectors;
    } else {
      return null;
    }
  } // return Set of String, null for all

  public int getAllowedEcmRate(String user) {
    AdvUserEntry entry = (AdvUserEntry)getUser(user);
    if(entry == null) return -1;
    else return entry.ecmRate;
  }

  static class AdvUserEntry extends UserEntry {
    int ecmRate;
    long startDate, expireDate;
    Set allowedServices = new HashSet();
    Set blockedServices = new HashSet();
    Set allowedConnectors = new HashSet();

    public AdvUserEntry(UserEntry ue) {
      super(ue.name, ue.password, ue.ipMask, ue.email, ue.maxConnections, ue.enabled, ue.admin, ue.exclude, ue.debug);
      profiles = ue.profiles;
    }
  }

}
