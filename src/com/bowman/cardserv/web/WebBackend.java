package com.bowman.cardserv.web;

import com.bowman.cardserv.*;
import com.bowman.cardserv.session.*;
import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.rmi.*;
import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.util.*;
import com.bowman.httpd.*;
import com.bowman.xml.*;
import com.bowman.util.*;

import java.io.*;
import java.net.*;
import java.rmi.RemoteException;
import java.security.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2006-mar-05
 * Time: 17:53:49
 */
public class WebBackend implements HttpRequestListener, RemoteListener, XmlConfigurable {

  private static final int MAX_EVENTS = 40, MAX_ECMS = 100;
  private static final String PLUGIN_SCRIPT = "load.js";

  private static final String xmlHandlerPattern = "/xmlHandler*";
  private static final String cfgHandlerPattern = "/cfgHandler*";
  private static final String bouquetPattern = "/userbouquet.*.tv";
  private static final String piconPattern = "/picon/*";
  private static final String pluginPattern = "/plugin/*";
  private static final String ghttpPattern = GHttpBackend.API_PREFIX + "/*";
  private static final String connectPattern = "/cspHandler";

  protected static final String anonPrefix = "anon:";

  Map sessions = new HashMap(), bouquets = new HashMap();
  Map userTransactions = new HashMap(), cwsTransactions = new HashMap();
  Set superUsers = new HashSet();
  List eventLog = Collections.synchronizedList(new ArrayList());
  List warningLog = Collections.synchronizedList(new ArrayList());
  List fileLog = Collections.synchronizedList(new ArrayList());

  private Set connecting = new HashSet(), invalid = new HashSet();

  private XmlHelper helper;
  private GHttpBackend ghttpd;
  protected PseudoHttpd httpd;
  private ProxyLogger httpdLogger;
  private boolean debugXml = false, allowCspConnect = true, ignoreCacheRequests = false;

  RemoteProxy proxy;
  ProxyLogger logger;

  public WebBackend(RemoteHandler proxy) {
    this.proxy = proxy;
    try {
      this.proxy.addRemoteListener(this);
    } catch (RemoteException e) {
      e.printStackTrace();
    }
    this.logger = ProxyLogger.getLabeledLogger(getClass().getName());
    this.helper = new XmlHelper(this);

    try {
      new CtrlCommand("debug-xml", "Toggle xml debugging", "Write all incoming and outgoing xml to sysout.").register(this);
    } catch(NoSuchMethodException e) {
      e.printStackTrace();
    }
  }

