package com.bowman.cardserv.web;

import com.bowman.cardserv.*;
import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.session.GHttpSession;
import com.bowman.cardserv.util.*;
import com.bowman.httpd.*;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2011-06-07
 * Time: 22:03
 */
public class GHttpBackend implements XmlConfigurable, HttpRequestListener {

  private static final int RECORD_SIZE = 28;

  protected static final String API_PREFIX = "/api";

  protected static final String API_CACHE_GET = API_PREFIX + "/c/";
  protected static final String API_ECM_POST = API_PREFIX + "/e/";
  protected static final String API_FEEDER_POST = API_PREFIX + "/f/";
  protected static final String API_CAPMT = API_PREFIX + "/p/";

  private WebBackend parent;
  private CacheHandler cache;
  private ProxyConfig config;
  private boolean enabled;
  private int alternatePort;
  private PseudoHttpd httpd;
  private ProxyLogger ghttpdLogger;
  private String accessPasswd, feederPasswd;

  private Map sessions = new HashMap();
  private Map sessionsByUser = new HashMap();
  private Map contexts = Collections.synchronizedMap(new LinkedHashMap());

  private StatusCommand contextsCmd;

  public GHttpBackend(WebBackend parent) {
    this.parent = parent;
    this.config = ProxyConfig.getInstance();
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    if(xml == null) enabled = true;
    else enabled = "true".equalsIgnoreCase(xml.getStringValue("enabled", "true"));

    if(enabled) {
      if(xml == null) alternatePort = -1;
      else {
        try {
          xml.getStringValue("alternate-port");
        } catch (ConfigException e) {
          alternatePort = -1;
          if(httpd != null) {
            httpd.stop();
            httpd = null;
          }
        }
        if(alternatePort != -1) {
          InetAddress bindAddr = null; String bindIp = null;
          try {
            bindIp = xml.getStringValue("alternate-bind-ip");
            bindAddr = InetAddress.getByName(bindIp);
          } catch(ConfigException e) {
          } catch(UnknownHostException e) {
            throw new ConfigException(xml.getFullName(), "alternate-bind-ip", "Invalid ghttp bind-ip: " + bindIp);
          }
          alternatePort = xml.getPortValue("alternate-port");
          if(alternatePort == parent.httpd.getListenPort())
            throw new ConfigException(xml.getFullName(), "alternate-port", "Port in use: " + alternatePort);
          if(httpd != null && httpd.getListenPort() != alternatePort) {
            httpd.stop();
            httpd = null;
          }
          if(httpd == null) httpd = new PseudoHttpd(alternatePort, bindAddr);
          httpd.addHttpRequestListener(API_PREFIX + "/*", this);
          httpd.setSilent(true);
          httpd.setV11(true);
          try {
            httpd.start();
          } catch (IOException e) {
            httpd = null;
            throw new ConfigException(xml.getFullName(), "Unable to start alternate httpd for ghttp: " + e, e);
          }
        }
      }
      if(xml == null) {
        accessPasswd = null;
        feederPasswd = null;
      } else {
        try {
          accessPasswd = xml.getStringValue("open-access-password");
        } catch (ConfigException e) {
          accessPasswd = null;
        }
        try {
          feederPasswd = xml.getStringValue("feeder-password");
        } catch (ConfigException e) {
          feederPasswd = null;
        }

        if(feederPasswd != null && accessPasswd != null && accessPasswd.equals(feederPasswd))
          throw new ConfigException(xml.getFullName(), "Passwords must be different.");

        if(httpd != null) {
          String logFile = null; int count = 0; int limit = 0;
          try {
            ProxyXmlConfig logXml = xml.getSubConfig("log-file");
            count = logXml.getIntValue("rotate-count");
            if(count < 1) count = 0;
            limit = logXml.getIntValue("rotate-max-size");
            if(limit < 1) limit = 0;
          } catch(ConfigException e) {}
          try {
            if(ghttpdLogger != null) ghttpdLogger.close();
            logFile = xml.getFileValue("log-file", true);
            if(count > 0 && limit > 0)
              if(!new File(logFile).delete()) { /* ignore */ }
            ghttpdLogger = ProxyLogger.getFileLogger("GHttpBackend", new File(logFile), "FINER", count, limit, true);
            httpd.setLogger(ghttpdLogger.getWrappedLogger());
          } catch(ConfigException e) {
            httpd.setLogger(null);
          } catch(IOException e) {
            throw new ConfigException(xml.getSubConfig("log-file").getFullName(), "Unable to assign log-file: " + logFile, e);
          }
        }

      }

      if(contextsCmd == null) {
        try {
          contextsCmd = new StatusCommand("ca-contexts", "Show ca-contexts map", "List all known ca-contexts and associated ecm pids.", true);
          contextsCmd.register(this);
        } catch(NoSuchMethodException e) {
          e.printStackTrace();
        }
      }

    } else {
      if(httpd != null) {
        httpd.stop();
        httpd = null;
      }
      if(contextsCmd != null) {
        contextsCmd.unregister();
        contextsCmd = null;
      }
    }

  }

