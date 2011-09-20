package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.session.CacheDummySession;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.web.FileFetcher;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2011-07-08
 * Time: 04:01
 */
public class CacheForwarder implements XmlConfigurable, GHttpConstants {

  private static final int RETRY_WAIT = 2000;
  private static final int RECORD_SIZE = 28, MAX_QSIZE = 2000, INSTANCE_MAXAGE = 60 * 20 * 1000;
  private static final int CONNECT_TIMEOUT = 10 * 1000;
  private static final String CRLF = "" + (char)0x0D + (char)0x0A;

  private FeederThread[] threads = new FeederThread[2];
  private TimedAverageList latency = new TimedAverageList(ServiceCacheEntry.WINDOW_SIZE);
  private TimedAverageList msgSize = new TimedAverageList(ServiceCacheEntry.WINDOW_SIZE);
  private Map remoteInstances = Collections.synchronizedMap(new HashMap());
  private final List singleQ = Collections.synchronizedList(new ArrayList());

  private final String name;
  private final CacheCoveragePlugin parent;

  private boolean ssl;
  private String host, prefix, passwd;
  private int port;
  private boolean connected, redundant;
  private int counter, reconnects, errors, ecmForwards, delayAlerts, filtered;
  private int maxDelay;
  private Set profiles;

  private long bytesOut, bytesIn;
  private TimedAverageList sentAvg = new TimedAverageList(10), recvAvg = new TimedAverageList(10);

  private CacheDummySession dummySession = new CacheDummySession(this);

  public CacheForwarder(CacheCoveragePlugin parent, String name) {
    this.parent = parent;
    this.name = name;
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    String urlStr = xml.getStringValue("url");
    try {
      URL url = new URL(urlStr);
      if(!url.getProtocol().startsWith("http")) throw new ConfigException(xml.getFullName(), "url", "Non-http url: " + urlStr);
      ssl = "https".equals(url.getProtocol());
      host = url.getHost();
      port = url.getPort();
      if(port == -1) port = ssl?443:80;
      prefix = url.getPath();
      if(prefix == null) prefix = "";
      if(!prefix.endsWith("/")) prefix += "/";
    } catch(MalformedURLException e) {
      throw new ConfigException(xml.getFullName(), "url", "Malformed url: " + urlStr);
    }
    if("true".equalsIgnoreCase(xml.getStringValue("enabled", "true"))) {
      passwd = xml.getStringValue("password");
      redundant = "true".equalsIgnoreCase(xml.getStringValue("redundant-forwarding", "false"));
      maxDelay = xml.getTimeValue("max-delay", 200, "ms");
      String profileStr = xml.getStringValue("profiles", "").trim().toLowerCase();
      if(profileStr.length() > 0) profiles = new HashSet(Arrays.asList(profileStr.split(" ")));
      else profiles = null;

      for(int i = 0; i < threads.length; i++) {
        threads[i] = new FeederThread(i);
        threads[i].start();
      }
    } else close();
  }

  public String getName() {
    return name;
  }

  public boolean isConnected() {
    return connected;
  }

  public int getAvgLatency() {
    return latency.getAverage(true);
  }

  public int getPeakLatency() {
    if(redundant) return Math.min(threads[0].peakLatency, threads[1].peakLatency);
    else return Math.max(threads[0].peakLatency, threads[1].peakLatency);
  }

  public int getAvgMsgSize() {
    return msgSize.getAverage(true);
  }

  public int getPeakMsgSize() {
    return msgSize.getMaxValue();
  }

  public int getCount() {
    return counter;
  }

  public int getReconnects() {
    return reconnects;
  }

  public int getErrors() {
    return errors;
  }

  public int getSendQSize() {
    if(redundant) return threads[0].localQ.size() + threads[1].localQ.size();
    else return singleQ.size();
  }

  public int getMaxDelay() {
    return maxDelay;
  }

  public long getSentAvg() {
    return sentAvg.getTotal(true) / 10;
  }

  public long getRecvAvg() {
    return recvAvg.getTotal(true) / 10;
  }

  public int getEcmForwards() {
    return ecmForwards;
  }

