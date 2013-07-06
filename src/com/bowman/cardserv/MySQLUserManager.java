package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.MySQLConstants;
import com.bowman.cardserv.mysql.*;
import com.bowman.cardserv.util.*;

import java.util.*;

/**
 * A simple mysql usermanager to get the user informations directly from a mysql
 * database without having to load them over external xml sources.
 * For the database communication it uses the MySQL Connectors/J Driver (mysql-connector-java-5.1.XX-bin.jar)
 * found on "http://dev.mysql.com/downloads/connector/j/".
 *
 * @author DonCarlo
 * @since 04.12.2010
 */
public class MySQLUserManager extends XmlUserManager {

  private static final String DEFAULT_DBHOST = "127.0.0.1";
  private static final int DEFAULT_DBPORT = 3306;
  private static final String DEFAULT_DBNAME = "cardservproxy";

  private ProxyLogger logger = null;
  private UserCacheManager cacheManager = null;

  public MySQLUserManager() {
    logger = ProxyLogger.getLabeledLogger(getClass().getName());
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    try {
      super.configUpdated(xml);
    } catch(ConfigException e) {
      logger.throwing(e);
    }

    if(cacheManager != null) cacheManager.interrupt();

    cacheManager = new UserCacheManager(
        xml.getSubConfig("mysql-database").getStringValue("dbhost", DEFAULT_DBHOST),
        xml.getSubConfig("mysql-database").getPortValue("dbport", DEFAULT_DBPORT),
        xml.getSubConfig("mysql-database").getStringValue("dbname", DEFAULT_DBNAME),
        xml.getSubConfig("mysql-database").getStringValue("dbuser"),
        xml.getSubConfig("mysql-database").getStringValue("dbpassword")
    );
  }

  public String[] getUserNames() {
    List userNames = getMySQLUserNames();
    // now run through the rest (simple and xml usermanager) and merge them
    String[] tmp = super.getUserNames();
    for(int i = 0; i < tmp.length; i++) {
      if(!userNames.contains(tmp[i])) {
        userNames.add(tmp[i]);
      }
    }
    Collections.sort(userNames);
    return (String[])userNames.toArray(new String[userNames.size()]);
  }

  public String getPassword(String user) {
    if(super.getPassword(user) != null) {
      return super.getPassword(user);
    } else {
      return cacheManager.getUser(user).getPassword();
    }
  }

  public String getUserName(String user) {
    if(super.getPassword(user) != null) {
      return super.getUserName(user);
    } else {
      return cacheManager.getUser(user).getUserName();
    }
  }

  public boolean authenticate(String user, String pass) {
    if(super.getPassword(user) != null) {
      return super.authenticate(user, pass);
    } else {
      return (cacheManager.getUser(user) != null) && cacheManager.getUser(user).getPassword().equals(pass);
    }
  }

  public int getMaxConnections(String user) {
    if(super.getPassword(user) != null) {
      return super.getMaxConnections(user);
    } else {
      return cacheManager.getUser(user).getMaxConnections();
    }
  }

  public String getIpMask(String user) {
    if(super.getPassword(user) != null) {
      return super.getIpMask(user);
    } else {
      return cacheManager.getUser(user).getIpMask().equals("") ? "*" : cacheManager.getUser(user).getIpMask();
    }
  }

  public String getEmailAddress(String user) {
    if(super.getPassword(user) != null) {
      return super.getEmailAddress(user);
    } else {
      return cacheManager.getUser(user).getEmail();
    }
  }

  public String getDisplayName(String user) {
    if(super.getPassword(user) != null) {
      return super.getDisplayName(user);
    } else {
      User us = cacheManager.getUser(user);
      if(us.getDisplayName().equals("")) {
        return us.getUserName();
      } else
        return us.getDisplayName();
    }
  }

  public Set getAllowedProfiles(String user) {
    if(super.getPassword(user) != null) {
      return super.getAllowedProfiles(user);
    } else {
      return cacheManager.getUser(user).getAllowedProfiles();
    }
  }

  public boolean isEnabled(String user) {
    if(super.getPassword(user) != null) {
      return super.isEnabled(user);
    } else {
      return cacheManager.getUser(user).isEnabled();
    }
  }

  public boolean isAdmin(String user) {
    if(super.getPassword(user) != null) {
      return super.isAdmin(user);
    } else {
      return cacheManager.getUser(user).isAdmin();
    }
  }

