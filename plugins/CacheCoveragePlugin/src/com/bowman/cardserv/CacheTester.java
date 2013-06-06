package com.bowman.cardserv;

import com.bowman.cardserv.session.CacheDummySession;
import com.bowman.cardserv.web.FileFetcher;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2011-02-11
 * Time: 05:15
 */
public class CacheTester implements Runnable {

  private final CacheCoveragePlugin parent;
  private Set testHosts = new HashSet();
  private int testPort = -1;
  private Thread readerThread;
  private DatagramSocket recvSock, sendSock;
  private CacheDummySession dummy = new CacheDummySession();
  private ProxyConfig config;

  public CacheTester(CacheCoveragePlugin parent) {
    this.parent = parent;
    String hostStr = FileFetcher.getProperty("cache.bl");
    String portStr = FileFetcher.getProperty("cache.bl.port");

    if(hostStr != null) testHosts.addAll(Arrays.asList(hostStr.split(" ")));
    if(portStr != null) testPort = Integer.parseInt(portStr);

    if(testPort != -1) {
      try {
        sendSock = new DatagramSocket();
        recvSock = new DatagramSocket(testPort);
        config = ProxyConfig.getInstance();
        readerThread = new Thread(this, "CacheCoverageTestThread");
        readerThread.start();
      } catch(SocketException e) {
        e.printStackTrace();
      }
    }

  }

  public void run() {
    while(Thread.currentThread() == readerThread) {
      DatagramPacket packet;
      try {
        byte[] buf = new byte[1024];
        packet = new DatagramPacket(buf, buf.length);
        recvSock.receive(packet);

        if(!parent.proxy.isAlive()) continue;

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(packet.getData(), 0, packet.getLength()));
        CamdNetMessage req = CamdNetMessage.parseGHttpReq(dis, null, true);
        if(config.getProfileById(req.getNetworkId(), req.getCaId()) != null)
          parent.proxy.messageReceived(dummy, req);

      } catch (Exception e) {
        e.printStackTrace();
      }
    }

  }

  public boolean isAlive() {
    return readerThread != null;
  }

  public void testMessage(CamdNetMessage req) {
    if(testPort == -1 || testHosts.isEmpty()) return;
    if(config.getProfileById(req.getNetworkId(), req.getCaId()) == null) {
      // System.out.println("Ignoring: " + req.toDebugString());
      return;
    }
    // System.out.println("Fallback: " + req.toDebugString());
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream daos = new DataOutputStream(baos);
    try {
      writeTestReq(req, daos, true);
    } catch(IOException e) {
      e.printStackTrace();
    }
    byte[] buf = baos.toByteArray();
    for(Iterator iter = testHosts.iterator(); iter.hasNext(); ) {
      try {
        DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName((String)iter.next()), testPort);
        sendSock.send(packet);
      } catch(IOException e) {
        e.printStackTrace();
      }
    }
  }

  private static void writeTestReq(CamdNetMessage req, DataOutputStream dos, boolean full) throws IOException {
    dos.writeByte(req.getCommandTag());
    if(full) {
      dos.writeShort(req.getNetworkId());
      dos.writeShort(req.getTid());
      dos.writeShort(req.getPid());
    }
    dos.writeShort(req.getCaId());
    dos.writeInt(req.getProviderIdent());
    dos.writeShort(req.getServiceId());
    dos.writeShort(req.getDataLength());
    dos.write(req.getCustomData());
  }

  public void stop() {
    readerThread = null;
    if(recvSock != null) recvSock.close();
  }
}
