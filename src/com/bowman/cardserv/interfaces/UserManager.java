package com.bowman.cardserv.interfaces;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 8, 2005
 * Time: 4:09:03 PM
 */
public interface UserManager extends XmlConfigurable {

  String[] getUserNames();
  String getPassword(String user);
  String getUserName(String user); // returns the same string but with the stored case
  boolean authenticate(String user, String pass);
  int getMaxConnections(String user);
  String getExpireDate(String  user);
  String getStartDate(String s);
  String getIpMask(String user);
  String getEmailAddress(String user);
  String getDisplayName(String user);
  Set getAllowedProfiles(String user);
  boolean isEnabled(String user);
  boolean isAdmin(String user);
  boolean exists(String user);
  boolean isMapExcluded(String user);
  boolean isDebug(String user);
  void setDebug(String user, boolean debug);
  boolean isSpider(String user);
  int getUserCount();
  void start();

  // access control/limits
  Set getAllowedServices(String user, String profile); // return Set of Integer, null for all
  Set getBlockedServices(String user, String profile); // return Set of Integer, null for all
  Set getAllowedConnectors(String user); // return Set of String, null for all
  int getAllowedEcmRate(String user); // return minimum interval between ecm in seconds, -1 for no limit

}
