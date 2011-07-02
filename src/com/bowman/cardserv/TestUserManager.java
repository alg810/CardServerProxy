package com.bowman.cardserv;

import com.bowman.cardserv.util.ProxyXmlConfig;

import java.text.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2011-06-30
 * Time: 23:26
 */
public class TestUserManager extends SimpleUserManager {

  static final SimpleDateFormat fmt = new SimpleDateFormat("yy-MM-dd");

  protected UserEntry parseUser(ProxyXmlConfig xml) throws ConfigException {
    TestUserEntry tue = new TestUserEntry(super.parseUser(xml)); // call SimpleUserManager parsing and wrap user entry

    String edStr = null;
    try {
      edStr = xml.getStringValue("expire-date");
      tue.expireDate = fmt.parse(edStr).getTime();
    } catch(ConfigException e) {
      tue.expireDate = -1;
    } catch(ParseException e) {
      throw new ConfigException("Bad expire-date for user '" + tue.name + "': " + edStr + " (expected yy-mm-dd)");
    }
    String sdStr = null;
    try {
      sdStr = xml.getStringValue("start-date");
      tue.startDate = fmt.parse(sdStr).getTime();
    } catch(ConfigException e) {
      tue.startDate = -1;
    } catch(ParseException e) {
      throw new ConfigException("Bad start-date for user '" + tue.name + "': " + sdStr + " (expected yy-mm-dd)");
    }
    if(tue.startDate != -1 && tue.expireDate != -1)
      if(tue.startDate > tue.expireDate) throw new ConfigException("User '" + tue.name + "' start-date must be before expire-date.");

    try {
      tue.ecmRate = xml.getIntValue("ecm-rate");
    } catch(ConfigException e) {
      tue.ecmRate = -1;
    }

    return tue;
  }

  public boolean isEnabled(String user) {
    if(super.isEnabled(user)) {
      TestUserEntry entry = (TestUserEntry)getUser(user);
      if(entry == null) return true;
      if(entry.startDate == -1 && entry.expireDate == -1) return true;
      long now = System.currentTimeMillis();
      if(now < entry.startDate) return false; // start date not reached
      if(now > entry.expireDate) return false; // expire date passed
      return true;
    }
    return false;
  }

  public int getAllowedEcmRate(String user) {
    TestUserEntry entry = (TestUserEntry)getUser(user);
    if(entry == null) return -1;
    else return entry.ecmRate;
  }

  static class TestUserEntry extends UserEntry {
    int ecmRate;
    long startDate, expireDate;

    public TestUserEntry(UserEntry ue) {
      super(ue.name, ue.password, ue.ipMask, ue.email, ue.maxConnections, ue.enabled, ue.admin, ue.exclude, ue.debug);
      profiles = ue.profiles;
    }
  }
}
