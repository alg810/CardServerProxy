package com.bowman.cardserv;

import com.bowman.cardserv.util.*;
import com.bowman.cardserv.web.*;
import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.session.*;
import com.bowman.cardserv.interfaces.*;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2006-mar-02
 * Time: 03:32:47
 */
public class ClusteredCache extends DefaultCache implements Runnable, StaleEntryListener {

  private static final int TYPE_REQUEST = 1, TYPE_REPLY = 2, TYPE_PINGREQ = 3, TYPE_PINGRPL = 4;
  private static final int TYPE_RESENDREQ = 5;

  private InetAddress mcGroup;
  private String trackerKey, localHost;
  private URL trackerUrl;
  private Set peerList = new HashSet();
  private Map hostPings = new HashMap(), hostReceives = new HashMap();
  private long trackerInterval, syncPeriod, pingSent;
  private int remotePort, localPort;
  private byte ttl;
  private DatagramSocket recvSock, sendSock;
  private Thread clusterThread, trackerThread;

  private boolean useMulticast, debug, hideNames, autoAddPeers;

  private int trackerFailures, receivedEntries, receivedPending, receivedDiscarded;
  private int sentPending, sentEntries, reSentEntries, receivedResendReqs, sentResendReqs;

  private TimedAverageList sentAvg = new TimedAverageList(10), recvAvg = new TimedAverageList(10);
  private RequestArbiter arbiter = new RequestArbiter();

  private Set mismatchedHosts = new HashSet();
  private MessageCacheMap resendReqs;

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    super.configUpdated(xml);

    pendingEcms.setStaleEntryListener(this);
    mismatchedHosts.clear();
    if(resendReqs == null) resendReqs = new MessageCacheMap(maxAge);
    else resendReqs.setMaxAge(maxAge);

    InetAddress remoteCache;

    try {
      String host = xml.getStringValue("remote-host");
      try {
        remoteCache = InetAddress.getByName(host);
      } catch(UnknownHostException e) {
        throw new ConfigException(xml.getFullName(), "remote-host", "Unable to resolve '" + host + "': " + e.getMessage());
      }
    } catch (ConfigException e) {
      remoteCache = null;
    }

    debug = "true".equalsIgnoreCase(xml.getStringValue("debug", "false"));
    if(debug) logger.warning("Cache debug mode enabled (will fail sooner or later under any significant traffic).");

    hideNames = "true".equalsIgnoreCase(xml.getStringValue("hide-names", "false"));
    autoAddPeers = "true".equalsIgnoreCase(xml.getStringValue("auto-add-peers", "false"));

    syncPeriod = xml.getTimeValue("sync-period", 0, "ms");
    if(syncPeriod > 0) logger.info("Strict cache-synchronization is enabled (sync-period is: " + syncPeriod + " ms).");

    useMulticast = false;
    try {
      String mcHost = xml.getStringValue("multicast-group");
      try {
        mcGroup = InetAddress.getByName(mcHost);
        useMulticast = true;
      } catch(UnknownHostException e) {
        throw new ConfigException(xml.getFullName(), "multicast-group", "Unable to resolve '" + mcHost + "': " + e.getMessage());
      }
    } catch (ConfigException e) {}

    ttl = (byte)xml.getIntValue("multicast-ttl", 2);
    remotePort = xml.getPortValue("remote-port", -1);

    if(!useMulticast)
      if(remotePort != -1 && remoteCache != null) {
        peerList.clear();
        peerList.add(new CachePeer(remoteCache, remotePort));
      }

    trackerUrl = null; trackerKey = null; trackerInterval = 0;
    if(!useMulticast) {
      String trackerUrlStr = null;
      try {
        trackerUrlStr = xml.getStringValue("tracker-url");
        trackerKey = xml.getStringValue("tracker-key");
      } catch (ConfigException e) {
      }
      if(trackerUrlStr != null) {
        try {
          trackerUrl = new URL(trackerUrlStr);
        } catch(MalformedURLException e) {
          throw new ConfigException(xml.getFullName(), "tracker-url", "Malformed URL: " + trackerUrlStr);
        }
        trackerInterval = xml.getTimeValue("tracker-update", 0, "m");
        if(trackerInterval != 0)
          if(trackerInterval < 60000) trackerInterval = 60000;
        try {
          localHost = xml.getStringValue("local-host");
        } catch (ConfigException e) {
          try {
            InetAddress lh = InetAddress.getLocalHost();
            localHost = lh.getHostAddress();
          } catch(UnknownHostException e1) {
          }
        }
      }
    }

