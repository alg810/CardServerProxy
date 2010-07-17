package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.session.SessionManager;

import java.net.*;
import java.io.IOException;
import java.lang.reflect.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2006-feb-26
 * Time: 11:27:34
 */
class AcceptThread extends Thread {

  private CamdMessageListener listener;
  private ListenPort listenPort;

  public AcceptThread(CamdMessageListener listener, ListenPort listenPort) {
    super("AcceptThread[" + listenPort.getProfile().getName() + ":" + listenPort + "]");
    this.listener = listener;
    this.listenPort = listenPort;
  }

  public void run() {
    Socket s;

    while(listenPort.isAlive()) {

      try {
        s = listenPort.getServerSocket().accept();
        String ip = s.getInetAddress().getHostAddress();

        int maxThreads = ProxyConfig.getInstance().getMaxThreads();
        if(Thread.activeCount() > maxThreads) {
          try {
            Thread.sleep(1000);
          } catch(InterruptedException e) {}
          CardServProxy.logger.severe("Max-threads exceeded (" + maxThreads + "), closing incoming connection for [" +
            listenPort.getProfile().getName() + ":" + listenPort + "]: " + com.bowman.cardserv.util.CustomFormatter.formatAddress(ip));
          s.close();
          continue;
        }

        if(!listenPort.isAllowed(ip) || listenPort.isDenied(ip)) {
          CardServProxy.logger.fine("Rejected connection for [" + listenPort.getProfile().getName() + ":" +
              listenPort + "]: " + com.bowman.cardserv.util.CustomFormatter.formatAddress(ip) + " not allowed.");
          SessionManager.getInstance().fireUserLoginFailed("?@" + ip, listenPort + "/" + listenPort.getProfile().getName(), ip, "rejected by [" +
            listenPort + "] ip deny list.");
          s.close();
        } else {
          CardServProxy.logger.fine("Accepted connection for [" + listenPort.getProfile().getName() + ":" +
              listenPort + "]: " + com.bowman.cardserv.util.CustomFormatter.formatAddress(ip));

          ProxySession ps = createSession(s, listenPort, listener);

          if(ps == null) {            
            try { s.close(); } catch (Exception e) {}
          } else if(ProxyConfig.getInstance().getRemoteHandler() != null) {
            ps.addTransactionListener(ProxyConfig.getInstance().getRemoteHandler());
          }
        }
      } catch(SocketException e) {
        CardServProxy.logger.throwing("Server socket closed: " + listenPort + " (" + e.getMessage() + ")", e);
        listenPort.destroy();
      } catch(IOException e) {
        CardServProxy.logger.severe("Exception accepting socket connection for [" + listenPort + "], aborting...", e);
        listenPort.destroy();
      } catch(Throwable t) {
        CardServProxy.logger.severe("Error in accept loop: " + t, t);
        return;
      }
    }

  }

  // load session implementation dynamically

  private static final String SESSION_PKG = "com.bowman.cardserv.session";

  private static ProxySession createSession(Socket s, ListenPort listenPort, CamdMessageListener listener) throws IOException {
    try {
      Class sessionClass = Class.forName(SESSION_PKG + "." + listenPort.getProtocol() + "Session");

      Constructor c = sessionClass.getConstructor(new Class[] {Socket.class, ListenPort.class, CamdMessageListener.class});
      Object o = c.newInstance(new Object[] {s, listenPort, listener});
      if(!(o instanceof ProxySession)) throw new IOException("Session class not instanceof ProxySession: " + sessionClass);
      return (ProxySession)o;

    } catch(InvocationTargetException e) {
      e.printStackTrace();
      if(e.getCause() != null) CardServProxy.logger.severe("Exception creating session: " + e.getCause(), e.getCause());
      else CardServProxy.logger.severe("Exception creating session: " + e, e);
      return null;
    } catch(Throwable e) {
      e.printStackTrace();
      if(e.getCause() != null) e = e.getCause();
      CardServProxy.logger.severe("Unable to create session: " + e, e);
      try { s.close(); } catch (Exception ex) {}
      throw new IOException(e.toString());
    }
  }
}