  public CtrlCommandResult runCtrlCmdDebugXml() {
    debugXml = !debugXml;
    return new CtrlCommandResult(true, "Debugging is now " + (debugXml?"on.":"off."));
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {

    boolean useSsl = false;
    String keyStorePath = null, keyStorePasswd = null;

    ProxyXmlConfig sslXml = null;
    try {
      sslXml = xml.getSubConfig("ssl");
    } catch(ConfigException e) {}
    if(sslXml != null && "true".equalsIgnoreCase(sslXml.getStringValue("enabled"))) {
      keyStorePath = sslXml.getFileValue("keystore", false);
      keyStorePasswd = sslXml.getSubConfig("keystore").getStringValue("password");
      useSsl = true;
    }

    InetAddress bindAddr = null; String bindIp = null;
    try {
      bindIp = xml.getStringValue("bind-ip");
      bindAddr = InetAddress.getByName(bindIp);
    } catch(ConfigException e) {
    } catch(UnknownHostException e) {
      throw new ConfigException(xml.getFullName(), "bind-ip", "Invalid status-web bind-ip: " + bindIp);
    }

    boolean autoStart = false;
    int listenPort = xml.getPortValue("listen-port");
    try {
      if(httpd != null) {
        if(httpd.getListenPort() != listenPort) {
          httpd.stop();
          httpd = useSsl?new SecurePseudoHttpd(listenPort, bindAddr, keyStorePath, keyStorePasswd):new PseudoHttpd(listenPort, bindAddr);
          autoStart = true;
        }
      } else {
        httpd = useSsl?new SecurePseudoHttpd(listenPort, bindAddr, keyStorePath, keyStorePasswd):new PseudoHttpd(listenPort, bindAddr);
      }
    } catch(GeneralSecurityException e) {
      throw new ConfigException(xml.getSubConfig("ssl").getFullName(), "Unable to initialize ssl: " + e.getMessage(), e);
    } catch(IOException e) {
      throw new ConfigException(xml.getSubConfig("ssl").getFullName(), "Unable to load keystore '" + keyStorePath +
          "' for ssl: " + e.getMessage(), e);
    }

    allowCspConnect = true;
    ProxyXmlConfig cspcXml = null;
    try {
      cspcXml = xml.getSubConfig("csp-connect");
    } catch(ConfigException e) {}
    if(cspcXml != null) {
      if("false".equalsIgnoreCase(cspcXml.getStringValue("enabled"))) allowCspConnect = false;
      try {
        boolean debug = "true".equalsIgnoreCase(cspcXml.getStringValue("debug"));
        CaProfile.MULTIPLE.setDebug(debug);
      } catch(ConfigException e) {}
      ignoreCacheRequests = "true".equalsIgnoreCase(cspcXml.getStringValue("ignore-cache-requests", "false"));
    }
    if(allowCspConnect) {
      ((ListenPort)CaProfile.MULTIPLE.getListenPorts().get(0)).setPort(httpd.getListenPort()); 
    } else {
      ((ListenPort)CaProfile.MULTIPLE.getListenPorts().get(0)).setPort(0);
    }

    String warFile = xml.getFileValue("war-file", "cs-status.war", false);
    try {
      httpd.setWar(new File(warFile));
    } catch(IOException e) {
      throw new ConfigException(xml.getSubConfig("war-file").getFullName(), "Bad war archive '" + warFile + "': " + e, e);
    }

    String indexFile = "cs-status.html";
    try {
      indexFile = xml.getStringValue("welcome-file");
    } catch(ConfigException e) {}
    httpd.setIndexFile(indexFile);
    httpd.setSilent(true);

    String logFile = null; int count = 0; int limit = 0;
    try {
      ProxyXmlConfig logXml = xml.getSubConfig("log-file");
      count = logXml.getIntValue("rotate-count");
      if(count < 1) count = 0;
      limit = logXml.getIntValue("rotate-max-size");
      if(limit < 1) limit = 0;
    } catch(ConfigException e) {}
    try {
      if(httpdLogger != null) httpdLogger.close();
      logFile = xml.getFileValue("log-file", true);
      if(count > 0 && limit > 0)
        if(!new File(logFile).delete()) { /* ignore */ }
      httpdLogger = ProxyLogger.getFileLogger("WebBackend", new File(logFile), "FINER", count, limit, true);
      httpd.setLogger(httpdLogger.getWrappedLogger());            
    } catch(ConfigException e) {
      httpd.setLogger(null);
    } catch(IOException e) {
      throw new ConfigException(xml.getSubConfig("log-file").getFullName(), "Unable to assign log-file: " + logFile, e);
    }

    superUsers.clear();
    String su = xml.getStringValue("super-users", "");
    if(su.length() > 0) superUsers.addAll(Arrays.asList(su.toLowerCase().split(" ")));

    ProxyXmlConfig ghttpXml;
    if(ghttpd == null) ghttpd = new GHttpBackend(this);
    try {
      ghttpXml = xml.getSubConfig("ghttp");
    } catch (ConfigException e) {
      ghttpd.configUpdated(null);
      ghttpXml = null;
    }
    if(ghttpXml != null) ghttpd.configUpdated(ghttpXml);
    if(ghttpd.isEnabled() && !ghttpd.hasAlternatePort()) httpd.setV11(true);

    httpd.addHttpRequestListener(xmlHandlerPattern, this);
    httpd.addHttpRequestListener(cfgHandlerPattern, this);
    httpd.addHttpRequestListener(bouquetPattern, this);
    httpd.addHttpRequestListener(piconPattern, this);
    httpd.addHttpRequestListener(pluginPattern, this);
    httpd.addHttpRequestListener(ghttpPattern, this);
    httpd.addHttpRequestListener(connectPattern, this);

    if(autoStart) try {
      start();
    } catch(IOException e) {
      throw new ConfigException(xml.getFullName(), "Unable to start httpd: " + e, e);
    }
  }

  public void start() throws IOException {
    httpd.start();
  }

  public void stop() {
    if(httpd != null) {
      httpd.stop();
    }
  }

  private String checkBasicAuth(HttpRequest req) {
    return checkBasicAuth(req, null);
  }

  protected String checkBasicAuth(HttpRequest req, String defaultPasswd) { // return username if successful, null otherwise
    String user;
    String basicAuth = req.getHeader("authorization");

    if(basicAuth != null && basicAuth.toLowerCase().startsWith("basic ")) {
      String userPass = new String(com.bowman.util.Base64Encoder.decode(basicAuth.substring(6).toCharArray()));
      // String userPass = new String(new sun.misc.BASE64Decoder().decodeBuffer(basicAuth.substring(6)));
      int idx;
      if((idx = userPass.indexOf(":")) > 1) {
        user = userPass.substring(0, idx);
        String pass = userPass.substring(idx + 1);
        if(authUser(user, pass)) return user;
        else if(pass.equals(defaultPasswd)) return anonPrefix + user;
        else {
          logger.warning("Http auth failed for user '" + user + "' (" +
              com.bowman.cardserv.util.CustomFormatter.formatAddress(req.getRemoteAddress()) + ").");
          SessionManager.getInstance().fireUserLoginFailed(user, "Web/" + CaProfile.MULTIPLE.getName(), req.getRemoteAddress(),
              "http basic auth failed (bad password)");
          return null;
        }
      }
    }
    return null;
  }

  public HttpResponse doGet(String urlPattern, HttpRequest getRequest) {
    if(xmlHandlerPattern.equals(urlPattern)) {

      // standard basic auth login and simple session tracking
      String session = getRequest.getCookie("JSESSIONID");
      String user = null;
      boolean newSession = false;
      HttpResponse resp;

      if(session == null || !sessions.containsKey(session)) { // no session or old session, require login
        user = checkBasicAuth(getRequest);
        if(user != null) {
          session = createSession(user);
          newSession = true;
        } else {
          return HttpResponse.getAuthReqResponse("Cardservproxy");
        }
      }

      // user already logged in
      if(user == null) user = (String)sessions.get(session);
      String cmd = getRequest.getParameter("command");
      if(cmd == null) resp = HttpResponse.getErrorResponse(500, "Missing parameter: command");
      else {
        Map params = getRequest.getParams();
        params.remove("command");
        try {
          String reply = null;
          if(helper.statusCommands.containsKey(cmd.toLowerCase())) reply = helper.onQryStatusCommand(cmd, params, user);
          else if(helper.ctrlCommands.containsKey(cmd.toLowerCase())) reply = helper.onQryControlCommand(cmd, params, user);
          if(reply != null) resp = new HttpResponse(reply, "text/xml");
          else resp = HttpResponse.getErrorResponse(500, "Unknown command: " + cmd);
        } catch (Exception e) {
          e.printStackTrace();
          resp = HttpResponse.getErrorResponse(e);
        }
      }
      if(newSession) resp.setCookie("JSESSIONID", session);
      resp.setHeader("Expires", new Date(0));
      return resp;

    } else if(bouquetPattern.equals(urlPattern)) {
      
      String bouquet = (String)bouquets.get(getRequest.getQueryString());
      if(bouquet == null) return null;
      else {
        HttpResponse resp = new HttpResponse(bouquet, "text/plain");
        resp.setHeader("Content-Disposition", "attachment; filename=" + getRequest.getQueryString().substring(1));
        return resp;
      }

    } else if(piconPattern.equals(urlPattern)) {

      if(!httpd.fileExists(getRequest.getQueryString())) {
        String imgName = getRequest.getQueryString().substring(piconPattern.length() - 1);
        String[] id = imgName.split("\\.");
        if(id.length == 3) {
          try {
            TvService chan = new TvService(Integer.parseInt(id[0]), id[1]);
            List services = new ArrayList(helper.getServices(null, true));
            int idx = services.indexOf(chan);
            if(idx > -1) chan = (TvService)services.get(idx);
            else return null;

            String piconName = "/picon/" + chan.getName().trim().toLowerCase().replace(' ', '_') + '.' + id[2];            
            
            if(!httpd.fileExists(piconName)) piconName = "/picon/unknown." + id[2];

            // rewrite querystring and return null response = httpd will handle as static file request
            getRequest.setQueryString(piconName);

          } catch (NumberFormatException e) {
          } catch (RemoteException e) {

          }
        }
      }

    } else if(cfgHandlerPattern.equals(urlPattern)) {
      return HttpResponse.getErrorResponse(405, getRequest.getMethod());
    } else if(pluginPattern.equals(urlPattern)) {
      try {
        return doPluginAccess(getRequest, false);
      } catch(Exception e) {
        e.printStackTrace();
        return HttpResponse.getErrorResponse(e);
      }
    } else if(ghttpPattern.equals(urlPattern)) {
      return ghttpd.doGet(urlPattern, getRequest);
    }

    return null;
  }

  private void setHostName(String host) {
    String[] s = host.split(":");
    if(s.length > 1) host = s[0];
    CtrlCommand cmd = (CtrlCommand)helper.ctrlCommands.get("gen-keystore");
    cmd.getParam("host").setValue(host);
    if(cmd.getParam("validity").value == null) cmd.getParam("validity").setValue("1000");
    if(cmd.getParam("password").value == null) cmd.getParam("password").setValue("123456");
  }

  public HttpResponse doPost(String urlPattern, HttpRequest postRequest) {
    if(bouquetPattern.equals(urlPattern)) return HttpResponse.getErrorResponse(405, postRequest.getMethod());
    if(piconPattern.equals(urlPattern)) return HttpResponse.getErrorResponse(405, postRequest.getMethod());
    if(xmlHandlerPattern.equals(urlPattern)) {
      InputStream is = new ByteArrayInputStream(postRequest.getContent());
      if(debugXml) System.out.println("\n<--\n" + new String(postRequest.getContent()));
      try {
        setHostName(postRequest.getHeader("host"));
        String reply = helper.onXMLInput(new XMLConfig(is, false, postRequest.getEncoding()), checkBasicAuth(postRequest), postRequest.getRemoteAddress());
        if(debugXml) System.out.println("\n-->\n" + reply);
        HttpResponse resp = new HttpResponse(reply, "text/xml", true);
        // hack to transfer sessionId to cookie as well
        XMLConfig replyXml = new XMLConfig(reply, false);
        replyXml = replyXml.getSubConfig("status");
        String sessionId = replyXml == null ? null : replyXml.getString("session-id");
        if(sessionId != null) resp.setCookie("JSESSIONID", sessionId);
        return resp;
      } catch(XMLConfigException xe) {
        xe.printStackTrace();
        System.out.println(postRequest.getContentStr());
        return HttpResponse.getErrorResponse(xe);
      } catch(Exception e) {
        e.printStackTrace();
        return HttpResponse.getErrorResponse(e);
      }
    } else if(cfgHandlerPattern.equals(urlPattern)) {
      try {
        String authUser;
        String session = postRequest.getCookie("JSESSIONID");
        if(session != null && sessions.containsKey(session)) authUser = (String)sessions.get(session);
        else authUser = checkBasicAuth(postRequest);
        if(authUser == null || !isSuperUser(authUser)) return HttpResponse.getErrorResponse(403);
        else {
          ProxyXmlConfig currentConfig =
              new ProxyXmlConfig(new XMLConfig(new ByteArrayInputStream(postRequest.getContent()), false, "UTF-8"));
          ProxyConfig config = ProxyConfig.getInstance();
          config.parseConfig(currentConfig);

          logger.info("Configuration updated by: " + authUser + " (" +
              com.bowman.cardserv.util.CustomFormatter.formatAddress(postRequest.getRemoteAddress()) + ")");
          config.saveCfgFile(postRequest.getContent());

          return new HttpResponse(XmlHelper.getCfgResult("Updated configuration installed OK."), "text/xml", true);
        }
      } catch (XMLConfigException e) {
        return new HttpResponse(XmlHelper.getCfgResult("Malformed XML: " + e.getMessage() +
            "\nUpdated configuration NOT installed."), "text/xml", true);
      } catch (ConfigException e) {
        StringBuffer sb = new StringBuffer();
        sb.append("Configuration error:\n");
        if(e.getLabel() != null) {
          if(e.getLabel().indexOf(' ') == -1) sb.append("- Element: <").append(e.getLabel()).append(">\n");
          else {
            sb.append("- Element: <").append(e.getLabel()).append("\n");
            if(e.getSubLabel() != null) sb.append("- Attribute: ").append(e.getSubLabel()).append("\n");
          }
        }
        sb.append("- Message: ").append(e.getMessage());
        sb.append("\nUpdated configuration NOT installed.");
        return new HttpResponse(XmlHelper.getCfgResult(sb.toString()), "text/xml", true);
      } catch (IOException e) {
        return new HttpResponse(XmlHelper.getCfgResult("Updated configuration installed, but an error occured saving to file: " +
            e), "text/xml", true);
      } catch (Exception e) {
        e.printStackTrace();
        return HttpResponse.getErrorResponse(e);
      }
    } else if(pluginPattern.equals(urlPattern)) {
      try {
        return doPluginAccess(postRequest, true);
      } catch(Exception e) {
        e.printStackTrace();
        return HttpResponse.getErrorResponse(e);
      }
    } else if(ghttpPattern.equals(urlPattern)) {
      return ghttpd.doPost(ghttpPattern, postRequest);
    }
    return null;
  }

  private HttpResponse doPluginAccess(HttpRequest req, boolean post) throws RemoteException {

    String authUser = checkBasicAuth(req);
    if(authUser == null) {
      String session = req.getCookie("JSESSIONID");
      if(session != null) authUser = (String)sessions.get(session);
    }
    boolean admin = authUser != null && proxy.isAdmin(authUser);

    String qryStr = req.getQueryString();
    if(qryStr.startsWith("/")) qryStr = qryStr.substring(1);
    String[] path = qryStr.split("/");

    if(path.length < 2) return HttpResponse.getErrorResponse(404);

    Map plugins = ProxyConfig.getInstance().getProxyPlugins();

    if(PLUGIN_SCRIPT.equals(path[1])) { // check all plugins and merge any load.js scripts they return
      if(post) return HttpResponse.getErrorResponse(405, req.getMethod());
      StringBuffer script = new StringBuffer();
      byte[] buf;
      for(Iterator iter = plugins.values().iterator(); iter.hasNext(); ) {
        buf = ((ProxyPlugin)iter.next()).getResource(PLUGIN_SCRIPT, admin);
        if(buf != null) try {
          script.append(new String(buf, "ISO-8859-1"));
        } catch(UnsupportedEncodingException e) {
          e.printStackTrace();
        }
      }
      return new HttpResponse(script.toString(), "application/x-javascript");
    }

    // allow arbitrary access to plugin files, via /plugin/pluginname/filepath
    ProxyPlugin plugin = (ProxyPlugin)plugins.get(path[1].toLowerCase());

    if(plugin == null) return HttpResponse.getErrorResponse(404, "No such plugin: " + path[1]);
    else {
      boolean open = path.length > 2 && path[2].equals("open");
      String pluginPath = qryStr.substring(path[0].length() + 1 + path[1].length());
      if(!open && authUser == null) return HttpResponse.getAuthReqResponse(plugin.getName()); // require auth      
      byte[] buf;
      if(post) buf = plugin.getResource(pluginPath, req.getContent(), admin);
      else buf = plugin.getResource(pluginPath, admin);
      return HttpResponse.getFileResponse(buf, pluginPath, httpd);
    }

  }

  public HttpResponse doConnect(String urlPattern, HttpRequest connectRequest) {
    if(!connectPattern.equals(urlPattern)) {
      return HttpResponse.getErrorResponse(405, connectRequest.getMethod());
    } else if(!allowCspConnect) {
      return HttpResponse.getErrorResponse(503); // service not available
    } else {
      String authUser = checkBasicAuth(connectRequest);
      if(authUser == null) return HttpResponse.getAuthReqResponse("Cardservproxy");
      else {
        // no ip check for regular web logins but these should have one
        HttpResponse error = doIpCheck(connectRequest, authUser);
        if(error != null) return error;
        // do handoff
        acceptCspConnect(authUser, connectRequest);
        return HttpResponse.CONNECT_RESPONSE;
      }
    }
  }

  protected HttpResponse doIpCheck(HttpRequest req, String authUser) {
    ProxyConfig config = ProxyConfig.getInstance();
    String ip = req.getRemoteAddress();
    ListenPort listenPort = (ListenPort)CaProfile.MULTIPLE.getListenPorts().get(0);

    if(listenPort.isDenied(ip)) {
      logger.fine("Rejected connection for [" + listenPort.getProfile().getName() + ":" +
          listenPort + "]: " + com.bowman.cardserv.util.CustomFormatter.formatAddress(ip) + " not allowed.");
      SessionManager.getInstance().fireUserLoginFailed("?@" + ip, listenPort + "/" + CaProfile.MULTIPLE.getName(),
          ip, "rejected by [" + listenPort + "] ip deny list.");
      return HttpResponse.getErrorResponse(403, "Ip denied");
    }

    String ipMask = config.getUserManager().getIpMask(authUser);
    if(!Globber.match(ipMask, ip, false)) {
      if(config.isLogFailures())
        logger.warning("User '" + authUser + "' (" + com.bowman.cardserv.util.CustomFormatter.formatAddress(ip) +
            ") login denied, ip check failed: " + ipMask);
      SessionManager.getInstance().fireUserLoginFailed(authUser, listenPort + "/" + CaProfile.MULTIPLE.getName(),
          ip, "ip check failed: " + ipMask);
      return HttpResponse.getErrorResponse(403, "Ip check failed");
    }
    return null;
  }

  public void acceptCspConnect(String user, HttpRequest connectRequest) {
    ProxyConfig config = ProxyConfig.getInstance();
    ListenPort lp = (ListenPort)CaProfile.MULTIPLE.getListenPorts().get(0);

    ProxySession ps = null;

    try {
      ps = new CspSession(connectRequest, user, lp, config.getDefaultMsgListener(), !ignoreCacheRequests);
    } catch (Exception e) {
      logger.severe("Exception creating CspSession: " + e, e);
    }

    if(ps == null) {
      try { connectRequest.getConnection().close(); } catch (Exception e) {}
    } else {
      if(config.getRemoteHandler() != null) ps.addTransactionListener(ProxyConfig.getInstance().getRemoteHandler());             
    }
  }

  boolean authUser(String userName, String passwd) {
    try {
      return proxy.authenticateUser(userName, passwd);
    } catch(RemoteException e) {
      e.printStackTrace();
      return false;
    }
  }

  String createSession(String user) {
    logger.info("User '" + user + "' logged in.");
    String sessionId = Long.toString(System.currentTimeMillis(), Character.MAX_RADIX);
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("MD5");
      sessionId = new String(Base64Encoder.encode(md.digest(sessionId.getBytes())));
    } catch(NoSuchAlgorithmException e) {
      e.printStackTrace();
    }           
    sessions.put(sessionId, user);
    return sessionId;
  }

