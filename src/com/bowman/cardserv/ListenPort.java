package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.ProxyXmlConfig;
import com.bowman.util.Globber;

import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2007-aug-31
 * Time: 14:38:37
 */
public class ListenPort implements XmlConfigurable {

  private static Map portNumbers = new HashMap();

  private String protocol;
  private int port;
  private InetAddress bindAddr;
  private boolean alive;

  private ServerSocket srv;
  private Thread acceptThread;

  private Map properties = new HashMap();
  private Set allowList = new HashSet();
  private Set denyList = new HashSet();

  private CaProfile profile;

  public ListenPort(String protocol) {
    this.protocol = protocol;
    if("Csp".equals(protocol)) alive = true;
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    int port = xml.getPortValue("listen-port");

    if(portNumbers.containsKey(new Integer(port))) {
      ListenPort lp = (ListenPort)portNumbers.get(new Integer(port));
      if(lp != this) 
        throw new ConfigException(xml.getFullName(), "listen-port", "Listen-port '" + port + "' already in use by [" + lp + "]");
    }
    setPort(port);

    String bindIp = null; bindAddr = null;
    try {
      bindIp = xml.getStringValue("bind-ip");
    } catch (ConfigException e) {}
    if(bindIp != null) try {
      bindAddr = InetAddress.getByName(bindIp);
    } catch (UnknownHostException e) {
      throw new ConfigException(xml.getFullName(), "bind-ip", "Invalid listen-port bind-ip: " + bindIp);
    }

    properties.clear();
    Iterator iter = xml.getMultipleSubConfigs(null);
    if(iter != null) {
      ProxyXmlConfig e; 
      while(iter.hasNext()) {
        e = (ProxyXmlConfig)iter.next();
        if(e.getName() != null && e.getName().length() > 0) {
          properties.put(e.getName(), xml);
        }
      }

      try {
        e = (ProxyXmlConfig)properties.remove("allow-list");
        if(e == null) throw new ConfigException("");
        String allow = e.getStringValue("allow-list");
        allowList = new HashSet(Arrays.asList(allow.split(" ")));
      } catch (ConfigException ce) {
        allowList = new HashSet();
      }
      try {
        e = (ProxyXmlConfig)properties.remove("deny-list");
        if(e == null) throw new ConfigException("");
        String deny = e.getStringValue("deny-list");        
        denyList = new HashSet(Arrays.asList(deny.split(" ")));
      } catch (ConfigException ce) {
        denyList = new HashSet();
      }

    }
  }

  public String getProperties() {
    StringBuffer sb = new StringBuffer();
    for(Iterator iter = properties.keySet().iterator(); iter.hasNext();) {
      String key = (String)iter.next();
      ProxyXmlConfig xml = (ProxyXmlConfig)properties.get(key);
      try {
        sb.append(xml.getSubConfig(key));
      } catch(ConfigException e) {}
    }
    return sb.toString();
  }

  public String getStringProperty(String name) {
    ProxyXmlConfig xml = (ProxyXmlConfig)properties.get(name);
    if(xml == null) return null;
    try {
      return xml.getStringValue(name);
    } catch(ConfigException e) {
      return null;
    }
  }

  public String getStringProperty(String element, String attribute) {
    ProxyXmlConfig xml = (ProxyXmlConfig)properties.get(element);
    if(xml == null) return null;
    try {
      xml = xml.getSubConfig(element);
      return xml.getStringValue(attribute);
    } catch(ConfigException e) {
      return null;
    }
  }

  public byte[] getBytesProperty(String name) {
    ProxyXmlConfig xml = (ProxyXmlConfig)properties.get(name);
    if(xml == null) return null;
    try {
      return xml.getBytesValue(name);
    } catch(ConfigException e) {
      return null;
    }
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
    portNumbers.put(new Integer(port), this);
  }

  public ServerSocket getServerSocket() {
    return srv;
  }

  public void setServerSocket(ServerSocket serverSocket) {
    if(srv != null) destroy();
    portNumbers.put(new Integer(serverSocket.getLocalPort()), this);
    srv = serverSocket;
  }

  public String getProtocol() {
    return protocol;
  }

  public void setProtocol(String protocol) {
    this.protocol = protocol;
  }

  public CaProfile getProfile() {
    return profile;
  }

  public void setProfile(CaProfile profile) {
    this.profile = profile;
  }

  public boolean isAlive() {
    return alive;
  }

  public boolean isDenied(String ip) {
    Set set = new HashSet(ProxyConfig.getInstance().getDefaultDenyList());
    set.addAll(denyList);
    return matchesMasks(ip, set);
  }

  public boolean isAllowed(String ip) {
    return allowList.isEmpty() || matchesMasks(ip, allowList);
  }

  private static boolean matchesMasks(String s, Set masks) {
    for(Iterator iter = masks.iterator(); iter.hasNext();) {
      if(Globber.match((String)iter.next(), s, false)) return true;
    }
    return false;
  }

  public void start(CamdMessageListener listener, CaProfile profile) {
    this.profile = profile;
    if(srv == null) {
      try {
        createServerSocket();
      } catch(IOException e) {
        CardServProxy.logger.throwing(e);
        CardServProxy.logger.warning("Failed to open listen port [" + this + "] for '" + profile.getName() +
            "' (" + e.getMessage() + ")");
      }
    }
    acceptThread = new AcceptThread(listener, this);
    alive = true;
    portNumbers.put(new Integer(port), this);
    acceptThread.start();
    CardServProxy.logger.info("Listen port ready for profile '" + getProfile().getName() + "' - " + this);
  }

  public void destroy() {
    if(!alive || "Csp".equals(protocol)) return;
    alive = false;
    try {
      if(srv != null) srv.close();
    } catch(IOException e) {
    }
    if(acceptThread != null) acceptThread.interrupt();
    srv = null;
    portNumbers.remove(new Integer(port));
    CardServProxy.logger.info("Listen port closed: " + this);
  }

  public String toString() {
    String p = ("" + protocol.charAt(0)).toUpperCase();
    return p + port + (bindAddr == null?"":"@" + bindAddr.getHostAddress());
  }

  public void createServerSocket() throws IOException {
    srv = new ServerSocket();
    if(bindAddr == null) srv.bind(new InetSocketAddress(port));
    else srv.bind(new InetSocketAddress(bindAddr, port));
  }
}
