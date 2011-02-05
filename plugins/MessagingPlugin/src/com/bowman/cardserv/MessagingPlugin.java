package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.rmi.*;
import com.bowman.cardserv.web.*;
import com.bowman.util.*;

import java.util.*;
import java.text.MessageFormat;
import java.net.URL;
import java.lang.reflect.Method;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Jul 8, 2008
 * Time: 2:05:17 PM
 */
public class MessagingPlugin implements ProxyPlugin {

  protected RemoteHandler proxy;

  private Map userOsdStatus = new HashMap(), userMailStatus = new HashMap(), mailAggregate = new HashMap();
  private int minOsdInterval, minMailInterval;
  private String smtpServer, senderAddr;
  private int smtpPort;
  private String mailFooter = "";

  private Set commands = new HashSet();
  private TriggerMessenger osdMessenger, mailMessenger;
  private CronTimer fetchCron;
  protected ProxyLogger logger = ProxyLogger.getLabeledLogger(getClass().getName(), getName());

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {

    if(fetchCron == null) {
      fetchCron = new CronTimer("* * * * *");
      fetchCron.start();
    }

    ProxyXmlConfig autoOsdCfg = xml.getSubConfig("auto-mgcamd-osd");
    if("true".equals(autoOsdCfg.getStringValue("enabled", "true"))) {
      osdMessenger = new TriggerMessenger(this);
      minOsdInterval = autoOsdCfg.getTimeValue("min-interval", 20, "s");
      osdMessenger.configUpdated(autoOsdCfg);
      fetchCron.addTimerListener(osdMessenger);
    } else {
      osdMessenger = null;
    }

    ProxyXmlConfig mailCfg = xml.getSubConfig("email");
    if("true".equals(mailCfg.getStringValue("enabled", "true"))) {
      smtpServer = mailCfg.getStringValue("smtp-server");
      smtpPort = mailCfg.getIntValue("smtp-port", 25);
      senderAddr = mailCfg.getStringValue("sender-address");
      mailFooter = mailCfg.getStringValue("mail-footer", "");

      ProxyXmlConfig autoMailCfg = mailCfg.getSubConfig("auto-email");
      if("true".equals(autoMailCfg.getStringValue("enabled", "true"))) {
        mailMessenger = new TriggerMessenger(this);
        minMailInterval = autoMailCfg.getTimeValue("min-interval", 600, "s");
        mailMessenger.configUpdated(autoMailCfg);
        fetchCron.addTimerListener(mailMessenger);
      } else {
        mailMessenger = null;
      }
    } else {
      smtpServer = null;
      mailMessenger = null;
    }
  }


  public void start(CardServProxy proxy) {
    this.proxy = proxy.getRemoteHandler();
    if(osdMessenger != null) this.proxy.addRemoteListener(osdMessenger);
    if(smtpServer != null) {
      if(mailMessenger != null) this.proxy.addRemoteListener(mailMessenger);
      try {
        CtrlCommand cmd = new CtrlCommand("mail-user", "Send mail (single)", "Send a mail to the specified user.", true);
        cmd.addParam("text", "Mail body").setSize(4);
        cmd.addParam("name", "User").setOptions("@known-users", false);
        cmd.register(this);
        commands.add(cmd);

        cmd = new CtrlCommand("mail-profile", "Send mail (profile)", "Send a mail to all users in the specified profile.", true);
        cmd.addParam("text", "Mail body").setSize(4);
        cmd.addParam("name", "Profile").setOptions("@profiles", false);
        cmd.register(this);
        commands.add(cmd);
      } catch(NoSuchMethodException e) {
        e.printStackTrace();
      }
    }
  }

  public void stop() {
    if(proxy != null) {
      if(osdMessenger != null) proxy.removeRemoteListener(osdMessenger);
      if(mailMessenger != null) proxy.removeRemoteListener(mailMessenger);
      proxy = null;
    }
    Command cmd;
    for(Iterator iter = commands.iterator(); iter.hasNext(); ) {
      cmd = (Command)iter.next();
      cmd.unregister();
    }
    commands.clear();
    if(osdMessenger != null) {
      if(fetchCron != null) {
        fetchCron.removeTimerListener(osdMessenger);
        fetchCron.removeTimerListener(mailMessenger);
        fetchCron.stop();
        fetchCron = null;
      }
    }
  }

  public CtrlCommandResult runCtrlCmdMailUser(Map params, String admin) {
    String user = (String)params.get("name");
    String text = (String)params.get("text");
    if(user == null) return new CtrlCommandResult(false, "Missing parameter: name");
    if(text == null) return new CtrlCommandResult(false, "Missing parameter: text");
    String emailAddr = proxy.getEmailAddress(user);
    if(emailAddr == null || "".equals(emailAddr))
      return new CtrlCommandResult(false, user + " has no email address set.");

    String subject = "CSP message from: " + admin;
    try {
      sendMail(user, emailAddr, subject, text);
      return new CtrlCommandResult(true, "Mail sent.");
    } catch (Throwable e) {
      if(e.getCause() != null) e = e.getCause();
      logger.warning("Failed to send mail to '" + emailAddr + "': " + e);
      logger.throwing(e);
      return new CtrlCommandResult(false, "Sending failed: " + e.getMessage());
    }
  }

