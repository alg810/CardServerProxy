package com.bowman.cardserv.web;

import com.bowman.cardserv.*;
import com.bowman.cardserv.cws.ServiceMapping;
import com.bowman.cardserv.interfaces.CommandManager;
import com.bowman.cardserv.rmi.*;
import com.bowman.cardserv.session.*;
import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.util.*;
import com.bowman.xml.XMLConfig;

import java.io.*;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Feb 6, 2008
 * Time: 9:44:41 PM
 */
public class XmlHelper implements CommandManager {

  private static final SimpleDateFormat rfc822fmt = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);  
  private static final String[] CWS_STATES = {"disconnected", "connected", "connecting", "", "unresponsive", "disabled"};
  private static boolean checkFileDescriptors = true;

  private RemoteProxy proxy;
  private WebBackend webBackend;

  Map ctrlCommands = new LinkedHashMap();
  Map statusCommands = new LinkedHashMap();

  public XmlHelper(WebBackend webBackend) {
    this.proxy = webBackend.proxy;
    this.webBackend = webBackend;

    registerCommands();    
  }

  void registerCommands() {
    Command.setManager(this);
    // built in control commands
    try {
      registerControlCommands(WebBackend.class.getResourceAsStream("ctrl-commands.xml"), this, "Internal");
    } catch (Exception e) {
      webBackend.logger.severe("Failed to load/parse internal control commands (ctrl-commands.xml).", e);
    }
    // built in status commands
    try {
      registerStatusCommands(WebBackend.class.getResourceAsStream("status-commands.xml"), this, "Internal");
    } catch (Exception e) {
      webBackend.logger.severe("Failed to load/parse internal status commands (status-commands.xml).", e);
    }
  }

  public static Set registerStatusCommands(InputStream is, Object handler, String label) throws Exception {
    ProxyXmlConfig cmdDefs = new ProxyXmlConfig(new XMLConfig(is, false, "UTF-8"));
    StatusCommand cmd; Set commands = new HashSet();
    for(Iterator iter = cmdDefs.getMultipleSubConfigs("command"); iter.hasNext(); ) {
      cmd = StatusCommand.createFromXml((ProxyXmlConfig)iter.next());
      cmd.register(handler, label);
      commands.add(cmd);
    }
    return commands;
  }

  public static Set registerControlCommands(InputStream is, Object handler, String label) throws Exception {
    ProxyXmlConfig cmdDefs = new ProxyXmlConfig(new XMLConfig(is, false, "UTF-8"));
    CtrlCommand cmd; Set commands = new HashSet();
    for(Iterator iter = cmdDefs.getMultipleSubConfigs("command"); iter.hasNext(); ) {
      cmd = CtrlCommand.createFromXml((ProxyXmlConfig)iter.next());
      cmd.register(handler, label);
      commands.add(cmd);
    }
    return commands;
  }  

  Set getServices(String[] profiles) throws RemoteException {
    CwsStatus[] connectors = proxy.getMultiCwsStatus(profiles);
    Set all = new HashSet();
    TvService[] services;
    for(int i = 0; i < connectors.length; i++) {
      services = proxy.getServices(connectors[i].getName(), false);
      if(services != null) all.addAll(Arrays.asList(services));
    }
    return all;
  }

  public String onQryStatusCommand(String cmd, Map params, String authUser) throws RemoteException {
    XmlStringBuffer xb = new XmlStringBuffer();
    xb.appendElement("cws-status-resp", "ver", "1.0");
    String profile = (String)params.get("profile");
    ProfileStatus[] ps = proxy.getUserProfiles(authUser);
    if(ps == null) ps = proxy.getProfiles();
    String[] profiles = getProfileNames(ps, profile);
    params.put("ps", ps);
    params.put("profiles", profiles);
    runStatusCmd(cmd, xb, params, authUser);
    xb.closeElement("cws-status-resp");
    return xb.toString();
  }

  public String onQryControlCommand(String cmd, Map params, String authUser) throws RemoteException {
    XmlStringBuffer xb = new XmlStringBuffer();
    xb.appendElement("cws-command-resp", "ver", "1.0");
    if(!proxy.isAdmin(authUser)) appendCmdResult(cmd, new CtrlCommandResult(false, "Admin user required.", null), xb);
    else appendCmdResult(cmd, runCtrlCmd(cmd, params, authUser), xb);
    xb.closeElement("cws-command-resp");
    return xb.toString();
  }

  private void appendCmdResult(String cmd, CtrlCommandResult res, XmlStringBuffer xb) {
    xb.appendElement("cmd-result", "command", cmd);
    xb.appendAttr("success", res.success);
    if(res.data != null) xb.appendAttr("data", res.data.toString());
    xb.endElement(false);
    xb.appendText(res.message);
    xb.closeElement("cmd-result");
  }

  public CtrlCommandResult runCtrlCmdShutdown() throws RemoteException {
    proxy.shutdown();
    return new CtrlCommandResult(true, "Shutdown initiated.", null);
  }

  public CtrlCommandResult runCtrlCmdResetConnector(Map params) throws RemoteException {
    return runCtrlCmdReset(params);
  }

  public CtrlCommandResult runCtrlCmdResetService(Map params) throws RemoteException {
    return runCtrlCmdReset(params);
  }

  private CtrlCommandResult runCtrlCmdReset(Map params) throws RemoteException {
    boolean result = false; int count = -1;
    String resultMsg;
    String name = (String)params.get("name");
    String idStr = (String)params.get("id");
    String profile = (String)params.get("profile");
    boolean full = "true".equalsIgnoreCase((String)params.get("full"));
    if(name != null) {
      if("ALL".equals(name)) {
        CwsStatus[] connectors = proxy.getMultiCwsStatus(null);
        count = 0;
        for(int i = 0; i < connectors.length; i++)
          count += proxy.resetStatus(connectors[i].getName(), full);
        resultMsg = "Service map for 'ALL' reset (" + count + " entries cleared).";
        result = true;
      } else {
        count = proxy.resetStatus(name, full);
        result = count != -1;
        resultMsg = result?"Service map for '" + name + "' reset (" + count + " cleared).":"No such connector: " + name;
      }
    } else if(idStr != null) {
      if(profile == null) resultMsg = "Missing parameter: profile";
      else {
        try {
          Set services = ProxyConfig.getServiceTokens("id", idStr, false);
          ServiceMapping sm = (ServiceMapping)services.iterator().next();
          result = proxy.resetStatus(profile, sm.serviceId, sm.getCustomData());
          resultMsg = result?"Service status for '" + profile + ":" + idStr + "' reset.":"No match found for: " + idStr;
        } catch (ConfigException e) {
          resultMsg = e.getMessage();
        }
      }
    } else resultMsg = "Missing parameter: name or id";
    return new CtrlCommandResult(result, resultMsg, count>-1?new Integer(count):null);
  }

  public CtrlCommandResult runCtrlCmdRetryConnector(Map params) throws RemoteException {
    boolean result = false;
    String resultMsg;
    String name = (String)params.get("name");
    if(name != null) {
      proxy.retryConnector(name);
      result = true;
      resultMsg = "Connector notified, attempting reconnect.";
    } else resultMsg = "Missing parameter: name";
    return new CtrlCommandResult(result, resultMsg, null);
  }

  public CtrlCommandResult runCtrlCmdDisableConnector(Map params) throws RemoteException {
    boolean result = false;
    String resultMsg;
    String name = (String)params.get("name");
    if(name != null) {
      proxy.disableConnector(name);
      result = true;
      resultMsg = "Connector disabled.";
    } else resultMsg = "Missing parameter: name";
    return new CtrlCommandResult(result, resultMsg, null);
  }

  public CtrlCommandResult runCtrlCmdSetConnectorMetric(Map params) throws RemoteException {
    boolean result = false;
    String resultMsg;
    String name = (String)params.get("name");
    String metricStr = (String)params.get("metric");
    if(metricStr == null) return new CtrlCommandResult(false, "Missing parameter: metric", null);
    int metric = 1;
    try {
      metric = Integer.parseInt(metricStr);
    } catch (NumberFormatException e) {
      return new CtrlCommandResult(false, "Bad metric value: " + metricStr, null);
    }
    if(name != null) {
      proxy.setConnectorMetric(name, metric);
      result = true;
      resultMsg = "Metric set for: " + name;
    } else resultMsg = "Missing parameter: name";
    return new CtrlCommandResult(result, resultMsg, null);
  }

  public CtrlCommandResult runCtrlCmdSetAuUser(Map params) throws RemoteException {
    boolean result = false;
    String resultMsg;
    String name = (String)params.get("name");
    String user = (String)params.get("user");
    if(user == null) return new CtrlCommandResult(false, "Missing parameter: user", null);
    if(name != null) {
      result = proxy.setAuUser(name, user);
      resultMsg = result?"Temp au-user '" + user + "' toggled for: " + name:"Invalid connector/user";
    } else resultMsg = "Missing parameter: name";
    return new CtrlCommandResult(result, resultMsg, null);
  }

  public CtrlCommandResult runCtrlCmdSetProfileDebug(Map params) throws RemoteException {
    boolean value = "true".equalsIgnoreCase((String)params.get("value"));
    String profile = (String)params.get("profile");
    if("ALL".equals(profile)) profile = null;

    proxy.setProfileDebug(value, profile);
    if(!value) webBackend.clearTransactions(profile);
    return new CtrlCommandResult(true, "Debug flag set for: " + (profile == null?"ALL":profile), null);
  }

  public CtrlCommandResult runCtrlCmdSetUserDebug(Map params) throws RemoteException {
    boolean value = "true".equalsIgnoreCase((String)params.get("value"));
    String user = (String)params.get("name");

    if(proxy.setUserDebug(value, user)) return new CtrlCommandResult(true, "Debug flag set for: " + user);
    else return new CtrlCommandResult(false, "No such user connected: " + user);
  }

  public CtrlCommandResult runCtrlCmdKickUser(Map params) throws RemoteException {
    boolean result = false; int count = 0;
    String resultMsg;
    String name = (String)params.get("name");
    if(name != null) {
      count = proxy.kickUser(name);
      if(count != -1) result = true;
      if(count == 0) resultMsg = "User '" + name + "' not connected.";
      else resultMsg = result?"User '" + name + "' kicked: " + count + " sessions closed.":"No such user: " + name;
    } else resultMsg = "Missing parameter: name";
    return new CtrlCommandResult(result, resultMsg, count>-1?new Integer(count):null);
  }

  public CtrlCommandResult runCtrlCmdOsdMessage(Map params) throws RemoteException {
    boolean result = false; int count = -1;
    String resultMsg;
    String text = (String)params.get("text");
    String name = (String)params.get("name");
    if("ALL".equalsIgnoreCase(name)) name = null;
    if(text == null) resultMsg = "Missing parameter: text";
    else {
      count = proxy.sendOsdMessage(name, text);
      if(count > 0) result = true;
      resultMsg = result?"Message sent to " + count + " active and compatible newcamd sessions.":"No active/compatible newcamd sessions found";
    }
    return new CtrlCommandResult(result, resultMsg, count>-1?new Integer(count):null);
  }

  public CtrlCommandResult runCtrlCmdRemoveSeen(Map params) throws RemoteException {
    String name = (String)params.get("name");
    if("ALL".equalsIgnoreCase(name)) name = null;
    int count = proxy.removeSeenUser(name);
    return new CtrlCommandResult(count > 0, "Removed " + count + " matching entries from the seen log.", new Integer(count));
  }

  public CtrlCommandResult runCtrlCmdRemoveFailed(Map params) throws RemoteException {
    String mask = (String)params.get("mask");
    if(mask == null) mask = "*";
    int count = proxy.removeLoginFailure(mask);
    return new CtrlCommandResult(count > 0, "Removed " + count + " matching entries from the failure log.", new Integer(count));
  }


  public CtrlCommandResult runCtrlCmdClearWarnings() throws RemoteException {
    webBackend.warningLog.clear();
    return new CtrlCommandResult(true, "Warnings cleared.");
  }

  public CtrlCommandResult runCtrlCmdClearEvents() throws RemoteException {
    webBackend.eventLog.clear();
    return new CtrlCommandResult(true, "Events cleared.");
  }

  public CtrlCommandResult runCtrlCmdClearFileLog() throws RemoteException {
    webBackend.fileLog.clear();
    return new CtrlCommandResult(true, "File log events cleared.");
  }  

  public CtrlCommandResult runCtrlCmdGenKeystore(Map params) throws RemoteException {
    String password = (String)params.get("password");
    String host = (String)params.get("host");
    String validity = (String)params.get("validity");
    if(password == null || password.length() < 2) return new CtrlCommandResult(false, "Missing parameter: password");
    if(host == null || host.length() < 2) return new CtrlCommandResult(false, "Missing parameter: host");
    if(validity == null || validity.length() == 0) validity = "1000";
    File binDir = new File(System.getProperty("java.home"), "bin");
    if(binDir.exists() && binDir.isDirectory()) {
      File keyTool = new File(binDir, "keytool");
      if(!keyTool.exists()) keyTool = new File(binDir, "keytool.exe");
      if(keyTool.exists()) {
        try {
          String cmdLine = keyTool.getAbsolutePath();
          cmdLine = cmdLine + " -keystore csp_keystore -genkey -alias Cardservproxy -keyalg RSA";
          cmdLine = cmdLine + " -storepass " + password + " -keypass " + password;
          cmdLine = cmdLine + " -dname cn=" + host + " -validity " + validity;

          Process p = Runtime.getRuntime().exec(cmdLine);
          BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
          String line; StringBuffer sb = new StringBuffer();
          while((line = br.readLine()) != null) {
            sb.append(line).append(" ");
            webBackend.logger.warning(line);
          }
          int exitValue = p.waitFor();
          if(exitValue != 0) {
            webBackend.logger.warning("Keytool exit value: " + exitValue + " CmdLine: " + cmdLine);
            return new CtrlCommandResult(false, "Keystore generation failed: " + sb, new Integer(exitValue));
          } else {
            return new CtrlCommandResult(true, "Keystore file 'csp_keystore' successfully created in proxy dir (probably). Enabling ssl requires restart.");
          }

        } catch(Exception e) {
          e.printStackTrace();
          return new CtrlCommandResult(false, "Error occured: " + e);
        }
      }
    }
    return new CtrlCommandResult(false, "Couldn't find keytool executable.");
  }

  String getCfgXml() throws IOException {
    File cfgFile = ProxyConfig.getInstance().getCfgFile();
    byte[] buf = new byte[(int)cfgFile.length()];
    DataInputStream dis = new DataInputStream(new FileInputStream(cfgFile));
    dis.readFully(buf);
    dis.close();
    return new String(buf, "UTF-8");
  }

  CtrlCommandResult runCtrlCmd(String cmd, Map params, String user) throws RemoteException {
    CtrlCommand cmdDef = (CtrlCommand)ctrlCommands.get(cmd);
    CtrlCommandResult cmdRes = cmdDef.invoke(params, user);
    webBackend.logger.info("CtrlCommand '" + cmd + "' executed by user: " + user + " (result: " + cmdRes.message + ")");
    return cmdRes;
  }

  public void runStatusCmdCaProfiles(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    String profileName = (String)params.get("name");
    ProfileStatus[] ps = (ProfileStatus[])params.get("ps");
    ProfileStatus[] psShow = null;
    if(profileName != null) {
      for(int i = 0; i < ps.length; i++) {
        if(profileName.equals(ps[i].getName())) {
          psShow = new ProfileStatus[] {ps[i]};
          break;
        }
      }
    } else psShow = ps;
    xmlFormatProfiles(psShow, xb, proxy.isAdmin(user));
  }

  public void runStatusCmdCwsConnectors(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    String[] profiles = (String[])params.get("profiles");
    CwsStatus[] connectors = null;
    String cwsName = (String)params.get("name");
    if(cwsName != null) {
      CwsStatus temp = proxy.getCwsStatus(cwsName);
      if(temp != null) connectors = new CwsStatus[] {temp};
    } else {
      connectors = proxy.getMultiCwsStatus(profiles);
    }
    xmlFormatConnectors(connectors, xb, proxy.isAdmin(user), webBackend.cwsTransactions, proxy, profiles);
  }

  public void runStatusCmdProxyUsers(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    String[] profiles = (String[])params.get("profiles");
    UserStatus[] users = null;
    String userName = (String)params.get("name");
    boolean activeOnly = "true".equalsIgnoreCase((String)params.get("hide-inactive"));
    if(!proxy.isAdmin(user)) userName = user;
    if(userName != null) {
      UserStatus temp = proxy.getUserStatus(userName, activeOnly);
      if(temp != null) users = new UserStatus[] {temp};
    } else {
      users = proxy.getUsersStatus(profiles, activeOnly);
    }
    SeenEntry[] seen = proxy.getSeenUsers(null, userName, true);
    xmlFormatUsers(users, seen.length, xb);
  }

  public void runStatusCmdProxyPlugins(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    String name = (String)params.get("name");
    xmlFormatProxyPlugins(xb, name, proxy.isAdmin(user));
  }

  public void runStatusCmdProxyStatus(XmlStringBuffer xb, Map params) throws RemoteException {
    xmlFormatProxyStatus(xb, (String[])params.get("profiles"));
  }

  public void runStatusCmdCacheStatus(XmlStringBuffer xb) throws RemoteException {
    CacheStatus cs = proxy.getCacheStatus();
    xmlFormatCacheStatus(cs, xb);
  }

  public void runStatusCmdErrorLog(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    xmlFormatErrorLog(xb, (String[])params.get("profiles"), proxy.isAdmin(user));
  }

  public void runStatusCmdFileLog(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    if(!proxy.isAdmin(user) || !ProxyConfig.getInstance().isIncludeFileEvents()) {
      xb.appendElement("file-log", "size", "-1", true);
    } else {
      xmlFormatFileLog(xb);
    }
  }

  public void runStatusCmdUserLog(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    String userName = (String)params.get("name");
    if(userName == null || !proxy.isAdmin(user)) userName = user;
    xmlFormatUserLog(xb, userName, (String[])params.get("profiles"));
  }

  public void runStatusCmdUserWarningLog(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    String[] profiles = (String[])params.get("profiles");
    if(proxy.isAdmin(user)) xmlFormatUserWarningLog(xb, null, profiles);
    else xmlFormatUserWarningLog(xb, user, profiles);
  }

  public void runStatusCmdCwsLog(XmlStringBuffer xb, Map params) throws RemoteException {
    xmlFormatCwsLog(xb, (String)params.get("name"));
  }

  public void runStatusCmdAllServices(XmlStringBuffer xb, Map params) throws RemoteException {
    String[] profiles = (String[])params.get("profiles");
    Set all = getServices(profiles);
    List sorted = new ArrayList(all);
    Collections.sort(sorted);
    xb.appendElement("all-services", "count", sorted.size());
    xmlFormatServices((TvService[])sorted.toArray(new TvService[sorted.size()]), xb, false, true, true, null, profiles);
    xb.closeElement("all-services");
  }

  public void runStatusCmdWatchedServices(XmlStringBuffer xb, Map params) throws RemoteException {
    String[] profiles = (String[])params.get("profiles");
    List watched = Arrays.asList(proxy.getWatchedServices(profiles));
    Collections.sort(watched);
    xb.appendElement("watched-services", "count", watched.size());
    xmlFormatServices((TvService[])watched.toArray(new TvService[watched.size()]), xb, true, true, true, null, profiles);
    xb.closeElement("watched-services");
  }

  public void runStatusCmdExportServices(XmlStringBuffer xb, Map params) throws RemoteException {
    String[] profiles = (String[])params.get("profiles");
    String format = (String)params.get("format");
    CwsStatus[] connectors;
    String cwsName = (String)params.get("name");
    if(cwsName != null) {
      CwsStatus temp = proxy.getCwsStatus(cwsName);
      if(temp != null) connectors = new CwsStatus[] {temp};
      else connectors = new CwsStatus[0];
    } else {
      connectors = proxy.getMultiCwsStatus(profiles);
    }
    xb.appendElement("export-services", "connectors", connectors.length);
    TvService[] services;
    for(int i = 0; i < connectors.length; i++) {
      xb.appendElement("connector", "name", connectors[i].getName());
      services = proxy.getServices(connectors[i].getName(), false);
      if(services != null) {
        xb.appendElement("can-decode-services", "count", services.length);
        if("hex".equalsIgnoreCase(format)) xmlFormatHexTokenList(services, xb);
        else xmlFormatServices(services, xb, false, true, null);
        xb.closeElement("can-decode-services");
      }
      services = proxy.getCannotDecodeServices(connectors[i].getName());
      if(services != null) {
        xb.appendElement("cannot-decode-services", "count", services.length);
        if("hex".equalsIgnoreCase(format)) xmlFormatHexTokenList(services, xb);
        else xmlFormatServices(services, xb, false, true, null);
        xb.closeElement("cannot-decode-services");
      }
      xb.closeElement("connector");
    }
    xb.closeElement("export-services");
  }

  private void xmlFormatHexTokenList(TvService[] services, XmlStringBuffer xb) {
    Map perProfile = new HashMap(); Set tokens; String token;
    for(int i = 0; i < services.length; i++) {
      if(perProfile.containsKey(services[i].getProfileName())) tokens = (Set)perProfile.get(services[i].getProfileName());
      else {
        tokens = new LinkedHashSet();
        perProfile.put(services[i].getProfileName(), tokens);
      }
      token = new ServiceMapping(services[i]).toString();
      tokens.add(token);
    }
    String profile;
    for(Iterator iter = perProfile.keySet().iterator(); iter.hasNext(); ) {
      profile = (String)iter.next();
      xb.appendElement("profile", "name", profile);
      for(Iterator i = ((Set)perProfile.get(profile)).iterator(); i.hasNext(); ) {
        xb.appendText((String)i.next());
        if(i.hasNext()) xb.appendText(" ");
      }
      xb.closeElement("profile");
    } 
  }

  public void runStatusCmdLastSeen(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    String userName = (String)params.get("name");
    if(!proxy.isAdmin(user)) userName = user;
    SeenEntry[] seen = proxy.getSeenUsers((String[])params.get("profiles"), userName, false);
    xmlFormatLastSeen(seen, xb);    
  }

  public void runStatusCmdLoginFailures(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    String userName = (String)params.get("name");
    if(!proxy.isAdmin(user)) userName = user;
    SeenEntry[] seen = proxy.getSeenUsers((String[])params.get("profiles"), userName, true);
    xmlFormatLoginFailures(seen, xb);
  }  

  public void runStatusCmdFetchCfg(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    if(!webBackend.isSuperUser(user)) {
      xb.appendElement("error", "description", "Super-user required.", true);
    } else {
      try {
        xb.setContents(getCfgXml());
      } catch (Exception e) {
        xb.setContents(getError(e.toString()));
      }
    }
  }

  public void runStatusCmdCwsBouquet(XmlStringBuffer xb, Map params) throws RemoteException {
    if(!params.containsKey("xml")) return;
    Set all = getServices((String[])params.get("profiles"));
    Map serviceMap = new HashMap();
    TvService service;
    for(Iterator iter = all.iterator(); iter.hasNext();) {
      service = (TvService)iter.next();
      serviceMap.put(new Integer(service.getId()), service);
    }
    StringBuffer bqFile = new StringBuffer();
    bqFile.append("#NAME Favourites (TV)\n");
    int srvId;
    XMLConfig bqXml = (XMLConfig)params.get("xml");
    XMLConfig fileXml = bqXml.getSubConfig("file");
    boolean enigma2 = false;
    if(fileXml != null) enigma2 = "enigma2".equalsIgnoreCase(fileXml.getString("format"));
    String prefix = enigma2?"#SERVICE 1:0:":"#SERVICE: 1:0:";
    for(Enumeration en = bqXml.getMultipleSubConfigs("service"); en.hasMoreElements(); ) {
      try {
        srvId = Integer.parseInt(((XMLConfig)en.nextElement()).getString("id"));
        service = (TvService)serviceMap.get(new Integer(srvId));
        if(service != null && service.getTransponder() != -1) {                   
          bqFile.append(prefix).append(Integer.toHexString(service.getType())).append(":");
          bqFile.append(Integer.toHexString(srvId)).append(":");
          bqFile.append(Integer.toHexString((int)service.getTransponder())).append(":");
          bqFile.append(Integer.toHexString((int)service.getNetworkId())).append(":");
          bqFile.append(Long.toHexString(service.getNamespace())).append(":0:0:0:\n");
        }
      } catch (NumberFormatException e) {
        // e.printStackTrace();
      }
    }
    String id = enigma2?"favourites":Long.toString(System.currentTimeMillis(), Character.MAX_RADIX);
    String fileName = "/userbouquet." + id  + ".tv";
    String data = bqFile.toString();
    if(enigma2) data = data.toUpperCase();
    webBackend.bouquets.put(fileName, data);
    xb.appendElement("cws-bouquet", "url", fileName, true);
  }

  public void runStatusCmdSystemProperties(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    if(!webBackend.isSuperUser(user)) {
      xb.appendElement("error", "description", "Super-user required.", true);
    } else {
      Properties p = System.getProperties();
      xb.appendElement("system-properties", "count", p.size());
      String key;
      for(Enumeration e = p.propertyNames(); e.hasMoreElements(); ) {
        key = (String)e.nextElement();
        xb.appendElement("property", "name", key);
        xb.appendAttr("value", p.getProperty(key)).endElement(true);
      }
      xb.closeElement("system-properties");
    }
  }

  public void runStatusCmdSystemThreads(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    if(!webBackend.isSuperUser(user)) {
      xb.appendElement("error", "description", "Super-user required.", true);
    } else {
      Thread[] threads = new Thread[Thread.activeCount()];
      Thread.enumerate(threads);
      xb.appendElement("system-threads", "count", threads.length);
      for(int i = 0; i < threads.length; i++) {
        if(threads[i] != null) xb.appendElement("thread", "name", threads[i].toString(), true);
      }
      xb.closeElement("system-threads");
    }
  }

  public void runStatusCmdCtrlCommands(XmlStringBuffer xb, Map params) throws RemoteException {
    String cmdName = (String)params.get("name");
    String grpName = (String)params.get("group");
    xmlFormatCtrlCommands(xb, cmdName, grpName);
  }

  public void runStatusCmdStatusCommands(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    String cmdName = (String)params.get("name");
    String grpName = (String)params.get("group");
    xmlFormatStatusCommands(xb, cmdName, grpName, proxy.isAdmin(user));
  }

  void runStatusCmd(String cmd,  XmlStringBuffer xb, Map params, String user) throws RemoteException {
    StatusCommand cmdDef = (StatusCommand)statusCommands.get(cmd);
    if(cmdDef == null) xb.appendElement("error", "description", "Command unregistered", true);
    else {
      if(cmdDef.adminOnly && !proxy.isAdmin(user)) xb.appendElement("error", "description", "Admin user required.", true);
      else cmdDef.invoke(xb, params, user);
    }
  }

  String onXMLInput(XMLConfig xml, String preAuthUser, String ip) throws RemoteException {
    XmlStringBuffer xb = new XmlStringBuffer();
    String authUser = preAuthUser;

    if("cws-status-req".equals(xml.getName())) {
      xb.appendElement("cws-status-resp", "ver", "1.0");
    } else if("cws-command-req".equals(xml.getName())) {
      xb.appendElement("cws-command-resp", "ver", "1.0");
    }

    XMLConfig loginXml = xml.getSubConfig("cws-login");
    if(loginXml != null) {
      loginXml = loginXml.getSubConfig("user");
      if(loginXml != null) {
        String user = loginXml.getString("name");
        String passwd = loginXml.getString("password");
        if(webBackend.authUser(user, passwd)) {
          String sessionId = webBackend.createSession(user);
          xb.appendElement("status", "state", "loggedIn");
          xb.appendAttr("user", user).appendAttr("admin", proxy.isAdmin(user));
          xb.appendAttr("super-user", webBackend.isSuperUser(user));
          xb.appendAttr("session-id", sessionId).endElement(true);
        } else {
          xb.appendElement("status", "state", "failed", true);
          webBackend.logger.warning("User '" + user + "' (" + com.bowman.cardserv.util.CustomFormatter.formatAddress(ip) +
              ") login failure: invalid password");
          SessionManager.getInstance().fireUserLoginFailed(user, "Web/" + CaProfile.MULTIPLE.getName(), ip,
              "xml api post auth failed (bad password)");
        }
      }
    } else {
      XMLConfig sessionXml = xml.getSubConfig("session");
      if(sessionXml != null) {
        String sessionId = sessionXml.getString("session-id");
        if(sessionId != null) authUser = (String)webBackend.sessions.get(sessionId);
      }
    }

    if(authUser != null) {
      if("cws-status-req".equals(xml.getName())) {
        onXMLStatus(xml, authUser, xb);
      } else if("cws-command-req".equals(xml.getName())) {
        onXMLCtrlCmd(xml, authUser, xb);
      } else throw new IllegalArgumentException("Malformed request. Expected root element cws-status-req or cws-command-req.");
    } else if(loginXml == null) xb.appendElement("error", "description", "Not logged in.", true);

    if("cws-status-req".equals(xml.getName())) {
      xb.closeElement("cws-status-resp");
    } else if("cws-command-req".equals(xml.getName())) {
      xb.closeElement("cws-command-resp");
    }

    return xb.toString();
  }

  private void onXMLCtrlCmd(XMLConfig xml, String preAuthUser, XmlStringBuffer reply) throws RemoteException {
    if(!webBackend.isSuperUser(preAuthUser))
      reply.appendElement("error", "description", "Super-user required.", true);
    else {
      XMLConfig cmdXml = xml.getSubConfig("command");
      if(cmdXml == null) throw new IllegalArgumentException("Malformed request. No command element in cws-command-req.");
      else {
        String cmd = cmdXml.getString("command");
        if(cmd == null) xml.getString("command");
        if(cmd == null || !ctrlCommands.containsKey(cmd.toLowerCase()))
          throw new IllegalArgumentException("Malformed request. Unknown command: " + cmd);

        appendCmdResult(cmd, runCtrlCmd(cmd, cmdXml.flatten(true), preAuthUser), reply);
      }
    }
  }

  private void onXMLStatus(XMLConfig xml, String authUser, XmlStringBuffer xb) throws RemoteException {
    String profile = xml.getString("profile");
    ProfileStatus[] ps = proxy.getUserProfiles(authUser);
    String[] profiles = getProfileNames(ps, profile);

    XMLConfig statusCmd;
    for(Enumeration e = xml.getAllSubConfigs(); e.hasMoreElements(); ) {
      statusCmd = (XMLConfig)e.nextElement();
      if("true".equalsIgnoreCase(statusCmd.getString("include"))) {
        Map params = statusCmd.flatten(true);
        params.put("ps", ps);
        params.put("profiles", profiles);
        params.put("xml", statusCmd);
        runStatusCmd(statusCmd.getName(), xb, params, authUser);
      }
    }
  }

  private void xmlFormatProfiles(ProfileStatus[] profiles, XmlStringBuffer xb, boolean admin) throws RemoteException {
    if(profiles != null) {
      xb.appendElement("ca-profiles");
      int mappedServices, capacity;
      for(int i = 0; i < profiles.length; i++) {
        mappedServices = getServices(new String[] {profiles[i].getName()}).size();
        capacity = proxy.getCwsCapacity(new String[] {profiles[i].getName()});

        xb.appendElement("profile");
        xb.appendAttr("name", profiles[i].getName());
        xb.appendAttr("enabled", profiles[i].isEnabled());
        if(!CaProfile.MULTIPLE.getName().equals(profiles[i].getName())) {
          xb.appendAttr("cache-only", profiles[i].isCacheOnly());
          xb.appendAttr("ca-id", profiles[i].getCaId());
          xb.appendAttr("network-id", profiles[i].getNetworkId());
          if(profiles[i].getProviderIdents() != null)
            xb.appendAttr("provider-idents", profiles[i].getProviderIdents());
          xb.appendAttr("mismatched-cards", profiles[i].isMismatchedCards());
          xb.appendAttr("provider-match", profiles[i].isRequiresProviderMatch());
          xb.appendAttr("parsed-services", profiles[i].getServices());
          xb.appendAttr("parsed-conflicts", profiles[i].getConflicts());
          if(profiles[i].getResetStr() != null)
            xb.appendAttr("reset-services", profiles[i].getResetStr());
          if(profiles[i].getBlockedStr() != null)
            xb.appendAttr("blocked-services", profiles[i].getBlockedStr());
          if(profiles[i].getAllowedStr() != null)
            xb.appendAttr("allowed-services", profiles[i].getAllowedStr());
        }
        xb.appendAttr("mapped-services", mappedServices);
        xb.appendAttr("debug", profiles[i].isDebug());
        xb.appendAttr("capacity", capacity);
        xb.appendAttr("sessions", profiles[i].getSessions());
        xb.appendAttr("max-cw-wait", profiles[i].getMaxCwWait());
        xb.appendAttr("max-cache-wait", profiles[i].getMaxCacheWait());
        if(profiles[i].getCongestionLimit() != profiles[i].getMaxCwWait())
          xb.appendAttr("congestion-limit", profiles[i].getCongestionLimit());

        xb.endElement(false);

        PortStatus[] ps = profiles[i].getListenPorts();
        for(int n = 0; n < ps.length; n++) {
          if(ps[n].getPort() > 0) {
            xb.appendElement("listen-port", "name", ps[n].getLabel());
            xb.appendAttr("protocol", ps[n].getProtocol());
            xb.appendAttr("port-number", ps[n].getPort());
            xb.appendAttr("alive", ps[n].isAlive());
            if(!"".equals(ps[n].getProperties()) && admin) xb.appendAttr("properties", ps[n].getProperties());
            xb.endElement(true);
          }
        }

        xb.closeElement("profile");
      }
      xb.closeElement("ca-profiles");
    }
  }

  public static void xmlFormatConnectors(CwsStatus[] connectors, XmlStringBuffer xb, boolean admin, Map cwsLog,
                                         RemoteProxy proxy, String[] profiles) throws RemoteException
  {
    if(connectors != null) {

      xb.appendElement("cws-connectors", "count", connectors.length);

      CwsStatus conn;
      for(int i = 0; i < connectors.length; i++) {
        conn = connectors[i];
        xb.appendElement("connector");
        xb.appendAttr("name", conn.getName());
        xb.appendAttr("protocol", conn.getProtocol());
        xb.appendAttr("profile", conn.getProfileName());
        xb.appendAttr("metric", conn.getMetric());
        xb.appendAttr("status", CWS_STATES[conn.getStatus()]);

        if(conn.getStatus() == CwsStatus.CWS_CONNECTED || conn.getStatus() == CwsStatus.CWS_UNRESPONSIVE) {
          TvService[] services = proxy.getServices(conn.getName(), true);
          if(admin) xb.appendAttr("host", conn.getRemoteHost());
          if(!CaProfile.MULTIPLE.getName().equals(conn.getProfileName()))
            xb.appendAttr("provider-idents", conn.getProviderIdents());
          xb.appendAttr("connected", formatTimeStamp(conn.getConnectTimeStamp()));
          xb.appendAttr("duration", formatDurationFrom(conn.getConnectTimeStamp()));
          xb.appendAttr("service-count", services.length);
          xb.appendAttr("sendq", conn.getSendQ());
          xb.appendAttr("utilization", conn.getUtilization());
          xb.appendAttr("avgutilization", conn.getAvgUtilization());
          xb.appendAttr("ecm-count", conn.getEcmCount());
          xb.appendAttr("ecm-load", conn.getEcmLoad());
          xb.appendAttr("emm-count", conn.getEmmCount());
          xb.appendAttr("timeout-count", conn.getTimeoutCount());
          xb.appendAttr("capacity", conn.getCapacity());
          xb.appendAttr("cutime", conn.getCurrentEcmTime());
          xb.appendAttr("avgtime", conn.getAverageEcmTime());
          if(admin && conn.getCardData1() != null) xb.appendAttr("card-data1", conn.getCardData1());
          if(admin && conn.getCardData2() != null) xb.appendAttr("card-data2", conn.getCardData2());
          if(cwsLog != null && cwsLog.containsKey(conn.getName())) {
            xb.appendAttr("cws-log", ((List)cwsLog.get(conn.getName())).size());
          }
          xb.endElement(false);

          boolean includeProfile = CaProfile.MULTIPLE.getName().equals(conn.getProfileName());
          xmlFormatServices(services, xb, false, includeProfile, false, conn.getRecentSids(), profiles);
          if(admin) xmlFormatRemoteInfo(conn, xb);

          xb.closeElement("connector");
        } else if(conn.getStatus() == CwsStatus.CWS_DISCONNECTED) {
          long secsLeft = (conn.getNextAttemptTimeStamp() - System.currentTimeMillis()) / 1000;
          String nextAttempt = formatDuration(secsLeft);
          if(nextAttempt.length() > 0) xb.appendAttr("next-attempt", nextAttempt);
          if(conn.getDisconnectTimeStamp() > 0)
            xb.appendAttr("disconnected", formatTimeStamp(conn.getDisconnectTimeStamp()));
          xb.endElement(true);
        } else xb.endElement(true);
      }

      xb.closeElement("cws-connectors");
    }
  }

  public static void xmlFormatRemoteInfo(CwsStatus connector, XmlStringBuffer xb) {
    Properties p = connector.getRemoteInfo();
    if(p == null || p.isEmpty()) return;
    xb.appendElement("remote-info", "count", p.size());
    List sorted = new ArrayList(p.keySet());
    Collections.sort(sorted);
    String key;
    for(Iterator iter = sorted.iterator(); iter.hasNext(); ) {
      key = (String)iter.next();
      xb.appendElement("cws-param", "name", key);
      xb.appendAttr("value", p.getProperty(key)).endElement(true);
    }
    xb.closeElement("remote-info");
  }

  public static void xmlFormatUsers(UserStatus[] users, int loginFailures, XmlStringBuffer xb) {
    if(users == null) users = new UserStatus[0];

    xb.appendElement("proxy-users", "count", users.length);
    xb.appendAttr("login-failures", loginFailures).endElement(false);
    for(int i = 0; i < users.length; i++) {
      xb.appendElement("user", "name", users[i].getUserName());
      xb.appendAttr("display-name", users[i].getDisplayName());
      xb.appendAttr("start-date", users[i].getStartDate());
      xb.appendAttr("expire-date", users[i].getExpireDate());
      if(users[i].isAdmin()) xb.appendAttr("admin", users[i].isAdmin());

      xb.appendAttr("sessions", users[i].getSessionCount(null));
      // xb.appendAttr("max-sessions", users[i].getMaxSessions());

      String name;
      for(Iterator iter = users[i].getPropertyNames(); iter.hasNext(); ) {
        name = (String)iter.next();
        xb.appendAttr(name, users[i].getProperty(name));
      }
      xb.endElement(false);

      SessionStatus[] sessions = users[i].getSessions();
      SessionStatus ss;
      for(int n = 0; n < sessions.length; n++) {
        ss = sessions[n];
        xb.appendElement("session", "host", ss.getRemoteHost());
        xb.appendAttr("id", ss.getSessionId());
        xb.appendAttr("count", users[i].getSessionCount(ss.getProfileName()) + "/" + ss.getMaxSessions());
        xb.appendAttr("active", ss.isActive());
        xb.appendAttr("profile", ss.getProfileName());
        xb.appendAttr("client-id", ss.getClientId());
        xb.appendAttr("protocol", ss.getProtocol());
        xb.appendAttr("context", ss.getContext());
        // hack to indicate au
        int idx = ss.getContext().indexOf("No (");
        if(idx != -1) xb.appendAttr("au", ss.getContext().substring(idx + 4, ss.getContext().lastIndexOf(")")));
        xb.appendAttr("connected", formatTimeStamp(ss.getConnectTimeStamp()));
        xb.appendAttr("duration", formatDurationFrom(ss.getConnectTimeStamp()));
        xb.appendAttr("ecm-count", ss.getEcmCount());
        xb.appendAttr("emm-count", ss.getEmmCount());
        xb.appendAttr("pending-count", ss.getPendingCount());
        if(ss.getKaCount() > 0) xb.appendAttr("keepalive-count", ss.getKaCount());
        xb.appendAttr("last-transaction", ss.getLastTransactionTime());
        if(ss.getLastZapTimeStamp() > 0) xb.appendAttr("last-zap", formatDurationFrom(ss.getLastZapTimeStamp()));
        if(ss.getIdleTime() > -1) xb.appendAttr("idle-time", formatDuration(ss.getIdleTime() / 1000));

        xb.appendAttr("flags", ss.getFlags());
        xb.appendAttr("avg-ecm-interval", ss.getAvgEcmInterval());
        xb.endElement(false);
        xmlFormatServices(new TvService[] {ss.getLastService()}, xb, false, CaProfile.MULTIPLE.getName().equals(ss.getProfileName()), null);
        xb.closeElement("session");
      }
      xb.closeElement("user");
    }
    xb.closeElement("proxy-users");
  }

  private void xmlFormatLastSeen(SeenEntry[] seen, XmlStringBuffer xb) {
    xb.appendElement("last-seen", "count", seen.length);
    for(int i = 0; i < seen.length; i++) {
      xb.appendElement("entry", "name", seen[i].getName());
      xb.appendAttr("profile", seen[i].getProfile());
      xb.appendAttr("last-login", formatTimeStamp(seen[i].getLastLogin()));
      xb.appendAttr("last-seen", formatTimeStamp(seen[i].getLastSeen()));
      xb.appendAttr("host", seen[i].getHostAddr());
      List log = (List)webBackend.userTransactions.get(seen[i].getName());
      if(log == null) log = new ArrayList();
      xb.appendAttr("user-log", log.size());
      /*
      xb.appendAttr("total-time", formatDuration(seen[i].getTotalTime()));
      xb.appendAttr("total-ecm-count", seen[i].getTotalEcmCount());
      */
      xb.endElement(true);
    }
    xb.closeElement("last-seen");
  }

  private void xmlFormatLoginFailures(SeenEntry[] seen, XmlStringBuffer xb) {
    xb.appendElement("login-failures", "count", seen.length);
    for(int i = 0; i < seen.length; i++) {
      xb.appendElement("entry", "name", seen[i].getName());
      xb.appendAttr("context", seen[i].getProfile());
      xb.appendAttr("last-failure", formatTimeStamp(seen[i].getLastLogin()));
      xb.appendAttr("first-failure", formatTimeStamp(seen[i].getLastLogout()));
      xb.appendAttr("failure-count", seen[i].getCount());
      xb.appendAttr("host", seen[i].getHostAddr());
      xb.appendAttr("reason", seen[i].getLastReason());
      xb.endElement(true);
    }
    xb.closeElement("login-failures");
  }

  public static void xmlFormatServices(TvService[] services, XmlStringBuffer xb, boolean includeCount, boolean includeProfile,
                                 int[] recentSids)
  {
    xmlFormatServices(services, xb, includeCount, includeProfile, true, recentSids, null);
  }

  public static void xmlFormatServices(TvService[] services, XmlStringBuffer xb, boolean includeCount, boolean includeProfile,
                                       boolean includeCid, int[] recentSids, String[] profileNames)
  {
    if(services != null) {
      Set sids = null;
      if(recentSids != null && recentSids.length > 0) {
        sids = new HashSet();
        for(int i = 0; i < recentSids.length; i++) sids.add(new Integer(recentSids[i]));
      }
      Set profiles = new HashSet();
      if(profileNames != null) profiles.addAll(Arrays.asList(profileNames));
      for(int n = 0; n < services.length; n++) {
        if(services[n] == null || services[n].getId() == -1) continue;
        if(!profiles.isEmpty())
          if(!profiles.contains(services[n].getProfileName())) continue;
        xb.appendElement("service", "id", services[n].getId());
        if(includeCid) xb.appendAttr("cdata", new ServiceMapping(services[n]).toString());
        xb.appendAttr("name", services[n].getDisplayName());
        if(includeCount) xb.appendAttr("watchers", services[n].getWatchers());
        if(includeProfile) xb.appendAttr("profile", services[n].getProfileName());
        if(sids != null && sids.contains(new Integer(services[n].getId()))) xb.appendAttr("hit", "true");
        xb.endElement(true);
      }
    }
  }

  private void xmlFormatProxyPlugins(XmlStringBuffer xb, String name, boolean admin) throws RemoteException {
    PluginStatus[] plugins = admin?proxy.getPlugins():new PluginStatus[0];
    xb.appendElement("proxy-plugins", "count", plugins.length);
    for(int i = 0; i < plugins.length; i++) {
      if(name == null || name.equals(plugins[i].getName())) {
        xb.appendElement("plugin", "name", plugins[i].getName());
        xb.appendAttr("description", plugins[i].getDescription());
        xb.appendAttr("class-name", plugins[i].getClassName());
        xb.endElement(false);

        String key;
        for(Iterator iter = plugins[i].getPropertyNames(); iter.hasNext(); ) {
          key = (String)iter.next();
          xb.appendElement("plugin-param", "name", key);
          xb.appendAttr("value", plugins[i].getProperty(key)).endElement(true);
        }
        xb.closeElement("plugin");
      }
    }
    xb.closeElement("proxy-plugins");
  }

  private void xmlFormatProxyStatus(XmlStringBuffer xb, String[] profiles) throws RemoteException {
    int sessions = proxy.getSessionCount(profiles, false);
    int connectors = proxy.getCwsCount(profiles);
    long startTime = proxy.getProxyStartTime();
    if(sessions == -1 && connectors == -1 && startTime == -1) {
      xb.appendElement("proxy-status", "state", "down", true);
    } else {
      int[] counters = proxy.getCounters();

      xb.appendElement("proxy-status", "state", "up");
      xb.appendAttr("name", proxy.getName());
      xb.appendAttr("version", CardServProxy.APP_VERSION);
      xb.appendAttr("build", CardServProxy.APP_BUILD);
      xb.appendAttr("ecm-count", counters[RemoteListener.C_ECMCOUNT]);
      xb.appendAttr("ecm-forwards", counters[RemoteListener.C_ECMFORWARDS]);
      xb.appendAttr("ecm-cache-hits", counters[RemoteListener.C_ECMCACHEHITS]);
      xb.appendAttr("ecm-failures", counters[RemoteListener.C_ECMFAILURES]);
      xb.appendAttr("ecm-denied", counters[RemoteListener.C_ECMDENIED]);
      xb.appendAttr("ecm-filtered", counters[RemoteListener.C_ECMFILTERED]);
      xb.appendAttr("emm-count", counters[RemoteListener.C_EMMCOUNT]);
      xb.appendAttr("ecm-rate", counters[RemoteListener.C_ECMRATE]);
      xb.appendAttr("probeq", counters[RemoteListener.C_PROBEQ]);
      xb.appendAttr("started", formatTimeStamp(startTime));
      xb.appendAttr("duration", formatDurationFrom(startTime));
      xb.appendAttr("connectors", connectors);
      xb.appendAttr("capacity", proxy.getCwsCapacity(profiles));
      xb.appendAttr("active-sessions", proxy.getSessionCount(profiles, true));
      xb.appendAttr("sessions", sessions);
      xb.endElement(false);

      String os = System.getProperty("os.name", "undetermined") + " " + System.getProperty("os.version", "")
          + " (" + System.getProperty("os.arch", "unknown") + ")";
      Runtime rt = Runtime.getRuntime();

      xb.appendElement("jvm", "name", System.getProperty("java.vm.name", "Unknown"));
      xb.appendAttr("version", System.getProperty("java.runtime.version", "0.0.0"));
      xb.appendAttr("heap-total", rt.totalMemory() / 1024);
      xb.appendAttr("heap-free", rt.freeMemory() / 1024);
      xb.appendAttr("threads", Thread.activeCount());

      if(checkFileDescriptors) {
        try { // java6+ unix specific jmx info
          long openFd = UnixUtil.getOpenFileDescriptorCount();
          long maxFd = UnixUtil.getMaxFileDescriptorCount();
          if(openFd > 0) xb.appendAttr("filedesc-open", openFd);
          if(maxFd > 0) xb.appendAttr("filedesc-max", maxFd);
        } catch(Throwable e) {
          webBackend.logger.fine("No unix management instrumentation available: " + e);
          checkFileDescriptors = false;
        }
      }
      xb.appendAttr("time", formatTimeStamp(System.currentTimeMillis()));
      xb.appendAttr("os", os).endElement(true);

      xb.closeElement("proxy-status");
    }
  }

  public static void xmlFormatCacheStatus(CacheStatus cs, XmlStringBuffer xb) {
    xb.appendElement("cache-status", "type", cs.getType());
    Properties p = cs.getUsageStats();
    if(p == null) p = new Properties();
    List sorted = new ArrayList(p.keySet());
    Collections.sort(sorted);
    String key;
    for(Iterator iter = sorted.iterator(); iter.hasNext(); ) {
      key = (String)iter.next();
      xb.appendElement("cache-param", "name", key);
      xb.appendAttr("value", p.getProperty(key)).endElement(true);
    }
    xb.closeElement("cache-status");
  }

  private void xmlFormatErrorLog(XmlStringBuffer xb, String[] profiles, boolean admin) {
    RemoteEvent event;
    Set ps = profiles == null ? null : new HashSet(Arrays.asList(profiles));
    xb.appendElement("error-log", "size", webBackend.eventLog.size());

    for(Iterator iter = new ArrayList(webBackend.eventLog).iterator(); iter.hasNext(); ) {
      event = (RemoteEvent)iter.next();
      if(ps == null || event.getProfile() == null || ps.contains(event.getProfile())) {
        xb.appendElement("event");
        xb.appendAttr("timestamp", formatTimeStamp(event.getTimeStamp()));
        xb.appendAttr("type", event.getType());
        xb.appendAttr("profile", event.getProfile());
        xb.appendAttr("label", event.getLabel());
        xb.appendAttr("msg", admin?event.getMessage():"");
        xb.endElement(true);
      }
    }
    xb.closeElement("error-log");
  }

  private void xmlFormatFileLog(XmlStringBuffer xb) {
    RemoteEvent event;
    xb.appendElement("file-log", "size", webBackend.fileLog.size());

    for(Iterator iter = new ArrayList(webBackend.fileLog).iterator(); iter.hasNext(); ) {
      event = (RemoteEvent)iter.next();
      xb.appendElement("event");
      xb.appendAttr("timestamp", formatTimeStamp(event.getTimeStamp()));
      xb.appendAttr("log-level", event.getProperty("log-level"));    
      xb.appendAttr("label", event.getLabel());
      xb.appendAttr("msg", event.getMessage());
      xb.endElement(true);
    }
    xb.closeElement("file-log");
  }

  private void xmlFormatUserLog(XmlStringBuffer xb, String userName, String[] profiles) {
    List ecmLog = (List)webBackend.userTransactions.get(userName);
    if(ecmLog == null) ecmLog = new ArrayList();
    xb.appendElement("user-log", "size", ecmLog.size()).appendAttr("name", userName).endElement(false);
    xmlFormatEcmTransactions(xb, ecmLog, profiles, null, false, false);
    xb.closeElement("user-log");
  }

  private void xmlFormatUserWarningLog(XmlStringBuffer xb, String userName, String[] profiles) {
    xb.appendElement("user-warning-log");
    if(userName != null) xb.appendAttr("name", userName).endElement(false);
    xmlFormatEcmTransactions(xb, webBackend.warningLog, profiles, userName, true, false);
    xb.closeElement("user-warning-log");
  }

  private void xmlFormatCwsLog(XmlStringBuffer xb, String cwsName) {
    List ecmLog = (List)webBackend.cwsTransactions.get(cwsName);
    if(ecmLog == null) ecmLog = new ArrayList();
    xb.appendElement("cws-log", "size", ecmLog.size()).appendAttr("name", cwsName).endElement(false);
    xmlFormatEcmTransactions(xb, ecmLog, null, null, false, true);
    xb.closeElement("cws-log");
  }

  public static void xmlFormatEcmTransactions(XmlStringBuffer xb, Collection ecmLog, String[] profiles, String userName,
      boolean warningsOnly, boolean cwsLog)
  {
    Set ps = profiles == null ? null : new HashSet(Arrays.asList(profiles));
    RemoteEvent event;
    for(Iterator iter = new ArrayList(ecmLog).iterator(); iter.hasNext(); ) {
      event = (RemoteEvent)iter.next();
      if(warningsOnly && !"true".equalsIgnoreCase(event.getProperty("warning"))) continue; // filter out non-warnings
      if(warningsOnly && userName != null && !userName.equals(event.getMessage())) continue; // filter out other users
      if(ps == null || event.getProfile() == null || ps.contains(event.getProfile())) {
        xb.appendElement("ecm");
        try {
          if(warningsOnly) {
            xb.appendAttr("name", event.getMessage());
            if(event.getProperty("time-cache") != null) xb.appendAttr("time-cache", event.getProperty("time-cache"));
            if(event.getProperty("time-queue") != null) xb.appendAttr("time-queue", event.getProperty("time-queue"));
            if(event.getProperty("time-cws") != null) xb.appendAttr("time-cws", event.getProperty("time-cws"));
            if(event.getProperty("time-client") != null) xb.appendAttr("time-client", event.getProperty("time-client"));
          } else {
            if("true".equalsIgnoreCase(event.getProperty("warning"))) xb.appendAttr("warning", "true");
          }
          if(event.getProperty("count") != null) xb.appendAttr("count", event.getProperty("count"));

          xb.appendAttr("timestamp", formatTimeStamp(Long.parseLong(event.getProperty("timestamp"))));
          xb.appendAttr("request-hash", event.getProperty("request-hash"));
          xb.appendAttr("ecm-size", event.getProperty("ecm-size"));
          if(event.getProperty("cw") != null) xb.appendAttr("cw", event.getProperty("cw"));
          if(event.getProperty("ext-newcamd") != null) xb.appendAttr("ext-newcamd", event.getProperty("ext-newcamd"));
          xb.appendAttr("session-id", event.getProperty("id"));
          xb.appendAttr("service-name", event.getProperty("service"));
          if(event.getProperty("provider-ident") != null) xb.appendAttr("provider-ident", event.getProperty("provider-ident"));
          if(event.getProperty("ca-id") != null) xb.appendAttr("ca-id", event.getProperty("ca-id"));
          if(event.getProperty("network-id") != null) xb.appendAttr("network-id", event.getProperty("network-id"));
          if(event.getProperty("origin-id") != null) xb.appendAttr("origin-id", event.getProperty("origin-id"));
          if(event.getProperty("reply-sid") != null) xb.appendAttr("reply-sid", event.getProperty("reply-sid"));
          xb.appendAttr("time", event.getProperty("time"));
          xb.appendAttr("flags", event.getProperty("flags"));
          if(event.getProperty("filtered-by") != null) xb.appendAttr("filtered-by", event.getProperty("filtered-by"));

          if(cwsLog) xb.appendAttr("user-name", event.getMessage());
          else if(event.getProperty("cws-name") != null) xb.appendAttr("cws-name", event.getProperty("cws-name"));

          xb.endElement(true);
        } catch (Exception e) {
          e.printStackTrace();
          xb.endElement(true);
        }
      }
    }
  }

  private void xmlFormatCtrlCommands(XmlStringBuffer xb, String cmdName, String grpName) throws RemoteException {
    xb.appendElement("ctrl-commands", "count", ctrlCommands.size());
    xmlFormatCommands(xb, cmdName, grpName, ctrlCommands, true);
    xb.closeElement("ctrl-commands");
  }

  private void xmlFormatStatusCommands(XmlStringBuffer xb, String cmdName, String grpName, boolean admin) throws RemoteException {
    xb.appendElement("status-commands", "count", statusCommands.size());
    xmlFormatCommands(xb, cmdName, grpName, statusCommands, admin);
    xb.closeElement("status-commands");
  }

  private void xmlFormatCommands(XmlStringBuffer xb, String cmdName, String grpName, Map commands, boolean admin) throws RemoteException {
    Map optionLists = new HashMap(); // merge named lists of options that may be used by multiple commands

    // group by handler
    Map groups = new LinkedHashMap();
    Command cmd;
    for(Iterator iter = commands.values().iterator(); iter.hasNext(); ) {
      cmd = (Command)iter.next();
      if(!groups.containsKey(cmd.groupLabel)) groups.put(cmd.groupLabel, cmd.handler);
    }
    String group;
    for(Iterator iter = groups.keySet().iterator(); iter.hasNext(); ) {
      group = (String)iter.next();
      if(grpName == null || grpName.equals(group)) {
        xb.appendElement("command-group", "name", group);
        xb.appendAttr("handler", groups.get(group).getClass().getName()).endElement(false);
        xmlFormatCommandGroup(xb, group, cmdName, commands, optionLists, admin);
        xb.closeElement("command-group");
      }
    }

    xmlFormatOptionLists(xb, optionLists);
  }

  public static void xmlFormatOptionLists(XmlStringBuffer xb, Map optionLists) {
    String name;
    for(Iterator iter = optionLists.keySet().iterator(); iter.hasNext(); ) {
      name = (String)iter.next();
      xb.appendElement("option-list", "name", name);
      for(Iterator i = ((Set)optionLists.get(name)).iterator(); i.hasNext(); ) {
        xb.appendElement("option", "value", i.next().toString(), true);
      }
      xb.closeElement("option-list");
    }
  }

  private void xmlFormatCommandGroup(XmlStringBuffer xb, String groupName, String cmdName, Map commands, Map optionLists, boolean admin)
      throws RemoteException
  {
    Command cmd; Command.CommandParam prm;
    for(Iterator iter = commands.values().iterator(); iter.hasNext(); ) {
      cmd = (Command)iter.next();
      if(cmdName != null && !cmd.name.equals(cmdName)) continue;
      if(groupName != null && !groupName.equals(cmd.groupLabel)) continue;
      if(cmd instanceof StatusCommand && ((StatusCommand)cmd).adminOnly && !admin) continue;
      xb.appendElement("command", "name", cmd.name);
      xb.appendAttr("label", cmd.label);
      xb.appendAttr("description", cmd.description);
      if(cmd instanceof CtrlCommand) xb.appendAttr("confirm", ((CtrlCommand)cmd).confirm);
      if(cmd instanceof StatusCommand) xb.appendAttr("admin-only", ((StatusCommand)cmd).adminOnly);
      xb.endElement(false);
      for(Iterator prms = cmd.params.values().iterator(); prms.hasNext(); ) {
        prm = (CtrlCommand.CommandParam)prms.next();
        if(!admin && prm.adminOnly) continue;
        xb.appendElement("command-param", "name", prm.name);
        xb.appendAttr("label", prm.label);
        if(prm.value != null) xb.appendAttr("value", prm.value);
        if(prm.size > 0) xb.appendAttr("size", prm.size);
        if(cmd instanceof CtrlCommand) {
          xb.appendAttr("allow-arbitrary", prm.allowArbitrary);
          if(!prm.allowArbitrary && prm.options.contains("true")) xb.appendAttr("boolean", true);
        }
        if(cmd instanceof StatusCommand) {
          xb.appendAttr("optional", prm.optional);
          xb.appendAttr("admin-only", prm.adminOnly);
        }
        xb.endElement(false);
        String[] options = prm.getOptions();
        if(options != null)
          for(int i = 0; i < options.length; i++) {
            if(options[i].startsWith("@")) {
              if(!optionLists.containsKey(options[i]))
                optionLists.put(options[i], getParamOptions(options[i].substring(1)));
            }
            xb.appendElement("option", "value", options[i], true);
          }
        xb.closeElement("command-param");
      }
      xb.closeElement("command");
    }
  }

  private Set getParamOptions(String source) throws RemoteException {
    Set set = new TreeSet();
    if("connected-users".equals(source)) {
      UserStatus[] users = proxy.getUsersStatus(null, false);
      for(int i = 0; i < users.length; i++) set.add(users[i].getUserName());
    } else if("active-users".equals(source)) {
      UserStatus[] users = proxy.getUsersStatus(null, true);
      for(int i = 0; i < users.length; i++) set.add(users[i].getUserName());
    } else if("known-users".equals(source)) {
      UserStatus[] users = proxy.getUsersStatus(null, false);
      for(int i = 0; i < users.length; i++) set.add(users[i].getUserName());
      SeenEntry[] seen = proxy.getSeenUsers(null, null, false);
      for(int i = 0; i < seen.length; i++) set.add(seen[i].getName());
    } else if("offline-users".equals(source)) {
      SeenEntry[] seen = proxy.getSeenUsers(null, null, false);
      for(int i = 0; i < seen.length; i++) set.add(seen[i].getName());
    } else if("profiles".equals(source)) {
      ProfileStatus[] profiles = proxy.getProfiles();
      for(int i = 0; i < profiles.length; i++)
        if(!CaProfile.MULTIPLE.getName().equals(profiles[i].getName())) set.add(profiles[i].getName());
    } else if("connectors".equals(source)) {
      CwsStatus[] connectors = proxy.getMultiCwsStatus(null);
      for(int i = 0; i < connectors.length; i++) set.add(connectors[i].getName());
    }
    return set;
  }

  public static String formatTimeStamp(long timeStamp) {
    synchronized(rfc822fmt) { // incredible - SimpleDateFormat isn't threadsafe
      return rfc822fmt.format(new Date(timeStamp));
    }
  }

  public static String formatDuration(long s) {
    if(s == 0) return "0s";
    int i = 0;
    String res = "";
    while(s > 0) {
      switch(i++) {
        case 0:
          res += s % 60 + "s";
          s = s / 60;
        case 1:
          if(s % 60 > 0) res = s % 60 + "m " + res;
          s = s / 60;
        case 2:
          if(s % 24 > 0) res = s % 24 + "h " + res;
          s = s / 24;
        case 3:
          if(s % 7 > 0) res = s % 7 + "d " + res;
          s = s / 7;
        case 4:
          if(s > 0) res = s + "w " + res;
          s = 0;
      }
    }
    return res;
  }

  public static String formatDurationFrom(long timeStamp) {
    long s = (System.currentTimeMillis() - timeStamp) / 1000;
    return formatDuration(s);
  }

  public static String[] getProfileNames(ProfileStatus[] profiles, String selected) {
    String[] names = new String[profiles.length];
    for(int i = 0; i < profiles.length; i++) names[i] = profiles[i].getName();
    Set profileSet = new HashSet(Arrays.asList(names));
    if(selected != null) {
      if(profileSet.contains(selected)) names = new String[] {selected};
      else names = new String[0]; // selected unknown or not allowed profile
    }
    return names;
  }  

  public static String getError(String descr) {
    return new XmlStringBuffer().appendElement("error", "description", descr, true).toString();
  }

  public static String getCfgResult(String message) {
    return new XmlStringBuffer().appendElement("cfg-result", "message", message, true).toString();
  }

  private void addCommand(Map commands, Command command, boolean override) {
    Command old;
    if(commands.containsKey(command.name) && override) {
      old = (Command)commands.get(command.name);
      old.setOverride(command);
    } else {
      commands.put(command.name, command);
    }
  }

  private void removeCommand(Map commands, Command command) {
    Command cmd = (Command)commands.get(command.name);
    if(cmd == command) commands.remove(command.name);
    else cmd.setOverride(null);
  }

  public void registerCommand(Command command) {
    registerCommand(command, false);
  }

  public void registerCommand(Command command, boolean override) {
    if(command instanceof CtrlCommand) addCommand(ctrlCommands, command, override);
    if(command instanceof StatusCommand) addCommand(statusCommands, command, override);
  }

  public void unregisterCommand(Command command) {
    if(command instanceof CtrlCommand) removeCommand(ctrlCommands, command);
    if(command instanceof StatusCommand) removeCommand(statusCommands, command);
  }
}