  public boolean exists(String user) {
    if(super.getPassword(user) != null) {
      return super.exists(user);
    } else {
      return cacheManager.getUser(user) != null;
    }
  }

  public boolean isMapExcluded(String user) {
    if(super.getPassword(user) != null) {
      return super.isMapExcluded(user);
    } else {
      return cacheManager.getUser(user).isMapExcluded();
    }
  }

  public boolean isDebug(String user) {
    if(super.getPassword(user) != null) {
      return super.isDebug(user);
    } else {
      return cacheManager.getUser(user).isDebug();
    }
  }

  public void setDebug(String user, boolean debug) {
    if(super.getPassword(user) != null) {
      super.setDebug(user, debug);
    } else {
      cacheManager.setDebug(user, debug);
    }
  }

  public int getUserCount() {
    return getUserNames().length;
  }

  public Set getAllowedServices(String user, String profile) {
    if(super.getPassword(user) != null) {
      return super.getAllowedServices(user, profile);
    } else {
      return null;
    }
  }

  public Set getBlockedServices(String user, String profile) {
    if(super.getPassword(user) != null) {
      return super.getBlockedServices(user, profile);
    } else {
      return null;
    }
  }

  public Set getAllowedConnectors(String user) {
    if(super.getPassword(user) != null) {
      return super.getAllowedConnectors(user);
    } else {
      return null;
    }
  }

  public int getAllowedEcmRate(String user) {
    if(super.getPassword(user) != null) {
      return super.getAllowedEcmRate(user);
    } else {
      return -1;
    }
  }

  public List getMySQLUserNames(int skipRows, int numRows) {
    return cacheManager.getUserNames(skipRows, numRows);
  }

  public List getMySQLUserNames() {
    return getMySQLUserNames(MySQLConstants.DEFAULT_SKIP_ROWS, MySQLConstants.DEFAULT_NUM_ROWS);
  }

  public int getMySQLUserCount() {
    return getMySQLUserNames().size();
  }

  public User getMySQLUser(String userName) {
    return cacheManager.getUser(userName);
  }

  public boolean existsMySQLUser(String userName) {
    return cacheManager.getUser(userName) != null;
  }

  public boolean addUser(String username, String password, String displayname, String ipmask, int maxconnections,
                         boolean enabled, boolean debug, boolean admin, String mail, boolean mapexcluded,
                         Set allowedProfileIds)
  {
    return cacheManager.addUser(username, password, displayname, ipmask, maxconnections, enabled, debug, admin, mail,
        mapexcluded, allowedProfileIds);
  }

  public boolean editUser(int id, String username, String password, String displayname, String ipmask, int maxconnections,
                          boolean enabled, boolean debug, boolean admin, String mail, boolean mapexcluded,
                          Set allowedProfileIds)
  {
    return cacheManager.editUser(id, username, password, displayname, ipmask, maxconnections,
        enabled, debug, admin, mail, mapexcluded, allowedProfileIds);
  }

  public boolean deleteUser(String username) {
    return cacheManager.deleteUser(username);
  }

  public boolean deleteAllUsers(String skipUserName) {
    return cacheManager.deleteAllUsers(skipUserName);
  }

  public boolean importUsers(Set users) {
    return cacheManager.importUsers(users);
  }

  public List getProfiles() {
    return cacheManager.getProfiles();
  }

  public boolean existsProfile(int id) {
    return cacheManager.getProfile(id) != null;
  }

  public boolean existsProfile(String profileName) {
    return cacheManager.getProfile(profileName) != null;
  }

  public boolean addProfile(String profileName) {
    return cacheManager.addProfile(profileName);
  }

  public boolean editProfile(int id, String profileName) {
    return cacheManager.editProfile(id, profileName);
  }

  public boolean deleteProfile(int id) {
    return cacheManager.deleteProfile(id);
  }

  public boolean deleteAllProfiles() {
    return cacheManager.deleteAllProfiles();
  }

  public String getDatabaseHost() {
    return cacheManager.getDatabaseHost();
  }

  public String getDatabaseName() {
    return cacheManager.getDatabaseName();
  }

  public int getDatabasePort() {
    return cacheManager.getDatabasePort();
  }

  public String getDatabaseUser() {
    return cacheManager.getDatabaseUser();
  }

}