    if(remotePort != -1)
      if(remoteCache == null && trackerUrl == null && mcGroup == null)
        throw new ConfigException(xml.getFullName(), "Either remote-host, tracker-url or multicast-group must be present when remote-port is set.");

    localPort = xml.getPortValue("local-port");

    if(recvSock == null) {
      try {
        sendSock = useMulticast?new MulticastSocket():new DatagramSocket();
        recvSock = useMulticast?new MulticastSocket(localPort):new DatagramSocket(localPort);
      } catch(IOException e) {
        throw new ConfigException(xml.getFullName(), "local-port", "Unable to open udp socket on port " + localPort + 
            ": " + e.getMessage());
      }

      if(useMulticast) {
        peerList.clear();
        try {
          ((MulticastSocket)recvSock).joinGroup(mcGroup);
        } catch(IOException e) {
          throw new ConfigException(xml.getFullName(), "Unable to join multicast-group '" + mcGroup.getHostAddress() +
              "': " + e.getMessage());
        }
      }
    }

    if(trackerUrl != null) {
      try {
        setPeerList(fetchList());
        logger.info("Fetched peer list from tracker (" + trackerUrl + "): " + peerList.size() + " entries");
        logger.fine("Peers: " + peerList);
      } catch(IOException e) {
        throw new ConfigException(xml.getFullName(), "tracker-url",
            "Failed to get list of peers from tracker url (" + trackerUrl + "): " + e, e);
      }
    }

