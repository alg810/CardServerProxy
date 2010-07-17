package com.bowman.cardserv.test;

import com.bowman.cardserv.rmi.*;

import java.io.*;
import java.rmi.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2006-feb-28
 * Time: 21:17:20
 */
public class RemoteTestClient extends UnicastRemoteObject implements RemoteListener {

  private static final String[] CWS_STATES = {"disconnected", "connected", "connecting", "", "unresponsive", "disabled"};
  private static final SimpleDateFormat fmt = new SimpleDateFormat("yyMMdd HH:mm:ss");

  static {
    System.setProperty("java.security.policy", "etc/policy.all");
  }

  private String registryUrl;

  public RemoteTestClient(int port, String registryUrl) throws RemoteException {
    super(port);
    this.registryUrl = registryUrl;
  }

  public void test() throws IOException, NotBoundException {

    System.setSecurityManager(new RMISecurityManager());

    RemoteProxy proxy = (RemoteProxy)Naming.lookup(registryUrl);

    proxy.addRemoteListener(this);

    System.out.println("connected to: " + registryUrl);

    System.out.println("proxy name: " + proxy.getName());
    System.out.println("started: " + fmt.format(new Date(proxy.getProxyStartTime())));
    System.out.println("defined users: " + Arrays.asList(proxy.getLocalUsers()));
    System.out.println("connected user count: " + proxy.getSessionCount(null, false));
    System.out.println();

    System.out.println("active users:");
    UserStatus[] us = proxy.getUsersStatus(null, true);
    for(int i = 0; i < us.length; i++) {
      // System.out.println("user '" + us[i].getUserName() + "': " + us[i].getSessionCount() + " session(s)...");
      SessionStatus[] sessions = us[i].getSessions();
      for(int n = 0; n < sessions.length; n++) {
        System.out.println("  " + (n + 1) + ": " + sessions[n].getRemoteHost() + " watching: " + sessions[n].getCurrentService());
        System.out.println("    ecms: " + sessions[n].getEcmCount() + " emms: " + sessions[n].getEmmCount() +
            " avgInterval: " + sessions[n].getAvgEcmInterval());
      }

    }
    System.out.println();

    System.out.println("defined card count: " + proxy.getCwsCount(null));
    CwsStatus[] cs = proxy.getMultiCwsStatus(null);
    for(int i = 0; i < cs.length; i++) System.out.println(formatCwsStatus(cs[i]));

    System.out.println();
    System.out.println("printing events...");

    synchronized(this) {
      try {
        wait();
      } catch(InterruptedException e) {
        e.printStackTrace();
      }
    }

  }

  private static String formatCwsStatus(CwsStatus cws) {
    String state = CWS_STATES[cws.getStatus()];
    String host = cws.getRemoteHost();
    if(host == null) host = "unknown";
    String status = state;
    if(cws.getStatus() == CwsStatus.CWS_CONNECTED || cws.getStatus() == CwsStatus.CWS_UNRESPONSIVE) {
      status += " (up since: " + fmt.format(new Date(cws.getConnectTimeStamp()));
      status += ", avg processing time: " + cws.getCurrentEcmTime() + " ms)";
    }
    return "CWS[" + cws.getName() + "] remote host: " + host + " status: " + status;
  }

  public void eventRaised(RemoteEvent event) throws RemoteException {

    switch(event.getType()) {
      case RemoteEvent.CWS_CONNECTED:
        System.out.println("Successfully connected to: " + event.getLabel());
        break;
      case RemoteEvent.CWS_DISCONNECTED:
        System.out.println("Connection lost with: " + event.getLabel());
        break;
      case RemoteEvent.CWS_CONNECTION_FAILED:
        System.out.println("Connection attempt failed: " + event.getLabel() + " (" + event.getMessage() + ")");
        break;
      case RemoteEvent.CWS_WARNING:
        System.out.println("CWS Timeout warning: " + event.getLabel() +  " (" + event.getMessage() + ")");
        break;
      case RemoteEvent.USER_STATUS_CHANGED:
        System.out.println(event.getLabel() + " zapped to: " +event.getMessage());
        break;
      case RemoteEvent.CWS_LOST_SERVICE:
        System.out.println(event.getLabel() + " lost service: " + event.getMessage());
        break;
      case RemoteEvent.ECM_TRANSACTION:
        System.out.println(event.getLabel() + " finished ecm transaction: " + event.getProperty("flags"));
        break;
      default:
        System.out.println("Unknown remote event received: " + event.getType() + " " + event.getLabel() + " " +
            event.getMessage());
    }
  }

  public static void main(String[] args) throws Exception {
    String url = "rmi://localhost:4099/cardservproxy";
    int port = 4100;
    if(args.length > 0) url = args[0];
    if(args.length > 1) port = Integer.parseInt(args[1]);
    new RemoteTestClient(port, url).test();
  }
}

