package com.bowman.cardserv.session;

import com.bowman.cardserv.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2011-08-07
 * Time: 23:41
 */
public class CacheDummySession extends AbstractSession {

  private CacheForwarder parent;

  public CacheDummySession(CacheForwarder parent) {
    super((ListenPort)CaProfile.MULTIPLE.getListenPorts().get(0), ProxyConfig.getInstance().getDefaultMsgListener());
    this.parent = parent;
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
    return parent.isConnected();
  }

  public int sendMessage(CamdNetMessage msg) {
    return 0;
  }

  public void setFlag(CamdNetMessage request, char f) {
    System.out.println("Flag: " + f);
  }

  public void run() {
  }

  public String getUser() {
    return "CacheCoveragePlugin";
  }
}
