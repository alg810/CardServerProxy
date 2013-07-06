package com.bowman.cardserv.rmi;

import com.bowman.util.Globber;
import com.bowman.cardserv.util.ProxyLogger;

import java.net.*;
import java.io.IOException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2007-jan-21
 * Time: 01:48:52
 */
public class IpCheckServerSocket extends ServerSocket {

  private static ProxyLogger logger;
  static {
    try {
      logger = ProxyLogger.getProxyLogger(RemoteHandler.class.getName());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static InetAddress bindAddress;
  private static Set ipMasks = new HashSet();
  public static void addIpMask(String mask) {
    ipMasks.add(mask);
  }
  public static void clearIpMasks() {
    ipMasks.clear();
  }
  public static void setBindAddress(InetAddress bindAddr) {
    bindAddress = bindAddr;
  }

  public IpCheckServerSocket(int port) throws IOException {
    super(port, 50, bindAddress);
  }    

  public Socket accept() throws IOException {
    Socket s = super.accept();
    for(Iterator iter = ipMasks.iterator(); iter.hasNext();) {
      String mask = (String)iter.next();
      if(Globber.match(mask, s.getInetAddress().getHostAddress(), false)) {
        // System.err.println("rmi connection matched " + mask + "(" + s.getInetAddress() + ")");
        return s;
      }
    }

    if(logger != null) logger.warning("RMI connection from unknown IP denied: " + s.getInetAddress().getHostAddress());
    s.close(); // no masks match, close and recurse
    return accept();
  }


}