  public int getDelayAlerts() {
    return delayAlerts;
  }

  public int getFiltered() {
    return filtered;
  }

  public Map getRemoteInstances() {
    String instance; Properties p; long timeStamp, now = System.currentTimeMillis();
    for(Iterator iter = remoteInstances.keySet().iterator(); iter.hasNext(); ) {
      instance = (String)iter.next();
      p = (Properties)remoteInstances.get(instance);
      timeStamp = Long.parseLong(p.getProperty("tstamp"));
      if(now - timeStamp > INSTANCE_MAXAGE) iter.remove();
    }
    return remoteInstances;
  }

  private void sentBytes(int count) {
    bytesOut += count;
    sentAvg.addRecord(count);
  }

  private void recvBytes(int count) {
    bytesIn += count;
    recvAvg.addRecord(count);
  }

  public void close() {
    if(threads != null) {
      for(int i = 0; i < threads.length; i++)
        if(threads[i] != null) threads[i].interrupt();
      threads = null;
    }
    connected = false;
  }

  public void forwardReply(CamdNetMessage req, CamdNetMessage reply) {
    if(threads != null && connected) {
      if(reply.getDataLength() == 16) {
        if(profiles != null)
          if(req.getProfileName() == null || !profiles.contains(req.getProfileName().toLowerCase())) {
            filtered++;
            return;
          }
        if(redundant) forwardRedundant(req, reply);
        else {
          synchronized(singleQ) {
            if(singleQ.size() > MAX_QSIZE) {
              singleQ.clear();
              parent.logger.warning("CacheForwarder[" + name + "] discarding sendQ, no working connections?");
            }
            singleQ.add(new RequestReplyPair(req, reply));
            singleQ.notifyAll();
          }
        }
      }
    }
  }

  private void forwardRedundant(CamdNetMessage req, CamdNetMessage reply) {
    for(int i = 0; i < threads.length; i++) { // individual queues for each sender thread, duplicate traffic but lower latency
      synchronized(threads[i].localQ) {
        if(threads[i].localQ.size() > MAX_QSIZE) {
          threads[i].localQ.clear();
          parent.logger.warning("CacheForwarder[" + name + "] discarding sendQ, no working connections?");
        }
        threads[i].localQ.add(new RequestReplyPair(req, reply));
        threads[i].localQ.notifyAll();
      }
    }
  }

  private static class RequestReplyPair {
    CamdNetMessage request, reply;

    private RequestReplyPair(CamdNetMessage request, CamdNetMessage reply) {
      this.request = request;
      this.reply = reply;
    }
  }

  private static class HttpReply {
    int code, hdrSize;
    String msg;
    Properties headers = new Properties(), cookies = new Properties();
    char[] body;
    boolean headerEnd;

    private HttpReply(String line) {
      hdrSize += line.length() + 2;
      String[] s = line.split(" ");
      code = Integer.parseInt(s[1]);
      msg = s[2];
    }

    void addHeader(String line) {
      hdrSize += line.length() + 2;
      if("".equals(line)) headerEnd = true;
      else {
        String[] s = line.split(": ");
        headers.setProperty(s[0], s[1]);
        if(s[0].equals("Set-Cookie")) {
          s = s[1].split("=");
          cookies.setProperty(s[0], s[1]);
        }
      }
    }

    String getCookie(String name) {
      return cookies.getProperty(name);
    }

    String getHeader(String name) {
      return headers.getProperty(name);
    }

    int getContentLength() {
      return Integer.parseInt(headers.getProperty("Content-Length", "-1"));
    }

    String getContentType() {
      return headers.getProperty("Content-Type");
    }

    String getContentAsString() {
      return new String(body);
    }

    SimpleTlvBlob getContentAsTlv() {
      try {
        return new SimpleTlvBlob(getContentAsString().getBytes("ISO-8859-1"));
      } catch(UnsupportedEncodingException e) {
        e.printStackTrace();
        return null;
      }
    }

    int getSize() {
      return hdrSize + body.length;
    }
  }

  private class FeederThread extends Thread {