  public void eventRaised(RemoteEvent event) throws RemoteException {

    if(event == null) return;

    switch(event.getType()) {
      case RemoteEvent.CWS_CONNECTED:
        connecting.remove(event.getLabel());
        invalid.remove(event.getLabel());
        eventLog.add(0, event);
        break;
      case RemoteEvent.CWS_CONNECTION_FAILED:
        if(!connecting.contains(event.getLabel())) {
          connecting.add(event.getLabel());
          eventLog.add(0, event);
        }
        break;
      case RemoteEvent.CWS_DISCONNECTED:
      case RemoteEvent.CWS_WARNING:
      case RemoteEvent.CWS_FOUND_SERVICE:
      case RemoteEvent.CWS_LOST_SERVICE:
      case RemoteEvent.PROXY_STARTUP:
        eventLog.add(0, event);
        break;
      case RemoteEvent.USER_LOGIN:
      case RemoteEvent.USER_STATUS_CHANGED:
      case RemoteEvent.USER_LOGOUT:
      case RemoteEvent.USER_LOGINFAIL:
        // ignore
        break;
      case RemoteEvent.ECM_TRANSACTION:
        logUserTransaction(event);
        if(event.getProperty("cws-name") != null) logCwsTransaction(event);
        if("true".equalsIgnoreCase(event.getProperty("warning"))) addWarning(event);
        break;
      case RemoteEvent.CWS_INVALID_CARD:
        if(!invalid.contains(event.getLabel())) {
          invalid.add(event.getLabel());
          eventLog.add(0, event);
        }
        break;
      case RemoteEvent.LOG_EVENT:
        fileLog.add(0, event);
        break;

      default:
        System.err.println("Unknown remote event received: " + event.getType() + " " + event.getLabel() + " " +
            event.getMessage());
    }

    if(eventLog.size() > MAX_EVENTS) eventLog.remove(eventLog.size() - 1);
    if(fileLog.size() > MAX_EVENTS) fileLog.remove(fileLog.size() - 1);
  }

