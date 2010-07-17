package com.bowman.cardserv;

import com.bowman.httpd.*;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.interfaces.XmlConfigurable;
import com.bowman.cardserv.crypto.DESUtil;

import java.net.*;
import java.io.*;
import java.text.MessageFormat;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Mar 22, 2009
 * Time: 9:09:59 PM
 */
public class AgentWeb implements HttpRequestListener, XmlConfigurable {

  static final String[] PATTERNS = {"/login", "/checkin", "/output*", "/installer.sh", "/open/*"};

  private PseudoHttpd httpd;
  private ProxyLogger httpdLogger;
  private DreamboxPlugin parent;
  private String frontendHost;
  private int frontendPort;

  public AgentWeb(DreamboxPlugin parent) {
    this.parent = parent;
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    InetAddress bindAddr = null; String bindIp = null;
    try {
      bindIp = xml.getStringValue("bind-ip");
      bindAddr = InetAddress.getByName(bindIp);
    } catch(ConfigException e) {
    } catch(UnknownHostException e) {
      throw new ConfigException(xml.getFullName(), "bind-ip", "Invalid status-web bind-ip: " + bindIp);
    }

    int listenPort = xml.getPortValue("listen-port", 8081);
    frontendPort = xml.getPortValue("frontend-port", listenPort);
    try {
      frontendHost = xml.getStringValue("frontend-host");
    } catch (ConfigException e) {
      frontendHost = null;
    }

    if(httpd != null) {
      if(httpd.getListenPort() != listenPort) {
        httpd.stop();
        httpd = new PseudoHttpd(listenPort, bindAddr);
      }
    } else {
      httpd = new PseudoHttpd(listenPort, bindAddr);
    }

    try {
      httpd.setWar(new File(DreamboxPlugin.PLUGIN_JAR)); // have the httpd read directly from the plugin-jar
      httpd.setWarRoot("web"); // but only expose the webdir
    } catch(IOException e) {
      throw new ConfigException(xml.getFullName(), "Httpd failed to read/find plugin jar 'dreamboxplugin.jar': " + e, e);
    }
    
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
      if(count > 0 && limit > 0) new File(logFile).delete();
      httpdLogger = ProxyLogger.getFileLogger("AgentWeb", new File(logFile), "FINER", count, limit, true);
      httpd.setLogger(httpdLogger.getWrappedLogger());
    } catch(ConfigException e) {
      httpd.setLogger(null);
    } catch(IOException e) {
      throw new ConfigException(xml.getSubConfig("log-file").getFullName(), "Unable to assign log-file: " + logFile, e);
    }