  public void runStatusCmdCaContexts(XmlStringBuffer xb, Map params) {
    xb.appendElement("ca-contexts", "count", contexts.size());
    String key; CaContext cc; EcmPid pid;
    for(Iterator iter = new ArrayList(contexts.keySet()).iterator(); iter.hasNext();) {
      key = (String)iter.next();
      cc = (CaContext)contexts.get(key);
      xb.appendElement("ca-context", "key", key);
      xb.appendAttr("name-space", Long.toHexString(cc.nameSpace));
      if(!cc.ecmPids.isEmpty()) {
        xb.appendAttr("pids", cc.ecmPids.size());
        xb.endElement(false);
        for(Iterator i = cc.iterator(); i.hasNext(); ) {
          pid = (EcmPid)i.next();
          xb.appendElement("ecm-pid", "pid", Integer.toHexString(pid.pid));
          xb.appendAttr("caid", Integer.toHexString(pid.caId));
          xb.appendAttr("provider-ident", Integer.toHexString(pid.provId));
          if(!pid.lengths.isEmpty()) xb.appendAttr("lengths", pid.getLengths());
          xb.closeElement();
        }
        xb.closeElement("ca-context");
      } else xb.endElement(true);
    }
    xb.closeElement("ca-contexts");
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean hasAlternatePort() {
    return httpd != null;
  }

  private CamdNetMessage waitForReply(CamdNetMessage request) {
    CamdNetMessage reply = cache.peekReply(request);
    if(reply != null) return reply;
    else reply = cache.processRequest(-1, request, true, request.getMaxWait() * 2); // todo
    return reply;
  }

  private String generateSessionId(String seed) {
    String sessionId = Integer.toString(Math.abs(seed.hashCode()), Character.MAX_RADIX);
    while(sessionId.length() < 6) sessionId = "0" + sessionId;
    return sessionId;
  }

  private GHttpSession newSession(String user, String ip) {
    String id = generateSessionId(user + parent.createSession(user) + ip);
    GHttpSession gs = new GHttpSession(id, user, ip);
    sessions.put(id, gs);
    if(!user.startsWith(WebBackend.anonPrefix)) sessionsByUser.put(user, gs);
    return gs;
  }

  private GHttpSession getSession(String id, String ip) {
    GHttpSession gs = (GHttpSession)sessions.get(id);
    if(gs != null && gs.isExpired()) {
      sessionsByUser.remove(gs.getUser());
      sessions.remove(id);
      gs = null;
    }
    if(gs != null && ip.equals(gs.getRemoteAddress())) return gs;
    else return null;
  }

  private GHttpSession findSession(String user, String ip) {
    GHttpSession gs = (GHttpSession)sessionsByUser.get(user);
    if(gs != null && gs.isExpired()) {
      sessionsByUser.remove(user);
      sessions.remove(gs.getGhttpSessionId());
      gs = null;
    }
    if(gs != null && ip.equals(gs.getRemoteAddress())) return gs;
    else return null;
  }

  private synchronized GHttpSession doGhttpAuth(HttpRequest req, String[] s) throws GHttpAuthException {
    GHttpSession gs = null;
    if(s[3].length() >= 6) { // session id included
      gs = getSession(s[3], req.getRemoteAddress());
    }
    if(gs == null) { // no session id in query string (or invalid/expired session), check for auth
      String user = parent.checkBasicAuth(req, accessPasswd);
      if(user == null) throw new GHttpAuthException(getErrorResponse(401, "Authorization required"));
      if(!user.startsWith(WebBackend.anonPrefix)) {
        HttpResponse error = parent.doIpCheck(req, user);
        if(error != null) throw new GHttpAuthException(getErrorResponse(403, "IP check failed"));
        gs = findSession(user, req.getRemoteAddress()); // reuse when identified, one session per user
      }
      if(gs == null) gs = newSession(user, req.getRemoteAddress());
    }
    if(gs != null) gs.touch();
    return gs;
  }

  public HttpResponse doGet(String urlPattern, HttpRequest req) {
    if(!enabled) return getErrorResponse(403, "Disabled");
    else {
      String q = req.getQueryString();
      if(q.startsWith(API_CACHE_GET)) {
        return doCacheGet(req);
      } else if(q.startsWith(API_CAPMT)) {
        return doPmtGetOrPost(req);
      } else return HttpResponse.getErrorResponse(404);
    }
  }

  private HttpResponse doCacheGet(HttpRequest req) {
    if(cache == null) cache = config.getCacheHandler();
    try {
      String[] s = req.getQueryString().split("/");
      GHttpSession gs = doGhttpAuth(req, s);
      if(gs == null) throw new GHttpAuthException(getErrorResponse(401, "Authorization required"));
      CamdNetMessage request = CamdNetMessage.parseGHttpReq(s, req.getRemoteAddress(), null);
      CamdNetMessage reply = waitForReply(request);
      HttpResponse res;
      if(reply == null) {
        res = getErrorResponse(503, "Cache timeout");
      } else {
        res = new HttpResponse(200, "OK", reply.getCustomData(), "application/octet-stream");
      }
      if(s.length < 6) res.setCookie("GSSID", gs.getGhttpSessionId());
      return res;
    } catch (GHttpAuthException e) {
      return e.getResponse();
    } catch (Exception e) {
      parent.logger.throwing("Bad ghttp cache request: " + req.getQueryString(), e);
      return HttpResponse.getErrorResponse(400, req.getQueryString());
    }
  }

  public HttpResponse doPost(String urlPattern, HttpRequest req) {
    if(!enabled) return getErrorResponse(403, "Disabled");
    else {
      String q = req.getQueryString();
      if(q.startsWith(API_ECM_POST)) {
        return doEcmPost(req);
      } else if(q.startsWith(API_CAPMT)) {
         return doPmtGetOrPost(req);
      } else if(q.startsWith(API_FEEDER_POST)) {
        // todo
      }
      return getErrorResponse(503, "Not implemented"); // todo
    }
  }

  private HttpResponse doEcmPost(HttpRequest req) {
    if(cache == null) cache = config.getCacheHandler();
    try {
      String[] s = req.getQueryString().split("/");
      GHttpSession gs = doGhttpAuth(req, s);
      if(gs == null) throw new GHttpAuthException(HttpResponse.getAuthReqResponse("GHttp"));
      CamdNetMessage ecmReq = CamdNetMessage.parseGHttpReq(s, req.getRemoteAddress(), req.getContent());

      CamdNetMessage reply = cache.peekReply(ecmReq);
      boolean instant = false;
      if(reply == null) {
        if(ProxyConfig.getInstance().getProfileById(ecmReq.getNetworkId(), ecmReq.getCaId()) == null)
          return getErrorResponse(503, "Unknown system: " + CaProfile.getKeyStr(ecmReq.getNetworkId(), ecmReq.getCaId()));
        gs.fireCamdMessage(ecmReq, false);
        reply = gs.waitForReply(ecmReq);
      } else instant = true;

      if(reply == null) return getErrorResponse(503, "Ecm timeout");
      else {
        CaContext cc = new CaContext(ecmReq.getNetworkId(), ecmReq.getTid(), ecmReq.getServiceId());
        cc = (CaContext)contexts.get(cc.toString());
        if(cc != null) cc.addLength(ecmReq.getPid(), ecmReq.getDataLength());
        HttpResponse res = new HttpResponse(200, "OK", reply.getCustomData(), "application/octet-stream");
        if(instant) res.setHeader("Pragma", "Cached");
        if(s.length < 10) res.setCookie("GSSID", gs.getGhttpSessionId());
        return res;
      }
    } catch (GHttpAuthException e) {
      return e.getResponse();
    } catch (Exception e) {
      parent.logger.throwing("Bad ghttp ecm request: " + req.getQueryString(), e);
      return HttpResponse.getErrorResponse(400, req.getQueryString());
    }
  }

  private HttpResponse doPmtGetOrPost(HttpRequest req) {
    try {
      String[] s = req.getQueryString().split("/");
      GHttpSession gs = doGhttpAuth(req, s);
      if(gs == null) throw new GHttpAuthException(HttpResponse.getAuthReqResponse("GHttp"));
      int offs = (s[3].length() >= 6)?4:3;

      CaContext cc = new CaContext();
      cc.networkId = Integer.parseInt(s[offs++], 16);
      cc.tsId = Integer.parseInt(s[offs++], 16);
      cc.serviceId = Integer.parseInt(s[offs++], 16);
      int pidCount = Integer.parseInt(s[offs++], 16);
      cc.nameSpace = 0;
      if(s.length > offs) cc.nameSpace = Long.parseLong(s[offs], 16);

      String key = cc.toString();
      if(contexts.containsKey(key)) cc = (CaContext)contexts.get(key);
      else contexts.put(key, cc);

      EcmPid pid;
      if(pidCount > 0) {
        if(!"POST".equalsIgnoreCase(req.getMethod()))
          return HttpResponse.getErrorResponse(400, req.getQueryString() + " (expected post)");
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(req.getContent()));
        for(int i = 0; i < pidCount; i++) {
          pid = new EcmPid();
          pid.pid = dis.readUnsignedShort();
          pid.caId = dis.readUnsignedShort();
          pid.provId = dis.readInt();
          cc.addPid(pid);
        }
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream daos = new DataOutputStream(baos);
      for(Iterator iter = cc.iterator(); iter.hasNext();) {
        pid = (EcmPid)iter.next();
        if(config.getProfileById(cc.networkId, pid.caId) == null)
          daos.writeShort(pid.pid);
      }
      HttpResponse res;
      byte[] pids = baos.toByteArray();
      if(pids.length == 0) res = new HttpResponse(204, "No Content");
      else res = new HttpResponse(200, "OK", pids, "application/octet-stream");
      if(s[3].length() < 6) res.setCookie("GSSID", gs.getGhttpSessionId());
      return res;

    } catch (GHttpAuthException e) {
      return e.getResponse();
    } catch (Exception e) {
      parent.logger.throwing("Bad ghttp pmt request: " + req.getQueryString(), e);
      return HttpResponse.getErrorResponse(400, req.getQueryString());
    }
  }

  public HttpResponse doConnect(String urlPattern, HttpRequest req) {
    return HttpResponse.getErrorResponse(405, req.getMethod());
  }

  private HttpResponse getErrorResponse(int code, String msg) {
    HttpResponse error = HttpResponse.getErrorResponse(code);
    error.setContent(msg.getBytes(), "application/octet-stream");
    return error;
  }

  static class GHttpAuthException extends Exception {
    HttpResponse response;

    GHttpAuthException(HttpResponse response) {
      this.response = response;
    }

    public HttpResponse getResponse() {
      return response;
    }
  }

  static class CaContext {
    int networkId, tsId, serviceId;
    long nameSpace;

    CaContext() {}

    CaContext(int networkId, int tsId, int serviceId) {
      this.networkId = networkId;
      this.tsId = tsId;
      this.serviceId = serviceId;
    }

    private Map ecmPids = new LinkedHashMap();

    void addPid(EcmPid pid) {
      ecmPids.put(String.valueOf(pid.pid), pid);
    }

    void addLength(int pid, int len) {
      EcmPid ecmPid = (EcmPid)ecmPids.get(String.valueOf(pid));
      if(ecmPid != null) ecmPid.addLength(len);
    }

    Iterator iterator() {
      return ecmPids.values().iterator();
    }

    public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;
      CaContext caContext = (CaContext)o;
      // if(nameSpace != caContext.nameSpace) return false;
      if(networkId != caContext.networkId) return false;
      if(serviceId != caContext.serviceId) return false;
      if(tsId != caContext.tsId) return false;
      return true;
    }

    public int hashCode() {
      int result = networkId;
      result = 31 * result + tsId;
      result = 31 * result + serviceId;
      // result = 31 * result + (int)(nameSpace ^ (nameSpace >>> 32));
      return result;
    }

    public String toString() {
      StringBuffer sb = new StringBuffer();
      sb.append(Integer.toHexString(networkId)).append('-');
      sb.append(Integer.toHexString(tsId)).append('-');
      sb.append(Integer.toHexString(serviceId));
      // sb.append(Long.toHexString(nameSpace));
      return sb.toString();
    }
  }

  static class EcmPid {
    int pid, caId, provId;

    private Set lengths = new HashSet();

    void addLength(int len) {
      lengths.add(new Integer(len));
    }

    String getLengths() {
      return lengths.toString();
    }

  }
}