  public CtrlCommandResult runCtrlCmdMailProfile(Map params, String admin) {
    String profile = (String)params.get("name");
    String text = (String)params.get("text");
    if(profile == null) return new CtrlCommandResult(false, "Missing parameter: name");
    if(text == null) return new CtrlCommandResult(false, "Missing parameter: text");

    String subject = "CSP announcement from: " + admin;

    int noMail = 0, failed = 0, sent = 0;
    String[] userNames = proxy.getLocalUsers(profile);
    String emailAddr;
    for(int i = 0; i < userNames.length; i++) {
      emailAddr = proxy.getEmailAddress(userNames[i]);
      if(emailAddr == null) {
        noMail++;
        continue;
      }

      try {
        sendMail(userNames[i], emailAddr, subject, text);
        sent++;
      } catch(Throwable e) {
        if(e.getCause() != null) e = e.getCause();
        logger.warning("Failed to send mail to '" + emailAddr + "': " + e);
        logger.throwing(e);
        failed++;
      }
    }
    return new CtrlCommandResult(true, "Sending complete. Counters: no address set = " + noMail + ", failed = " +
        failed + ", sent ok = " + sent);
  }

  protected void sendMail(String user, String recipient, String subject, String text) throws Exception {
    if(mailFooter.length() > 0)
      text = text + "\n\n" + MessageFormat.format(mailFooter, new Object[] {user, CardServProxy.APP_VERSION});    
    Class mailer = Class.forName("com.bowman.util.MailerWrapper");
    Method sendMethod = mailer.getMethod("send", new Class[] {String.class, int.class, String.class, String.class, String.class, String.class, URL.class});
    sendMethod.invoke(null, new Object[] {smtpServer, new Integer(smtpPort), recipient, senderAddr, subject, text, null});
    // MailerWrapper.send(smtpServer, smtpPort, recipient, senderAddr, subject, text, null);
    logger.info("Mail sent to user '" + user + "' (" + recipient + "): " + text);
  }

  public String getName() {
    return "MessagingPlugin";
  }

  public String getDescription() {
    return "Automated mgcamd osd replies and email functionality.";
  }
  
  public Properties getProperties() {
    return null;
  }

  public CamdNetMessage doFilter(ProxySession proxySession, CamdNetMessage msg) {
    return msg;
  }

  public byte[] getResource(String path, boolean admin) {
    return null;
  }

  public byte[] getResource(String path, byte[] inData, boolean admin) {
    return null;
  }

  public void addDynamicServiceTrigger(String name, TvService ts, String message) throws ConfigException {
    if(osdMessenger != null) osdMessenger.addDynamicServiceTrigger(name, ts, message);
  }

  public void clearDynamicServiceTriggers(String name) {
    if(osdMessenger != null) osdMessenger.clearDynamicServiceTrigger(name);
  }

  protected void triggeredMessage(TriggerMessenger tm, String target, String text) {
    if(tm == osdMessenger) sendOsdMessage(target, text);
    else if(tm == mailMessenger) sendAutoMail(target, text);
  }

  protected void sendOsdMessage(String user, String text) {
    long now = System.currentTimeMillis();
    Long last = (Long) userOsdStatus.get(user);
    if(last != null) if(now - last.longValue() < minOsdInterval) {
      logger.fine("Flood detected for user '" + user + "', skipping msg: " + text);
      return;
    }
    proxy.sendOsdMessage(user, text);
    userOsdStatus.put(user, new Long(now));
  }

  protected void sendAutoMail(String target, String text) {
    String emailAddr;
    if(target.indexOf('@') != -1 && target.indexOf('.') != -1) emailAddr = target;
    else emailAddr = proxy.getEmailAddress(target);
    if(emailAddr == null || "".equals(emailAddr)) {
      logger.fine("User '" + target + "' has no email address set, skipping message: " + text);
      return;
    }

    long now = System.currentTimeMillis();

    Long last = (Long)userMailStatus.get(target);
    if(last != null) if(now - last.longValue() < minMailInterval) {
      logger.fine("Flood detected for user '" + target + "', aggregating mail: " + text);
      String mail = (String)mailAggregate.get(target);
      if(mail == null) mail = text;
      else mail = mail + "\n" + text;
      mailAggregate.put(target, mail);
      return;
    }

    String mail = (String)mailAggregate.get(target);
    if(mail == null) mail = text;
    else mail = mail + "\n" + text;
    mailAggregate.remove(target);

    try {
      sendMail(target, emailAddr, "CSP alert", mail);
    } catch(Throwable e) {
      logger.throwing(e);
      if(e.getCause() != null) e = e.getCause();
      logger.warning("Failed to send auto mail to '" + emailAddr + "' using '" + smtpServer + "': " + e);
    }

    userMailStatus.put(target, new Long(now));

  }

}