    httpd.addHttpRequestListener(PATTERNS, this);
  }

  public void stop() {
    httpd.stop();
    if(httpdLogger != null) httpdLogger.close();          
  }

  public void start() {
    try {
      httpd.start();
    } catch(IOException e) {
      parent.logger.severe("Unable to start httpd: " + e, e);
    }
  }

  public HttpResponse doGet(String s, HttpRequest req) {
    if(s.equals("/installer.sh")) return doGetInstaller(req);
    else if(s.equals("/login")) return doGetLogin(req);
    else if(s.equals("/checkin")) return doGetCheckin(req);
    else if(s.startsWith("/open/")) {
      byte[] buf = parent.getFile(req.getQueryString().substring(6));
      System.out.println(req.getQueryString().substring(6));
      if(buf != null) return HttpResponse.getFileResponse(buf, s, httpd);
    }
    return null;
  }

  private HttpResponse doGetInstaller(HttpRequest req) {
    String user = checkBasicAuth(req);
    if(user != null) {
      try {
        String installer = new String(parent.getResource("installer-template.sh", false), "ISO-8859-1");
        String fh = frontendHost;
        if(fh == null) fh = req.getHeader("host");
        installer = installer.replaceFirst("\\{0\\}", fh);
        installer = installer.replaceFirst("\\{1\\}", String.valueOf(frontendPort));
        installer = installer.replaceFirst("\\{2\\}", user);
        installer = installer.replaceFirst("\\{3\\}", String.valueOf(parent.checkInterval));
        installer = installer.replaceFirst("\\{4\\}", parent.getAgentVersion());
        HttpResponse resp = new HttpResponse(installer, "application/x-sh");
        resp.setHeader("Content-Disposition", "attachment; filename=installer-" + user + ".sh");
        return resp;
      } catch(UnsupportedEncodingException e) {
        throw new IllegalStateException(e.toString());
      }
    } else return HttpResponse.getAuthReqResponse(parent.getName());
  }

  private HttpResponse doGetLogin(HttpRequest req) {

    String user = req.getHeader("csp-user");
    if(parent.proxy.getUserPasswd(user) == null) return HttpResponse.getErrorResponse(403, "Unknown user: " + user);
    String seed = req.getHeader("csp-seed");
    String macAddr = req.getHeader("csp-mac");

    BoxMetaData box;
    BoxMetaData[] existingBoxes = parent.findBox(macAddr, user);
    if(existingBoxes.length > 1) {
      parent.logger.warning("Multiple boxes with mac '" + macAddr + "' for user: " + user);
      return HttpResponse.getErrorResponse(403, "Ambigious box identity: mac=" + macAddr + " user=" + user);
    } else if(existingBoxes.length == 1) {
      box = existingBoxes[0];
    } else {
      box = new BoxMetaData(macAddr, user, seed);
      parent.registerBox(box);
    }

    String type = req.getHeader("csp-boxtype");
    if(type != null) type = type.toLowerCase();
    box.setProperty("type", type);
    box.setProperty("local-ip", req.getHeader("csp-local-ip"));
    box.setProperty("agent-version", req.getHeader("csp-agent-version"));
    box.setProperty("kernel-version", req.getHeader("csp-kernel-version"));
    box.setProperty("image-guess", req.getHeader("csp-img-guess"));
    box.setProperty("image-info", req.getHeader("csp-img-info"));
    if(req.getHeader("csp-uname-m") != null) box.setProperty("machine", req.getHeader("csp-uname-m"));

    box.setProperty("external-ip", req.getRemoteAddress());
    box.checkin(Integer.parseInt(req.getHeader("csp-iv")));

    return new HttpResponse(box.getBoxId(), "text/plain");
  }

  private HttpResponse doGetCheckin(HttpRequest req) {

    String boxId = req.getHeader("csp-boxid");
    if(boxId == null) return HttpResponse.getErrorResponse(403, "Missing boxid");
    else {
      BoxMetaData box = parent.getBox(boxId);
      if(box == null) return HttpResponse.getErrorResponse(403, "Invalid/unknown boxid: " + boxId);

      box.setProperty("external-ip", req.getRemoteAddress());
      String sid = req.getHeader("csp-sid");
      if(sid != null) {
        if(sid.endsWith("h")) sid = String.valueOf(Integer.parseInt(sid.substring(0, sid.length() - 1), 16));
        box.setProperty("sid", sid);
      }
      String onid = req.getHeader("csp-onid");
      if(onid != null) {
        if(onid.endsWith("h")) onid = String.valueOf(Integer.parseInt(onid.substring(0, onid.length() - 1), 16));
        box.setProperty("onid", onid);
      }            
      box.setProperty("uptime", req.getHeader("csp-uptime"));
      String agentV = req.getHeader("csp-agent-version");
      if(agentV != null) box.setProperty("agent-version", agentV);
      String localIp = req.getHeader("csp-local-ip");
      if(localIp != null) box.setProperty("local-ip", localIp);

      box.checkin(Integer.parseInt(req.getHeader("csp-iv")));

      if(box.getPendingOperation() != null) {
        return new HttpResponse(generateOperationScript(box), "text/plain");
      } else return new HttpResponse("", "text/plain"); // empty response = agent does nothing
    }
  }

  protected String generateOperationScript(BoxMetaData box) {
    BoxOperation op = box.getPendingOperation();
    if(op != null) {
      String s = null;
      if(op.isScript()) {
        byte[] buf = parent.getFile("scripts/" + op.getScriptName());
        if(buf == null) parent.logger.warning("Operation '" + op + "' was queued for box '" + box.getBoxId() +
          "', but no such script was found in /web/open/scripts/");
        else try {
          s = loadScript(buf);
        } catch(IOException e) {
          e.printStackTrace();
        }
      } else s = op.getCmdLine();

      if(s != null) {

        s = resolveContextVars(s, box, op);

        // wrap the script, turning the output into a valid http CONNECT operation, connecting it with the boxId + op
        StringBuffer script = new StringBuffer("#!/bin/ash\n");
        script.append("echo \"CONNECT /output?boxId=").append(box.getBoxId()).append("&opId=").append(op.getId());
        script.append("\n\n").append("\""); // end of http header
        script.append("\n");
        script.append(s); // output from stored script or cmdline

        System.out.println(script);

        box.runPendingOperation();
        return script.toString();
      }
    }
    return "";
  }

  protected String loadScript(byte[] buf) throws IOException {
    StringBuffer sb = new StringBuffer();
    BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buf), "ISO-8859-1"));
    String line;
    while((line = br.readLine()) != null) {
      if(line.startsWith("#!/")) continue; // skip shebang
      sb.append(line).append("\n");
    }
    return sb.toString();
  }

  protected String resolveContextVars(String s, BoxMetaData box, BoxOperation op) {

    if(s.indexOf("{TUNNELPORT}") != -1) s = s.replaceFirst("\\{TUNNELPORT\\}", String.valueOf(parent.getTunnelPort()));
    s = s.replaceFirst("\\{SSHDPORT\\}", String.valueOf(parent.getSshdPort()));
    s = s.replaceFirst("\\{CPUARCH\\}", parent.getBoxCpuArch(box));
    s = s.replaceFirst("\\{USERNAME\\}", box.getUser());
    s = s.replaceFirst("\\{PASSWORD\\}", parent.getUserPasswd(box.getUser()));
    s = s.replaceFirst("\\{IMAGE\\}", box.getProperty("image-guess"));
    s = s.replaceFirst("\\{BOXTYPE\\}", box.getProperty("type"));
    s = s.replaceFirst("\\{PARAMS\\}", op.getParams());

    return s;
  }

  public HttpResponse doPost(String s, HttpRequest req) {
    System.out.println(s + " - " + req);
    return null;
  }

  public HttpResponse doConnect(String urlPattern, HttpRequest connectRequest) {
    if(urlPattern.equals("/output*")) {
      System.out.println(connectRequest.getQueryString());
      BoxMetaData box = parent.getBox(connectRequest.getParameter("boxId"));
      BoxOperation op = box.getOperation(Integer.parseInt(connectRequest.getParameter("opId")));
      if(box != null && op != null) new OutputReaderThread(connectRequest.getConnection(), box, op);
      else {
        parent.logger.warning("Connect request from unknown/deleted box or operation: " + connectRequest.getQueryString());
        return HttpResponse.getErrorResponse(401);
      }
      return HttpResponse.CONNECT_RESPONSE;
    } else return HttpResponse.getErrorResponse(405, connectRequest.getMethod());
  }  

  private String checkBasicAuth(HttpRequest req) { // return username if successful, null otherwise
    String user;
    String basicAuth = req.getHeader("authorization");

    if(basicAuth != null && basicAuth.toLowerCase().startsWith("basic ")) {
      String userPass = new String(com.bowman.util.Base64Encoder.decode(basicAuth.substring(6).toCharArray()));
      int idx;
      if((idx = userPass.indexOf(":")) > 1) {
        user = userPass.substring(0, idx);
        String pass = userPass.substring(idx + 1);
        if(parent.proxy.authenticateUser(user, pass)) return user;
        else {
          parent.logger.warning("Http auth failed for user '" + user + "' (" +
              com.bowman.cardserv.util.CustomFormatter.formatAddress(req.getRemoteAddress()) + ").");
          return null;
        }
      }
    }
    return null;
  }

  public String getFrontendHost() {
    return frontendHost;
  }

  public int getFrontendPort() {
    return frontendPort;
  }

  private static class OutputReaderThread extends Thread {

    Socket conn;
    BoxMetaData box;
    BoxOperation op;

    OutputReaderThread(Socket conn, BoxMetaData box, BoxOperation op) {
      super("OutputReaderThread[" + op.getId() + "]");
      this.conn = conn;
      this.box = box;
      this.op = op;
      start();
    }

    public void run() {
      op.start(conn);
      try {
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        while((line = br.readLine()) != null) {
          System.out.println(op.getId() + " - " + line);
          if(line.indexOf(0) == -1) op.appendOutput(line);
        }
      } catch(IOException e) {
        e.printStackTrace();
      }
      op.end();
    }
  }
}
