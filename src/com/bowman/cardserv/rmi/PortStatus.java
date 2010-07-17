package com.bowman.cardserv.rmi;

import com.bowman.cardserv.ListenPort;

import java.io.Serializable;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Feb 26, 2009
 * Time: 11:59:02 PM
 */
public class PortStatus extends AbstractStatus implements Serializable {

  private final String protocol, label, properties;
  private final int port;
  private final boolean alive;

  public PortStatus(ListenPort listenPort) {
    protocol = listenPort.getProtocol();
    label = listenPort.toString();
    port = listenPort.getPort();
    alive = listenPort.isAlive();
    properties = listenPort.getProperties();
  }

  public String getProtocol() {
    return protocol;
  }

  public String getLabel() {
    return label;
  }

  public String getProperties() {
    return properties;
  }

  public int getPort() {
    return port;
  }

  public boolean isAlive() {
    return alive;
  }
}
