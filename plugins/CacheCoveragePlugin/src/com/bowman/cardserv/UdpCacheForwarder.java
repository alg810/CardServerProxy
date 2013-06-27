package com.bowman.cardserv;

import com.bowman.cardserv.util.*;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2012-02-11
 * Time: 11:38
 */
public class UdpCacheForwarder implements CacheForwarder {

  private final String name;
  private final CacheCoveragePlugin parent;

  private DatagramSocket sendSock;
  private InetAddress host;
  private int port;
  private Set profiles, caids;
  private boolean sendLocks, hideNames;

  private int requests, replies, filtered;
  private TimedAverageList sentAvg = new TimedAverageList(10);

  public UdpCacheForwarder(CacheCoveragePlugin parent, String name) {
    this.parent = parent;
    this.name = name;
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    if("true".equalsIgnoreCase(xml.getStringValue("enabled", "true"))) {
      try {
        host = InetAddress.getByName(xml.getStringValue("host"));
      } catch(UnknownHostException e) {
        throw new ConfigException(xml.getFullName(), "host", "Unable to resolve host: " + xml.getStringValue("host"), e);
      }
      port = xml.getPortValue("port");

      sendLocks = "true".equalsIgnoreCase(xml.getStringValue("send-locks", "true"));
      hideNames = "true".equalsIgnoreCase(xml.getStringValue("hide-names", "true"));

      String profileStr = xml.getStringValue("profiles", "").trim().toLowerCase();
      if(profileStr.length() > 0) profiles = new HashSet(Arrays.asList(profileStr.split(" ")));
      else profiles = null;
      String caidStr = xml.getStringValue("caids", "").trim();
      if(caidStr.length() > 0) caids = ProxyXmlConfig.getIntTokens("caids", caidStr);
      else caids = null;

      try {
        sendSock = new DatagramSocket();
      } catch(SocketException e) {
        throw new ConfigException(xml.getFullName(), "Unable to create udp forwarder socket: " + e, e);
      }
    } else close();
  }

  public String getName() {
    return name;
  }

  public boolean isConnected() {
    return sendSock != null;
  }

  public Properties getProperties() {
    Properties p = new Properties();
    p.setProperty("lock-count", String.valueOf(requests));
    p.setProperty("msg-count", String.valueOf(replies));
    p.setProperty("filtered", String.valueOf(filtered));
    p.setProperty("avg-sent-rate", String.valueOf(sentAvg.getTotal(true) / 10));
    return p;
  }

  public void close() {
    sendSock = null;
  }

  public void forwardRequest(CamdNetMessage req) {
    if(!sendLocks) return;
    if(isFiltered(req)) {
      filtered++;
      return;
    }
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(bos);
    try {
      dos.writeByte(ClusteredCache.TYPE_REQUEST);
      ClusteredCache.writeCacheReq(dos, req, false);
      dos.close();
      byte[] buf = bos.toByteArray();
      DatagramPacket packet = new DatagramPacket(buf, buf.length, host, port);
      sendSock.send(packet);
      sentAvg.addRecord(packet.getLength());
      requests++;
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void forwardReply(CamdNetMessage req, CamdNetMessage reply) {
    if(reply.getDataLength() == 16) {
      if(isFiltered(req)) {
        filtered++;
        return;
      }
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(bos);
      try {
        dos.writeByte(ClusteredCache.TYPE_REPLY);
        ClusteredCache.writeCacheReq(dos, req, false);
        ClusteredCache.writeCacheRpl(dos, reply, !hideNames);
        dos.close();
        byte[] buf = bos.toByteArray();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, host, port);
        sendSock.send(packet);
        sentAvg.addRecord(packet.getLength());
        replies++;
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public boolean isFiltered(CamdNetMessage req) {
    if(profiles != null)
      if(req.getProfileName() == null || !profiles.contains(req.getProfileName().toLowerCase())) return true;
    if(caids != null)
      if(!caids.contains(new Integer(req.getCaId()))) return true;
    return false;
  }

}
