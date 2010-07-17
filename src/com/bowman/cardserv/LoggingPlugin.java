package com.bowman.cardserv;

import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.web.*;
import com.bowman.util.Globber;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.*;
import java.lang.reflect.Field;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 8, 2005
 * Time: 5:48:53 PM
 */
public class LoggingPlugin implements ProxyPlugin {

  private static Map newcamdCmds;

  static {
    try {
      Field[] fields = CamdConstants.class.getFields();
      newcamdCmds = new TreeMap();
      for(int i = 0; i < fields.length; i++) {
        if(fields[i].getName().startsWith("MSG_")) newcamdCmds.put(fields[i].getName(), fields[i].get(null));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private ProxyLogger proxyLogger;
  private CardServProxy proxy;
  private Map loggers = new HashMap();
  private String logDir, ipFilter;
  private Level logLevel;

  private CtrlCommand filterCmd, delayCmd, sendCmd;
  private long delay = 0;

  public LoggingPlugin() {
    proxyLogger = ProxyLogger.getLabeledLogger(getClass().getName());
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    logLevel = ProxyLogger.getLogLevel(xml.getStringValue("log-level"));
    logDir = xml.getFileValue("log-dir", true, true);
    ipFilter = xml.getStringValue("ip-filter", "*");

    proxyLogger.fine("Configuration updated");
  }

  public void start(CardServProxy proxy) {
    filterCmd = new CtrlCommand("set-ip-filter", "Set IP filter",
        "Set the IP filter for the plugin, wildcards supported (only traffic for matching addresses will be logged).");
    filterCmd.addParam("filter", "").setValue(ipFilter);
    delayCmd = new CtrlCommand("set-test-delay", "Set test-delay",
        "Set a fixed delay time (in ms) applied to each logged request before processing, for testing purposes.");
    delayCmd.addParam("delay", "").setValue(String.valueOf(delay));
    sendCmd = new CtrlCommand("send-to-session", "Send to session",
        "Send an arbitrary newcamd msg to a connected client session, for testing purposes.");
    sendCmd.addParam("session-id", "");
    sendCmd.addParam("cmd", "cmd").setOptions(newcamdCmds.keySet(), false);
    sendCmd.addParam("bytes", "bytes").setValue(" ");

    try {
      filterCmd.register(this);
      delayCmd.register(this);
      sendCmd.register(this);
    } catch(NoSuchMethodException e) {
      e.printStackTrace();
    }

    this.proxy = proxy;
  }

  public void stop() {
    if(filterCmd != null) filterCmd.unregister();
    if(delayCmd != null) delayCmd.unregister();
    if(sendCmd != null) sendCmd.unregister();

    for(Iterator iter = loggers.values().iterator(); iter.hasNext(); ) ((ProxyLogger)iter.next()).close();    
    loggers.clear();
  }

  public String getName() {
    return "LoggingPlugin";
  }

  public String getDescription() {
    return "Logs all ecm traffic between client sessions and proxy to file.";
  }

  public Properties getProperties() {
    Properties p = new Properties();
    p.setProperty("ip-filter" , ipFilter);
    p.setProperty("delay", String.valueOf(delay));
    p.setProperty("loggers", String.valueOf(loggers.size()));
    return p;
  }

  public CtrlCommandResult runCtrlCmdSetIpFilter(Map params) {
    String newFilter = (String)params.get("filter");
    if(newFilter == null || "".equals(newFilter)) {
      ipFilter = "*";
      return new CtrlCommandResult(true, "IP filter cleared (set to '*').");
    } else {
      ipFilter = newFilter;
      return new CtrlCommandResult(true, "IP filter set to: " + ipFilter);
    }
  }

  public CtrlCommandResult runCtrlCmdSetTestDelay(Map params) {
    String newDelay = (String)params.get("delay");
    if(newDelay == null || "".equals(newDelay)) {
      setDelay(0);
      return new CtrlCommandResult(true, "Test-delay removed.");
    } else {
      setDelay(Long.parseLong(newDelay));
      return new CtrlCommandResult(true, "Test-delay set to " + delay + " ms");
    }
  }

  public void setDelay(long delay) {
    if(delay < 0) delay = 0;
    this.delay = delay;
    if(delayCmd != null) delayCmd.getParam("delay").setValue(String.valueOf(delay));
  }

  public CtrlCommandResult runCtrlCmdSendToSession(Map params) {
    String sessionId = (String)params.get("session-id");
    String cmdStr = (String)params.get("cmd");
    String byteStr = (String)params.get("bytes");
    ProxySession session = proxy.getSessionManager().getSession(sessionId);
    if(session == null || !"Newcamd".equals(session.getProtocol()))
      return new CtrlCommandResult(false, "No such newcamd sesssion: " + sessionId);
    Integer cmd = (Integer)newcamdCmds.get(cmdStr);
    if(cmd == null) return new CtrlCommandResult(false, "No such newcamd cmd: " + cmdStr);
    byte[] bytes;
    try {
      bytes = DESUtil.stringToBytes(byteStr);
    } catch (Exception e) {
      return new CtrlCommandResult(false, "Invalid byte string '" + byteStr + "': " + e);
    }
    CamdNetMessage msg = new CamdNetMessage(cmd.intValue());
    msg.setCustomData(bytes);
    int status = session.sendMessage(msg);
    if(status != -1) {
      sendCmd.getParam("session-id").setValue(sessionId);
      sendCmd.getParam("tag").setValue(cmdStr);
      if(bytes.length == 0) sendCmd.getParam("bytes").setValue(" ");
      else sendCmd.getParam("bytes").setValue(DESUtil.bytesToString(bytes));
    }
    return new CtrlCommandResult(status != -1, "Status: " + status + " Message sent: " + msg);
  }

  public CamdNetMessage doFilter(ProxySession session, CamdNetMessage msg) {
    if(msg.getType() == CamdNetMessage.TYPE_NEW)
      proxyLogger.warning("Bad message (not sent/received): " + msg); // skip pseudo-messages (locally generated)
    else logMessage(session, msg);
    return msg;
  }

  public byte[] getResource(String path, boolean admin) {
    return null;
  }

  public byte[] getResource(String path, byte[] data, boolean admin) {
    return null;
  }

  private void logMessage(ProxySession session, CamdNetMessage msg) {
    if(!Globber.match(ipFilter, msg.getRemoteAddress(), false)) return;
    ProxyLogger logger = getLogger(session.getLabel().replace(':', '_').replace('*', 'A').replace('?', 'Q'));
    if(logger == null) return;

    switch(msg.getType()) {

      case CamdNetMessage.TYPE_RECEIVED:
        logger.info(session.getLabel() + " Recv [" + msg.getLabel() + "]: " + DESUtil.bytesToString(msg.getRawIn()));
        if(!msg.isFiltered() && msg.isEcm() && delay > 0) try {
          logger.info(session.getLabel() + " Delaying [" + msg.getLabel() + "]: " + delay + " ms ...");
          Thread.sleep(delay);
        } catch(InterruptedException e) {
          e.printStackTrace();
        }
        break;
      case CamdNetMessage.TYPE_SENT:
        logger.info(session.getLabel() + " Sent [" + msg.getLabel() + "]: " + DESUtil.bytesToString(msg.getRawOut()));
        break;
    }
    if(msg.isFiltered()) logger.info(session.getLabel() + " Filtered [" + msg.getLabel() + "] Reason: " + msg.getFilteredBy());
  }

  private ProxyLogger getLogger(String session) {
    if(loggers.containsKey(session)) return (ProxyLogger)loggers.get(session);
    else {
      try {
        ProxyLogger logger = createFileLogger(session, new File(logDir, session + ".log"));
        loggers.put(session, logger);
        return logger;
      } catch(Exception e) {
        proxyLogger.warning("Unable to initialize logger FileHandler: " + e);
        proxyLogger.throwing(e);
      }
      return null;
    }
  }

  private ProxyLogger createFileLogger(String name, File logFile) throws IOException {
    ProxyLogger logger = ProxyLogger.getProxyLogger(ProxyLogger.LOG_BASE + "." + name);
    FileHandler handler = new FileHandler(logFile.getAbsolutePath(), true);
    handler.setFormatter(new CamdTrafficFormatter());
    logger.getWrappedLogger().addHandler(handler);
    logger.getWrappedLogger().setUseParentHandlers(false);
    logger.getWrappedLogger().setLevel(logLevel);
    return logger;
  }

  public String toString() {
    return getClass().getName();
  }

  static class CamdTrafficFormatter extends java.util.logging.Formatter {

    private static SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS");

    // todo
    public String format(LogRecord lr) {
      return "[" + fmt.format(new Date(lr.getMillis())) + "] " + lr.getMessage() + "\n";
    }
  }

}
