package com.bowman.cardserv.rmi;

import java.rmi.server.RMISocketFactory;
import java.net.*;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2007-jan-21
 * Time: 01:50:59
 */
public class IpCheckSocketFactory extends RMISocketFactory {

  public static final int READ_TIMEOUT = 300 * 1000; // 5 mins

  public ServerSocket createServerSocket(int port) throws IOException {
    return new IpCheckServerSocket(port);
  }

  public Socket createSocket(String host, int port) throws IOException {
    Socket s = new Socket(host, port);
    s.setSoTimeout(READ_TIMEOUT);
    return s;
  }
}
