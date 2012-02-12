package com.bowman.cardserv.session;

import com.bowman.cardserv.*;
import com.bowman.cardserv.web.FileFetcher;

import java.io.*;
import java.net.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2011-08-07
 * Time: 23:41
 */
public class CacheDummySession extends AbstractSession {

  public CacheDummySession() {
    super((ListenPort)CaProfile.MULTIPLE.getListenPorts().get(0), ProxyConfig.getInstance().getDefaultMsgListener());
  }

  public int sendEcmReply(CamdNetMessage ecmRequest, CamdNetMessage ecmReply) {
    return 0;
  }

  protected int sendEcmReplyNative(CamdNetMessage req, CamdNetMessage reply) {
    return 0;
  }

  public String getProtocol() {
    return "GHttp";
  }

  public String getLastContext() {
    return "Dummy";
  }

  public void close() {}

  public boolean isConnected() {
    return true;
  }

  public int sendMessage(CamdNetMessage msg) {
    return 0;
  }

  public void setFlag(CamdNetMessage request, char f) {
  }

  public void run() {}

  public String getUser() {
    return "CacheCoveragePlugin";
  }
}