    registerCtrlCommands();
  }

  public int getLocalPort() {
    return localPort;
  }

  protected void registerCtrlCommands() {
    CtrlCommand cmd;
    try {
      new CtrlCommand("toggle-debug", "Toggle debug", "Turn debugging on/off.").register(this);

      cmd = new CtrlCommand("add-peer", "Add cache peer", "Temporarily add a cache peer (until tracker/cfg update).");
      cmd.addParam("host", "Host");
      cmd.addParam("port", "Port");
      cmd.register(this);

      cmd = new CtrlCommand("remove-peer", "Remove cache peer", "Temporarily remove a cache peer.");
      cmd.addParam("peer", "").setOptions(peerList, false);
      cmd.register(this);

      if(trackerUrl != null)
        new CtrlCommand("update", "Run tracker update", "Fetch the tracker list now.").register(this);

    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }
  }

  public CtrlCommandResult runCtrlCmdToggleDebug() {
    debug = !debug;
    return new CtrlCommandResult(true, "Debugging is now " + (debug?"on.":"off."));
  }

  public CtrlCommandResult runCtrlCmdUpdate() {
    try {
      setPeerList(fetchList());
    } catch (IOException e) {
      return new CtrlCommandResult(false, "Updated failed: " + e);
    }
    return new CtrlCommandResult(true, "Update completed.");
  }

  public CtrlCommandResult runCtrlCmdAddPeer(Map params) {
    boolean result = false;
    String resultMsg;
    String host = (String)params.get("host");
    String port = (String)params.get("port");
    if(host == null) resultMsg = "Missing parameter: host";
    else if(port == null) resultMsg = "Missing parameter: port";
    else {
      try {
        int p = Integer.parseInt(port);
        if(p < 1 || p >= 0xFFFF) throw new NumberFormatException();
        InetAddress a = InetAddress.getByName(host);
        peerList.add(new CachePeer(a, p));
        resultMsg = "Cache peer added: " + a.getHostAddress() + ":" + p;
        result = true;
      } catch (NumberFormatException e) {
        resultMsg = "Bad port number: " + port;
      } catch (UnknownHostException e) {
        resultMsg = "Unknown host: " + host;
      }
    }
    return new CtrlCommandResult(result, resultMsg);
  }

  public CtrlCommandResult runCtrlCmdRemovePeer(Map params) {
    String peer = (String)params.get("peer");
    if(peer.indexOf('/') > -1) peer = peer.substring(peer.lastIndexOf('/') + 1);
    boolean result = true; String resultMsg;
    try {
      result = (peerList.remove(new CachePeer(peer.split(":"))));
      resultMsg = result?"Peer removed: " + params.get("peer"):"Peer not found: " + params.get("peer");
    } catch (UnknownHostException e) {
      resultMsg = "Failed to remove peer: " + e;
    }
    return new CtrlCommandResult(result, resultMsg);
  }

  public void setPeerList(Set peerList) {
    this.peerList.clear();
    this.peerList.addAll(peerList);

    ProxySession session;
    List cspSessions = SessionManager.getInstance().getSessions(CaProfile.MULTIPLE.getName());
    if(cspSessions != null)
      for(Iterator iter = cspSessions.iterator(); iter.hasNext(); ) {
        session = (ProxySession)iter.next();
        if(session instanceof CspSession) addCachePeer((CspSession)session);
      }
  }

  public CachePeer addCachePeer(CspSession session) {
    try {
      if(session.getCachePort() != -1) {
        CachePeer peer = new CachePeer(InetAddress.getByName(session.getCacheHost()), session.getCachePort());
        if(peerList.add(peer)) logger.fine("Added CspSession as cache peer: " + peer);
        return peer;
      }
    } catch (UnknownHostException e) {
      logger.warning("Unable to add cache peer, unknown host: " + session.getCacheHost());
    }
    return null;
  }

  public void removeCachePeer(CachePeer peer) {
    if(peerList.remove(peer)) logger.fine("Removed CspSession as cache peer: " + peer);
  }

  public void start() {

    trackerFailures = 0; // tracker update failures
    receivedPending = 0; // # of received pending ecm notifications
    receivedEntries = 0; // # of received ecm -> cw mappings
    receivedDiscarded = 0; // # of received mappings that already existed in local cache
    sentPending = 0;
    sentEntries = 0;
    sentAvg.clear();
    recvAvg.clear();
    pingSent = System.currentTimeMillis();

    if(clusterThread == null) {
      clusterThread = new Thread(this, "ClusteredCacheThread");
      clusterThread.start();
    }
    if(trackerUrl != null && trackerInterval != 0) {
      if(trackerThread == null) {
        trackerThread = new Thread(this, "ClusterCacheTrackerThread");
        trackerThread.start();
      }
    } else {
      if(trackerThread != null) trackerThread.interrupt();
    }
  }

  protected Set fetchList() throws IOException {
    List lines = Arrays.asList(FileFetcher.fetchList(trackerUrl, trackerKey));
    if(lines.isEmpty()) throw new IOException("Empty list or decryption failed.");
    String thisProxy = localHost + ":" + localPort;
    if(!lines.contains(thisProxy)) // couldn't identify self in list? might be sending to self then, best to warn...
      logger.warning("No record for this proxy found in list from tracker (" + trackerUrl + "), expected: " + thisProxy);

    Set peers = new HashSet(); String line; String[] pair; InetAddress addr; int port;
    for(Iterator iter = lines.iterator(); iter.hasNext(); ) {
      line = (String)iter.next();
      pair = line.split(":");
      if(pair.length != 2) {
        logger.warning("Malformed line in tracker list skipped: '" + line + "' (expected host:port)");
        continue;
      }
      if(localHost.equals(pair[0])) continue; // skip self
      try {
        addr = InetAddress.getByName(pair[0]);
      } catch (UnknownHostException e) {
        logger.warning("Unable to resolve host in tracker list, skipping: " + line);
        continue;
      }
      try {
        port = Integer.parseInt(pair[1]);
        if(port < 1 || port >= 0xFFFF) throw new NumberFormatException();
      } catch (NumberFormatException e) {
        logger.warning("Bad port number in tracker list, skipping: " + line);
        continue;
      }
      peers.add(new CachePeer(addr, port));
    }
    if(peers.isEmpty())
      throw new IOException("No valid peers found in tracker list (" + trackerUrl + "), wrong password?");
    return peers;
  }


  public CamdNetMessage processRequest(int successFactor, CamdNetMessage request, boolean alwaysWait, long maxCwWait) {

    /*
      decide whether to join the arbitration procedure to select which proxy in the cluster has the best chance of getting
      a fast reply, based on syncPeriod (0 = arbitration disabled), alwaysWait (= cache-only proxy, cant provide any replies),
      successFactor (-1 = service cannot be decoded on any local card), !hasPeers() (= configured for receive only mode,
      wont be sending any replies).
   */

    if(syncPeriod > 0 && !alwaysWait && successFactor != -1) {
      if(!contains(request) && !containsPending(request)) { // ensure the request is a new one
        if(hasPeers()) {
          if(debug) logger.fine("Adding for arbitration: " + request.hashCodeStr() + " (" + successFactor + ")");
          if(arbiter.addForArbitration(successFactor, request)) { // marks the request for arbitration
            sendMessage(request, null); // peer proxies check for the arbitration mark and will handle accordingly
          }
        }
        try {
          Thread.sleep(syncPeriod);
        } catch(InterruptedException e) {}
        if(hasPeers()) {
          if(!arbiter.resolveArbitration(request)) { // this proxy didn't win and should not process the request
            if(debug) logger.fine("Lost arbitration for: " + request.hashCodeStr());
            super.addRequest(successFactor, request, true); // achieve this by inserting into the pending set (= request already being processed)
          } else if(debug) logger.fine("Won arbitration for: " + request.hashCodeStr()); // else process normally
        }
      }
    }

    return super.processRequest(successFactor, request, alwaysWait, maxCwWait); // call the default processing
  }

  protected void delayAlert(int successFactor, CamdNetMessage request, boolean alwaysWait, long maxWait) {
    // only ask for help if there is no local resource
    if(successFactor == -1 || alwaysWait || successFactor > maxWait) sendResendRequest(request);
  }

  protected void addRequest(int successFactor, CamdNetMessage request, boolean alwaysWait) {
    super.addRequest(successFactor, request, alwaysWait);
    if(!alwaysWait) { // dont send any notification when we wont be forwarding to card
      request.setArbiterNumber(null); // remove any arbitration marker
      sendMessage(request, null); // tell all proxy peers that we're now processing this request
      if(System.currentTimeMillis() - pingSent > 4000) sendPing();
    }
  }

  public synchronized boolean processReply(CamdNetMessage request, CamdNetMessage reply) {
    if(reply == null || reply.isEmpty()) {
      if(pendingEcms.containsKey(request)) { // only send null replies for requests that were actually locked
        if(reply == null) reply = request.getEmptyReply();
        sendMessage(request, reply);
      }
    } else sendMessage(request, reply);
    return super.processReply(request, reply);
  }

  private boolean hasPeers() {
    if(useMulticast) return true;
    else return !peerList.isEmpty();
  }

  private void sendMessage(CamdNetMessage request, CamdNetMessage reply) {
    if(!hasPeers()) return;
    try {
      if(hideNames) {
        if(reply != null) {
          reply = new CamdNetMessage(reply);
          reply.setConnectorName(null);
        }
      }

      // 4 scenarios (type + tag + sid + onid + caid + hash + maybe arbiternr)
      // - request with arbiternumber:    1 + 1 + 2 + 2 + 2 + 4 + 8 (type request) negotiation for lock
      // - request without arbiternumber: 1 + 1 + 2 + 2 + 2 + 4     (type request) lock
      // - request and reply:             1 + 1 + 2 + 2 + 2 + 4            1 + 16 + connectorNameLen (type reply)
      // - request and empty reply:       1 + 1 + 2 + 2 + 2 + 4            1                         (type reply)

      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(bos);
      if(reply == null) dos.writeByte(TYPE_REQUEST);
      else {
        dos.writeByte(TYPE_REPLY);
        request.setArbiterNumber(null);
      }
      writeCacheReq(dos, request);
      if(reply != null) writeCacheRpl(dos, reply);
      dos.close();
      byte[] buf = bos.toByteArray();

      if(reply == null) {
        if(debug) logger.fine("Sending pending ecm, " + buf.length + " bytes");
        if(request.getArbiterNumber() == null) sentPending++;
      } else {
        if(debug) logger.fine("Sending ecm>cw pair, " + buf.length + " bytes");
        sentEntries++;
      }
      sendToPeers(buf);
    } catch(IOException e) {
      logger.throwing(e);
    }
  }

  private void reSendMessage(CamdNetMessage request, CamdNetMessage reply, CachePeer peer) {
    try {
      if(hideNames && reply.getConnectorName() != null) {
        reply = new CamdNetMessage(reply);
        reply.setConnectorName(null);
      }
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(bos);
      dos.writeByte(TYPE_REPLY);
      request.setArbiterNumber(null);
      writeCacheReq(dos, request);
      writeCacheRpl(dos, reply);
      dos.close();
      byte[] buf = bos.toByteArray();
      if(debug) logger.fine("Resending ecm>cw pair, " + buf.length + " bytes");
      reSentEntries++;
      sendToPeer(buf, peer.addr, peer.port);
    } catch(IOException e) {
      logger.throwing(e);
    }
  }

  private void sendResendRequest(CamdNetMessage request) {
    if(!hasPeers()) return;
    Long lastSend = (Long)resendReqs.get(request);
    long now = System.currentTimeMillis();
    if(lastSend == null || now - lastSend.longValue() > 50) { // avoid rebroadcasting the same req too often
      resendReqs.put(request, new Long(now));
      try {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeByte(TYPE_RESENDREQ);
        dos.writeInt(localPort);
        request.setArbiterNumber(null);
        writeCacheReq(dos, request);
        dos.close();
        byte[] buf = bos.toByteArray();
        if(debug) logger.fine("Sending resend request, " + buf.length + " bytes");
        sentResendReqs++;
        sendToPeers(buf);
      } catch(IOException e) {
        logger.throwing(e);
      }
    }
  }

  private static void writeCacheReq(DataOutputStream dos, CamdNetMessage msg) throws IOException {
    dos.writeByte(msg.getCommandTag());
    dos.writeShort(msg.getServiceId());
    dos.writeShort(msg.getNetworkId());
    dos.writeShort(msg.getCaId());
    dos.writeInt(msg.getDataHash());
    if(msg.getArbiterNumber() != null) dos.writeDouble(msg.getArbiterNumber().doubleValue());
  }

  private static void writeCacheRpl(DataOutputStream dos, CamdNetMessage msg) throws IOException {
    dos.writeByte(msg.getCommandTag());
    if(!msg.isEmpty()) {
      dos.write(msg.getCustomData());
      if(msg.getConnectorName() != null) dos.writeUTF(msg.getConnectorName());
    }
  }

  private void sendToPeers(byte[] buf) throws IOException {
    sendToPeers(buf, null);
  }

  private void sendToPeers(byte[] buf, String excludeIp) throws IOException {
    DatagramPacket packet;
    CachePeer peer;

    if(useMulticast) {
      packet = new DatagramPacket(buf, buf.length, mcGroup, remotePort);
      ((MulticastSocket)sendSock).setTimeToLive(ttl);
      sendPacket(packet);
    } else {
      for(Iterator iter = new ArrayList(peerList).iterator(); iter.hasNext(); ) {
        peer = (CachePeer)iter.next();
        if(excludeIp != null && excludeIp.equals(peer.addr.getHostAddress())) continue;
        packet = new DatagramPacket(buf, buf.length, peer.addr, peer.port);
        sendPacket(packet);
      }
    }
  }

  private void sendToPeer(byte[] buf, InetAddress target, int port) throws IOException {
    DatagramPacket packet = new DatagramPacket(buf, buf.length, target, port);
    sendPacket(packet);
  }

  private synchronized void sendPacket(DatagramPacket packet) throws IOException {
    sendSock.send(packet);
    sentAvg.addRecord(packet.getLength());
  }

  private void sendPing() {
    if(!hasPeers()) return;
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(bos);
      dos.writeByte(TYPE_PINGREQ);
      pingSent = System.currentTimeMillis();
      dos.writeLong(pingSent);
      dos.writeInt(localPort);
      dos.close();
      byte[] buf = bos.toByteArray();
      if(debug) logger.fine("Sending ping request, " + buf.length + " bytes");
      sendToPeers(buf);
    } catch (IOException e) {
      logger.throwing(e);
    }
  }

  private void sendPingReply(long ping, InetAddress target, int port) {
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(bos);
      dos.writeByte(TYPE_PINGRPL);
      dos.writeLong(ping);
      dos.close();
      byte[] buf = bos.toByteArray();
      sendToPeer(buf, target, port);
    } catch (IOException e) {
      logger.throwing(e);
    }
  }

  public void run() {
    if(Thread.currentThread() == clusterThread) startReceiving();
    else if(Thread.currentThread() == trackerThread) startTrackerUpdates();
  }

  private void startReceiving() {
    boolean alive = true;

    while(alive) {
      DatagramPacket packet = null;
      try {
        byte[] buf = new byte[512];
        packet = new DatagramPacket(buf, buf.length);
        recvSock.receive(packet);
        recvAvg.addRecord(packet.getLength());

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(packet.getData(), 0, packet.getLength()));
        int type = dis.readByte();

        CamdNetMessage request;
        long ping;

        switch(type) {
          case TYPE_REPLY:
            receivedEntries++;
            incrementReceived(packet.getAddress().getHostAddress());
            request = CamdNetMessage.parseCacheReq(dis);
            request.setOriginAddress(packet.getAddress().getHostAddress());            
            CamdNetMessage reply = CamdNetMessage.parseCacheRpl(dis, request);
            reply.setOriginAddress(packet.getAddress().getHostAddress());
            if(reply.getConnectorName() != null) reply.setConnectorName("remote: " + reply.getConnectorName());
            if(!this.contains(request)) super.processReply(request, reply);
            else receivedDiscarded++;
            if(debug) logger.fine("Cache reply received for: " + request.hashCodeStr() + " -> " + reply.hashCodeStr() +
              " (from: " + reply.getOriginAddress() + ") " + packet.getLength() + " bytes");
            break;

          case TYPE_REQUEST:
            request = CamdNetMessage.parseCacheReq(dis);
            request.setOriginAddress(packet.getAddress().getHostAddress());            
            if(request.getArbiterNumber() != null) {
              arbiter.addArbitrationPeer(request);
              if(debug) logger.fine("Arbitration request received: " + request.hashCodeStr() + " (from: " +
                  packet.getAddress().getHostAddress() + ") arbiterNumber: " + request.getArbiterNumber());
            } else {
              receivedPending++;
              super.addRequest(-1, request, false);
              if(debug) logger.fine("Pending request received: " + request.hashCodeStr() + " (from: " +
                  packet.getAddress().getHostAddress() + ") " + packet.getLength() + " bytes");
            }
            break;

          case TYPE_PINGREQ:
            ping = dis.readLong();
            int port = dis.readInt();
            sendPingReply(ping, packet.getAddress(), port);
            if(autoAddPeers) autoAddPeer(new CachePeer(packet.getAddress(), port));
            break;

          case TYPE_PINGRPL:
            ping = dis.readLong();
            ping = System.currentTimeMillis() - ping;
            hostPings.put(packet.getAddress().getHostAddress(), new Long(ping));
            break;

          case TYPE_RESENDREQ:
            receivedResendReqs++;
            port = dis.readInt();
            request = CamdNetMessage.parseCacheReq(dis);
            CachePeer peer = new CachePeer(packet.getAddress(), port);
            if(peerList.contains(peer)) {
              reply = peekReply(request);
              if(reply != null) reSendMessage(request, reply, peer);
            }
            break;

          default:
            logger.warning("Unknown cache message received from " + packet.getAddress() + ", type: " + type +
                " size: " + packet.getLength());
        }
        dis.close();
      } catch(IOException e) {
        logger.throwing(e);
        try {
          logger.warning("Internal error receiving remote cache packet [" + packet.getAddress().getHostAddress() + " - " +
              DESUtil.bytesToString(packet.getData(), packet.getLength()) + "]: " + e);
          mismatchedHosts.add(packet.getAddress().getHostAddress());
        } catch (Exception e2) {
          logger.throwing(e2);
          logger.warning("Internal error receiving remote cache packet [" + packet + "]: " + e2);
        }
      }
    }
    clusterThread = null;
    logger.fine("Cluster thread dying.");
  }

  private void autoAddPeer(CachePeer peer) {
    if(!peerList.contains(peer)) {
      peerList.add(peer);
      logger.info("New peer auto-added: " + peer.addr.getHostAddress() + ":" + peer.port);
    }
    if(System.currentTimeMillis() - pingSent > 4000) sendPing();
  }

  private void incrementReceived(String addr) {
    Long l = (Long)hostReceives.get(addr);
    if(l == null) l = new Long(1);
    else l = new Long(1 + l.longValue());
    hostReceives.put(addr, l);
  }

  private void startTrackerUpdates() {
    boolean alive = true;

    while(alive && trackerInterval > 0) {
      try {
        logger.fine("Waiting " + trackerInterval + " ms before next tracker update...");
        Thread.sleep(trackerInterval);
        try {
          setPeerList(fetchList());
          logger.info("Fetched peer list from tracker (" + trackerUrl + "): " + peerList.size() + " entries found.");
          logger.fine("Peers: " + peerList);
        } catch(IOException e) {
          trackerFailures++;
          logger.warning("Exception occured fetching tracker list: " + e);
          logger.throwing(e);

        }
      } catch(InterruptedException e) {
        alive = false;
      }
    }
    trackerThread = null;
    logger.fine("Tracker thread dying.");
  }

  public Properties getUsageStats() {
    Properties p = super.getUsageStats();
    p.setProperty("cache-peers", String.valueOf(peerList.size()));
    p.setProperty("tracker-failures", String.valueOf(trackerFailures));
    p.setProperty("received-pending", String.valueOf(receivedPending));
    p.setProperty("received-cached", String.valueOf(receivedEntries));
    p.setProperty("received-discarded", String.valueOf(receivedDiscarded));
    p.setProperty("received-resendreqs", String.valueOf(receivedResendReqs));
    p.setProperty("sent-resends", String.valueOf(reSentEntries));
    p.setProperty("sent-resendreqs", String.valueOf(sentResendReqs));
    p.setProperty("sent-pending", String.valueOf(sentPending));
    p.setProperty("sent-cached", String.valueOf(sentEntries));
    p.setProperty("avg-sent-bytes/s", String.valueOf(sentAvg.getTotal(true) / 10));
    p.setProperty("avg-received-bytes/s", String.valueOf(recvAvg.getTotal(true) / 10));
    if(!mismatchedHosts.isEmpty()) p.setProperty("version-mismatch", mismatchedHosts.toString());

    if(debug) {
      p.setProperty("peer-pings", hostPings.toString());
      p.setProperty("peer-receives", hostReceives.toString());
      p.setProperty("cached-services", getServiceList());
      p.setProperty("pending-services", getPendingList());
      p.setProperty("arbiter-mine", String.valueOf(arbiter.prePendingMine.size()));
      p.setProperty("arbiter-all", String.valueOf(arbiter.prePendingAll.size()));
    }
    return p;
  }

  protected String getServiceList() {
    Set ids = new HashSet();
    CamdNetMessage msg;
    for(Iterator iter = new ArrayList(ecmMap.values()).iterator(); iter.hasNext(); ) {
      msg = (CamdNetMessage)iter.next();
      ids.add(Integer.toHexString(msg.getServiceId()));
    }
    return ids.toString();
  }

  protected String getPendingList() {
    Set ids = new HashSet();
    CamdNetMessage msg;
    for(Iterator iter = new ArrayList(pendingEcms.keySet()).iterator(); iter.hasNext(); ) {
      msg = (CamdNetMessage)iter.next();
      ids.add(Integer.toHexString(msg.getServiceId()));
    }
    return ids.toString();
  }

  protected void removeRequest(CamdNetMessage request) {
    super.removeRequest(request);
    arbiter.cleanupArbitration(request);
  }

  public void onRemoveStale(CamdNetMessage msg) {
    arbiter.cleanupArbitration(msg);
  }

  static class RequestArbiter {

    Map prePendingMine = Collections.synchronizedMap(new HashMap());
    Map prePendingAll = Collections.synchronizedMap(new HashMap());

    boolean addForArbitration(int baseValue, CamdNetMessage request) {
      if(prePendingMine.containsKey(request)) return false;
      Double d = new Double(Math.random() + (double)baseValue);
      request.setArbiterNumber(d);
      prePendingMine.put(request, d);
      addArbitrationPeer(request);
      return true;
    }

    void addArbitrationPeer(CamdNetMessage request) {
      Set allNumbers = new TreeSet();
      if(prePendingAll.containsKey(request)) allNumbers = (Set)prePendingAll.get(request);
      else prePendingAll.put(request, allNumbers);
      allNumbers.add(request.getArbiterNumber());
    }

    boolean resolveArbitration(CamdNetMessage request) {
      Double mine = (Double)prePendingMine.get(request);
      TreeSet all = (TreeSet)prePendingAll.get(request);
      if(mine == null || all == null) return true; // already cleaned up via local remove
      Double winner = (Double)all.first();
      return mine.equals(winner);
    }

    void cleanupArbitration(CamdNetMessage request) {
      prePendingMine.remove(request);
      prePendingAll.remove(request);
    }

  }

  public static class CachePeer {

    InetAddress addr;
    int port;

    CachePeer(InetAddress addr, int port) {
      this.addr = addr;
      this.port = port;
    }

    CachePeer(String[] s) throws UnknownHostException {
      this(InetAddress.getByName(s[0]), Integer.parseInt(s[1]));
    }

    public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;
      CachePeer cachePeer = (CachePeer) o;
      return port == cachePeer.port && addr.equals(cachePeer.addr);
    }

    public int hashCode() {
      int result;
      result = addr.hashCode();
      result = 31 * result + port;
      return result;
    }

    public String toString() {
      return addr + ":" + port;
    }
  }

}
