package com.bowman.cardserv;

import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.interfaces.XmlConfigurable;
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
public class CacheForwarder implements XmlConfigurable {

  private static final int RECORD_SIZE = 28;
  private static final int CONNECT_TIMEOUT = 10 * 1000;
  private static final String CRLF = "" + (char)0x0D + (char)0x0A;

  private final List outQ = Collections.synchronizedList(new ArrayList());
  private FeederThread[] threads = new FeederThread[2];
  private TimedAverageList latency = new TimedAverageList(ServiceCacheEntry.WINDOW_SIZE);
  private TimedAverageList recordSize = new TimedAverageList(ServiceCacheEntry.WINDOW_SIZE);

  private final String name;
  private final CacheCoveragePlugin parent;

  private boolean ssl;
  private String host, prefix;
  private int port;
  private boolean connected;
  private int counter, reconnects, errors;

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
    return latency.getMaxValue();
  }

  public int getAvgRecordSize() {
    return recordSize.getAverage(true);
  }

  public int getPeakRecordSize() {
    return recordSize.getMaxValue();
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

  public void close() {
    if(threads != null) {
      for(int i = 0; i < threads.length; i++)
        if(threads[i] != null) threads[i].interrupt();
      threads = null;
    }
  }

  public void forwardReply(CamdNetMessage req, CamdNetMessage reply) {
    if(reply.getDataLength() == 16) {
      synchronized(outQ) {
        outQ.add(new RequestReplyPair(req, reply));
        outQ.notifyAll();
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

  private class FeederThread extends Thread {

    private Socket conn;
    private DataOutputStream dos;
    private BufferedReader br;

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
      long now = System.currentTimeMillis();
      dos.writeBytes("POST " + prefix + "api/f/ HTTP/1.1" + CRLF);
      dos.writeBytes("Host: " + host + CRLF);
      dos.writeBytes("Content-Length: " + (RECORD_SIZE * sendQ.size()) + CRLF);
      // dos.writeBytes("Connection: Keep-Alive" + CRLF + CRLF);// seems there's no need to actually request keep-alive?
      dos.writeBytes(CRLF);
      recordSize.addRecord(sendQ.size());
      RequestReplyPair pair; // int i = 0;
      for(Iterator iter = sendQ.iterator(); iter.hasNext(); ) {
        pair = (RequestReplyPair)iter.next();
        ClusteredCache.writeCacheReq(dos, pair.request, false);
        ClusteredCache.writeCacheRpl(dos, pair.reply, false);
        iter.remove();
        // if(i >= 3) System.out.println(pair.request + " - " + pair.reply);
        // i++;
      }
      dos.flush();
      String resp = br.readLine();
      if(!resp.endsWith("200 OK")) {
        parent.logger.warning("CacheForwarder[" + name + "] received error reply: " + resp);
        errors++;
        close(); // todo handle failures
        return;
      }
      latency.addRecord(now, (int)(System.currentTimeMillis() - now));
      String line;
      do { // read remaining header
        line = br.readLine();
        // System.out.println(counter + "\t" + line);
      } while(!"".equals(line));
      do { // read body, always chunked with gae?
        line = br.readLine();
        // System.out.println(counter + "\t" + line);
      } while(!"".equals(line));
    }

    public void run() {
      List myQ = new ArrayList();

      while(threads != null) {
        try {
          if(conn == null) initConn();
          synchronized(outQ) {
            while(outQ.isEmpty()) {
              outQ.wait();
            }
            if(!outQ.isEmpty()) {
              myQ.addAll(outQ);
              outQ.clear();
            }
          }
          if(!myQ.isEmpty()) {
            try {
              httpPost(myQ);
            } catch (SocketException se) {
              // socket was closed, reconnect immediately
              parent.logger.throwing(se);
              initConn();
              reconnects++;
            }
          }
        } catch(IOException e) {
          parent.logger.warning("CacheForwarder disconnected: " + e);
          parent.logger.throwing(e);
          e.printStackTrace();
          conn = null;
          connected = false;
          errors++;
          // todo retry wait
        } catch(InterruptedException e) {
          return;
        }
      }
    }

    public void interrupt() {
      if(conn != null) {
        try {
          conn.close();
        } catch(IOException e) {
          // ignore
        }
        conn = null;
      }
      super.interrupt();
    }
  }

}