    private final List localQ = Collections.synchronizedList(new ArrayList());

    private Socket conn;
    private DataOutputStream dos;
    private BufferedReader br;

    private String sessionId;
    protected int peakLatency;

    public FeederThread(int i) {
      super("GHttpFeederThread-" + i);
    }

    private void initConn() throws IOException {
      conn = ssl?FileFetcher.socketFactory.createSocket():new Socket();
      conn.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT);
      dos = new DataOutputStream(new BufferedOutputStream(conn.getOutputStream()));
      br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "ISO-8859-1"));
      connected = true;
    }

    private void httpPost(List sendQ) throws IOException {
      counter++;
      long start = System.currentTimeMillis();
      int size = dos.size();

      String path = "api/f/" + (sessionId == null?"":sessionId);
      dos.writeBytes("POST " + prefix + path + " HTTP/1.1" + CRLF);
      dos.writeBytes("Host: " + host + CRLF);
      dos.writeBytes("Content-Length: " + (RECORD_SIZE * sendQ.size()) + CRLF);
      if(sessionId == null) {
        String auth = Integer.toHexString(ProxyConfig.getInstance().getProxyOriginId()) + ":" + passwd;
        auth = new String(com.bowman.util.Base64Encoder.encode(auth.getBytes("ISO-8859-1")));
        dos.writeBytes("Authorization: Basic " + auth + CRLF);
      }
      dos.writeBytes(CRLF);
      msgSize.addRecord(sendQ.size());

      RequestReplyPair pair;
      for(Iterator iter = sendQ.iterator(); iter.hasNext(); ) {
        pair = (RequestReplyPair)iter.next();
        ClusteredCache.writeCacheReq(dos, pair.request, false);
        ClusteredCache.writeCacheRpl(dos, pair.reply, false);
      }
      dos.flush();
      sentBytes(dos.size() - size);

      String resp = br.readLine();
      if(resp == null) {
        parent.logger.warning("CacheForwarder[" + name + "] connection closed after post.");
        handleError(sendQ, true);
        return;
      }

      HttpReply reply = new HttpReply(resp);

      do { // read remaining header
        reply.addHeader(br.readLine());
      } while(!reply.headerEnd);

      // read body
      if("chunked".equalsIgnoreCase(reply.getHeader("Transfer-Encoding"))) {
        StringBuffer sb = new StringBuffer(); String line;
        do {
          line = br.readLine();
          sb.append(line).append(CRLF);
        } while(!"".equals(line));
        reply.body = sb.toString().toCharArray();
      } else {
        reply.body = new char[reply.getContentLength()];
        if(br.read(reply.body) != reply.body.length) throw new IOException("Assertation failed");
      }

      recvBytes(reply.getSize());

      String sessionCookie = reply.getCookie("GSSID");
      if(sessionCookie != null) sessionId = sessionCookie;

      switch(reply.code) {
        case 200:
        case 204:
          sendQ.clear();
          int time = (int)(System.currentTimeMillis() - start);
          if(time > peakLatency) peakLatency = time;
          latency.addRecord(time);
          if(reply.body.length > 0) handleReply(reply.getContentAsTlv());
          break;
        case 401:
          parent.logger.warning("CacheForwarder[" + name + "] session expired.");
          sessionId = null;
          break;
        case 400:
          parent.logger.warning("CacheForwarder[" + name + "] received bad request error, disabling: " +
              reply.getContentAsString());
          close();
          return;
        case 403:
          parent.logger.warning("CacheForwarder[" + name + "] invalid password, disabling.");
          close();
          return;
        default: // all 5xx errors
          parent.logger.warning("CacheForwarder[" + name + "] received error reply: " + resp);
          handleError(sendQ, true);
          break;
      }
    }

    void handleReply(SimpleTlvBlob tb) {
      int key; String instance = null;
      for(Iterator iter = tb.keySet().iterator(); iter.hasNext(); ) {
        key = ((Integer)iter.next()).intValue();
        switch(key) {
          case T_INSTANCE_ID:
            instance = new String(tb.getSingle(key)); // always first
            break;
          case T_STAT_UPDATE:
            Properties p = new Properties();
            p.setProperty("tstamp", String.valueOf(System.currentTimeMillis()));
            int[] stats = tb.getIntArray(key);
            for(int i = 0; i < stats.length; i++) p.setProperty(STAT_KEYS[i], String.valueOf(stats[i]));
            if(remoteInstances.put(instance, p) == null) {
              parent.logger.fine("CacheForwarder[" + name + "] encountered new remote instance: " + instance +
                  " (" + getRemoteInstances().size() + ")");
            }
            break;
          case T_ECM_REQ:
            List ecms = tb.get(key);
            parent.logger.fine("CacheForwarder[" + name + "] received " + ecms.size() + " ecm requests...");
            CamdNetMessage ecmReq;
            for(Iterator i = ecms.iterator(); i.hasNext(); ) {
              try {
                ecmReq = CamdNetMessage.parseGHttpReq(new DataInputStream(new ByteArrayInputStream((byte[])i.next())),
                    conn.getInetAddress().getHostAddress(), false);
                ecmReq.setNetworkId(0xa027); // todo
                System.out.println(ecmReq.hashCodeStr());
                parent.proxy.messageReceived(dummySession, ecmReq);
                ecmForwards++;
              } catch(IOException e) {
                parent.logger.throwing(e);
              }
            }
            break;
          case T_CACHE_MISS:
            if(parent.cache instanceof ClusteredCache) {
              List delayed = tb.get(key);
              parent.logger.fine("CacheForwarder[" + name + "] received " + delayed.size() + " cache requests for resend...");
              CamdNetMessage req;
              for(Iterator i = delayed.iterator(); i.hasNext(); ) {
                try {
                  req = CamdNetMessage.parseCacheReq(new DataInputStream(new ByteArrayInputStream((byte[])i.next())), false);
                  ((ClusteredCache)parent.cache).delayAlert(-1, req, false, -1);
                  delayAlerts++;
                } catch(IOException e) {
                  parent.logger.throwing(e);
                }
              }
            }
            break;
          default:
            parent.logger.fine("CacheForwarder[" + name + "] received unknown TLV field in reply: " + key);
            break;
        }

      }
    }

    public void run() {
      List myQ = new ArrayList();

      while(threads != null) {
        try {
          if(conn == null) initConn();
          if(redundant) {
            synchronized(localQ) { // fetch from local
              while(localQ.isEmpty()) {
                localQ.wait();
              }
              if(!localQ.isEmpty()) {
                myQ.addAll(localQ);
                localQ.clear();
              }
            }
          } else {
            synchronized(singleQ) { // fetch from shared
              while(singleQ.isEmpty()) {
                singleQ.wait();
              }
              if(!singleQ.isEmpty()) {
                myQ.addAll(singleQ);
                singleQ.clear();
              }
            }
          }
          if(!myQ.isEmpty()) {
            try {
              httpPost(myQ);
              if(maxDelay > 0) Thread.sleep(maxDelay);
            } catch (SocketException se) {
              // socket was closed gracefully, reconnect immediately
              parent.logger.throwing(se);
              initConn();
              reconnects++;
            }
          }
        } catch(IOException e) { // abnormal disconnect
          parent.logger.warning("CacheForwarder[" + name + "] disconnected: " + e);
          parent.logger.throwing(e);
          e.printStackTrace();
          handleError(myQ, false);
        } catch(InterruptedException e) {
          return;
        }
      }
    }

    private void handleError(List sendQ, boolean disconnect) {
      if(!sendQ.isEmpty()) {
        localQ.addAll(sendQ);
        sendQ.clear();
      }
      errors++;
      if(disconnect) disconnect();
      else conn = null;
      try {
        Thread.sleep(RETRY_WAIT);
      } catch(InterruptedException ie) {
        ie.printStackTrace();
      }
    }

    public void disconnect() {
      if(conn != null) {
        try {
          conn.close();
        } catch(IOException e) {
          // ignore
        }
        conn = null;
      }
    }

    public void interrupt() {
      disconnect();
      super.interrupt();
    }
  }

}
