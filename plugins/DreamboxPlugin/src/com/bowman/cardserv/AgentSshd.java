package com.bowman.cardserv;

import org.apache.sshd.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.*;
import org.apache.sshd.server.shell.ProcessShellFactory;
import org.apache.sshd.server.session.ServerSession;

import java.io.*;
import java.security.PublicKey;
import java.net.InetSocketAddress;
import java.util.logging.*;
import java.util.*;

import com.bowman.cardserv.interfaces.XmlConfigurable;
import com.bowman.cardserv.util.ProxyXmlConfig;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Feb 1, 2010
 * Time: 5:59:12 PM
 */
public class AgentSshd implements XmlConfigurable {

  private DreamboxPlugin parent;
  private SshServer sshd;
  int listenPort, rangeStart, rangeCount;
  private Map tunnelSessions = new HashMap();
  private Set reservedPorts = new HashSet();

  public AgentSshd(DreamboxPlugin parent) {
    this.parent = parent;
    try {
      LogManager lm = LogManager.getLogManager();
      if(lm.getLogger("org.apache.sshd.SshServer") == null)
        lm.readConfiguration(AgentSshd.class.getResourceAsStream("sshd-logging.properties"));      
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    listenPort = xml.getPortValue("listen-port");
    xml = xml.getSubConfig("tunnel-port-range");
    rangeStart = xml.getIntValue("start");
    rangeCount = xml.getIntValue("count", 10);
  }  

  public void start() throws IOException {
    sshd = SshServer.setUpDefaultServer();
    sshd.setPort(listenPort);

    // sshd.setShellFactory(new ProcessShellFactory(new String[] { "/bin/sh", "-i", "-l" }));
    sshd.setShellFactory(new DummyShellFactory());

    sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider("etc/hostkey.ser"));

    sshd.setPublickeyAuthenticator(new PublickeyAuthenticator() {
      public boolean authenticate(String s, PublicKey publicKey, ServerSession session) {
        if(parent.getBox(s) == null) {
          parent.logger.warning("Unknown box (" + s + ") ssh login from: " + session.getIoSession().getRemoteAddress());
          return false;
        }
        return true;
      }
    });

    sshd.setTcpIpForwardFilter(new TcpIpForwardFilter() {
      public boolean canListen(InetSocketAddress address, ServerSession session) {
        TunnelSession ts = getTunnelSession(session.getUsername());
        if(ts != null) {
          parent.logger.warning("Tunnel session already exists for box: " + session.getUsername() + " (closing old...)");
          ts.close();
        }

        parent.logger.info("Tunnel session granted: " + addTunnelSession(address.getPort(), session));
        return true;
      }

      public boolean canConnect(InetSocketAddress address, ServerSession session) {
        return false;
      }
    });

    sshd.start();
        
  }

  public void stop() {
    try {
      sshd.stop();
    } catch(InterruptedException e) {
      e.printStackTrace();
    }
  }

  public synchronized int findTunnelPort() {
    TunnelSession ts; Set usedPorts = new TreeSet();
    usedPorts.addAll(reservedPorts);
    for(Iterator iter = tunnelSessions.keySet().iterator(); iter.hasNext(); ) {
      ts = getTunnelSession((String)iter.next());
      if(ts != null) usedPorts.add(new Integer(ts.listenPort));
    }
    Integer i = new Integer(rangeStart);
    while(usedPorts.contains(i)) i = new Integer(i.intValue() + 1);
    if(i.intValue() > (rangeStart + rangeCount)) return -1; // no free ports
    else {
      reservedPorts.add(i);
      return i.intValue();
    }
  }
  
  public int getBoxPort(String id) {
    TunnelSession ts = getTunnelSession(id);
    if(ts == null) return -1;
    else return ts.listenPort;
  }

  private TunnelSession addTunnelSession(int port, ServerSession session) {
    String id = session.getUsername();
    TunnelSession newSession = new TunnelSession(port, id, session);
    TunnelSession oldSession = (TunnelSession)tunnelSessions.put(id, newSession);
    if(oldSession != null) oldSession.close();
    return newSession;
  }

  private TunnelSession getTunnelSession(String id) {
    TunnelSession session = (TunnelSession)tunnelSessions.get(id);
    if(session != null && !session.isAlive()) {
      tunnelSessions.remove(session);
      reservedPorts.remove(new Integer(session.listenPort));
      session = null;
    }
    return session;
  }

  public boolean closeTunnelSession(String id) {
    TunnelSession session = getTunnelSession(id);
    if(session != null) {
      session.close();
      reservedPorts.remove(new Integer(session.listenPort));
      return true;
    } else return false;
  }

  public int getPort() {
    return listenPort;
  }

  private static class TunnelSession {
    int listenPort;
    String boxId;
    ServerSession session;

    TunnelSession(int listenPort, String boxId, ServerSession session) {
      this.listenPort = listenPort;
      this.boxId = boxId;
      this.session = session;
    }

    boolean isAlive() {
      if(session == null) return false;
      else {
        boolean connected = session.getIoSession().isConnected();
        if(!connected) session = null;
        return connected;
      }
    }

    void close() {
      if(session != null) session.close(true);
    }

    public String toString() {
      return boxId + " (port: " + listenPort + " remote-ip: " + (session == null?null:session.getIoSession().getRemoteAddress() + ")");
    }
  }

  public static void main(String[] args) throws IOException {
    AgentSshd sshd = new AgentSshd(null);
    sshd.listenPort = 2222;
    sshd.start();
  }

}