  private void logUserTransaction(RemoteEvent event) {
    List ecmLog = (List)userTransactions.get(event.getMessage()); // message = username
    if(ecmLog == null) {
      ecmLog = new ArrayList();
      userTransactions.put(event.getMessage(), ecmLog);
    }
    ecmLog.add(0, new RemoteEvent(event));
    if(ecmLog.size() > MAX_ECMS) ecmLog.remove(ecmLog.size() - 1);
  }

  private void logCwsTransaction(RemoteEvent event) {
    String flags = event.getProperty("flags");
    if(flags == null) return;
    if(flags.indexOf('F') == -1 && flags.indexOf('T') == -1) return;
    List ecmLog = (List)cwsTransactions.get(event.getProperty("cws-name"));
    if(ecmLog == null) {
      ecmLog = new ArrayList();
      cwsTransactions.put(event.getProperty("cws-name"), ecmLog);
    }
    ecmLog.add(0, new RemoteEvent(event));
    if(ecmLog.size() > MAX_ECMS) ecmLog.remove(ecmLog.size() - 1);
  }

  private void addWarning(RemoteEvent ev) {
    int idx = warningLog.indexOf(ev);
    if(idx > -1) {
      RemoteEvent prev = (RemoteEvent)warningLog.remove(idx);
      int count = 1;
      try {
        count += Integer.parseInt(prev.getProperty("count"));
      } catch (Exception e) {
        count++;
      }
      ev.setProperty("count", String.valueOf(count));
    }
    warningLog.add(0, ev);
    if(warningLog.size() > MAX_EVENTS) warningLog.remove(warningLog.size() - 1);
  }

  public boolean isSuperUser(String user) {
    if(user == null) return false;
    try {
      return superUsers.contains(user.toLowerCase()) && proxy.isAdmin(user);
    } catch(RemoteException e) {
      e.printStackTrace();
      return false;
    }
  }

  public void clearTransactions(String profile) {
    if(profile == null) {
      userTransactions.clear();
      cwsTransactions.clear();
    } else {
      clearTransactions(profile, userTransactions);
      clearTransactions(profile, cwsTransactions);
    }
  }

  private void clearTransactions(String profile, Map map) {
    String user; List list; RemoteEvent ev;
    for(Iterator iter = new ArrayList(map.keySet()).iterator(); iter.hasNext(); ) {
      user = (String)iter.next();
      list = (List)map.get(user);
      for(Iterator i = list.iterator(); i.hasNext(); ) {
        ev = (RemoteEvent)i.next();
        if(profile.equals(ev.getProfile())) i.remove();
      }
    }
  }
}
