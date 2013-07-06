package com.bowman.cardserv;

import com.bowman.cardserv.util.*;
import com.bowman.cardserv.web.*;
import com.bowman.xml.*;

import java.net.*;
import java.io.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2007-okt-17
 * Time: 04:38:22
 */
public class XmlUserManager extends SimpleUserManager implements Runnable {

  private ProxyLogger logger;
  protected Thread fetchThread, updateThread;
  protected Map sources = new HashMap();
  protected Map lastParsed = new HashMap(); // last known good set
  protected long updateInterval;

  public XmlUserManager() {
    logger = ProxyLogger.getLabeledLogger(getClass().getName());
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    super.configUpdated(xml);

    sources.clear();
    Iterator iter = xml.getMultipleSubConfigs("user-source");
    if(iter != null) {
      while(iter.hasNext()) addSource((ProxyXmlConfig)iter.next(), null);
    }

    if(sources.isEmpty()) addSource(xml, "default"); // backwards compatible

    updateInterval = xml.getTimeValue("update-interval", 0, "m");
    if(updateInterval > 0) {
      if(updateThread == null) {
        updateThread = new Thread(this, "XmlUserManagerUpdateThread");
        updateThread.start();
      }
    } else {
      if(updateThread != null) {
        updateThread.interrupt();
        updateThread = null;
      }
    }

    pollUserSources();
    registerCtrlCommands();
  }

  protected void addSource(ProxyXmlConfig xml, String name) throws ConfigException {

    if(name == null) name = xml.getStringValue("name");
    XmlUserSource source = new XmlUserSource(name);

    String url = xml.getStringValue("user-file-url");
    try {
      source.fileUri = new URL(url);
    } catch(MalformedURLException e) {
      throw new ConfigException(xml.getFullName(), "user-file-url", "Malformed URL: " + e.getMessage());
    }

    try {
      source.key = xml.getStringValue("user-file-key");
    } catch (ConfigException e) {
      source.key = null;
    }

    source.lastModified = -1;

    sources.put(source.name, source);

    UserData last = (UserData)lastParsed.get(source.name);
    if(last != null) users.putAll(last.users); // keep any previous users as super.configUpdated() clears them
  }

  protected void registerCtrlCommands() {
    try {
      new CtrlCommand("update-users", "Run update", "Fetch/install user files now.").register(this);
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }
  }

  public CtrlCommandResult runCtrlCmdUpdateUsers() {
    pollUserSources();
    return new CtrlCommandResult(true, "Updated performed.");
  }

  public boolean isEnabled(String user) {
    if(matchesOpen(user)) return true;
    UserEntry entry = getUser(user);
    if(entry == null) return false;
    else return entry.enabled;
  }

  protected void pollUserSources() {

    XmlUserSource source; UserData last;
    for(Iterator iter = sources.values().iterator(); iter.hasNext();) {
      source = (XmlUserSource)iter.next();
      last = (UserData)lastParsed.get(source.name);

      try {
        logger.fine("[" + source.name + "] Fetching '" + source.fileUri + (source.lastModified != -1?", lm: " +
            new Date(source.lastModified):""));
        String newFile = FileFetcher.fetchFile(source.fileUri, source.key, source.lastModified);
        if(newFile != null) {
          if(last != null && last.userFile.hashCode() == newFile.hashCode())
            logger.fine("[" + source.name + "] No changes found after fetch...");
          else if(processUserFile(source, newFile)) {
            last = (UserData)lastParsed.get(source.name);
            logger.fine ("[" + source.name + "] Parsed " + last.users.size() + " users from '" + source.fileUri + "'");                
          }
        } else logger.fine("[" + source.name + "] User file unchanged...");
      } catch(IOException e) {
        logger.throwing(e);
        logger.warning("Failed to fetch user file '" + source.fileUri +"': " + e);
      }

    }
  }

  protected boolean processUserFile(XmlUserSource source, String newFile) {
    try {
      ProxyXmlConfig xml = new ProxyXmlConfig(new XMLConfig(newFile, false));
      Map newUsers = new HashMap();
      for(Iterator iter = xml.getMultipleSubConfigs("user"); iter.hasNext(); )
        addUser(parseUser((ProxyXmlConfig)iter.next()), newUsers, true);

      // update/overwrite
      users.putAll(newUsers);

      // remove any deleted
      UserData last = (UserData)lastParsed.get(source.name);
      if(last != null) {
        last.users.keySet().removeAll(newUsers.keySet());
        if(!last.users.keySet().isEmpty()) {
          logger.fine("[" + source.name + "] Removing " + last.users.keySet().size() + " deleted users.");
          for(Iterator iter = last.users.keySet().iterator(); iter.hasNext(); )
            users.remove(iter.next());
        }
      }

      lastParsed.put(source.name, new UserData(newUsers, newFile));
      source.lastModified = System.currentTimeMillis();
      return true;
    } catch(XMLConfigException e) {
      logger.throwing(e);
      logger.warning("Unable to parse '" + source.fileUri + "': " + e.getMessage());
    } catch(ConfigException e) {
      logger.throwing(e);
      logger.warning("Error in user file '" + source.fileUri + "': " + e.getMessage());
    }
    return false;
  }

  public void run() {
    if(Thread.currentThread() == updateThread) {
      while(updateThread != null) {
        try {
          Thread.sleep(updateInterval);
          fetchThread = new Thread(this, "XmlUserManagerFetchThread");
          fetchThread.start();
        } catch(InterruptedException e) {
          return;
        }
      }
    } else if(Thread.currentThread() == fetchThread) {
      pollUserSources();
    }
  }

  static class XmlUserSource {

    URL fileUri;
    String name, userFile, key;
    long lastModified;

    XmlUserSource(String name) {
      this.name = name;
    }
    
  }

  static class UserData {

    Map users;
    String userFile;

    UserData(Map users, String userFile) {
      this.users = users;
      this.userFile = userFile;
    }
  }

}
