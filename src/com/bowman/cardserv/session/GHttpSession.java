package com.bowman.cardserv.session;

import com.bowman.cardserv.*;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2011-06-08
 * Time: 15:13
 */
public class GHttpSession extends AbstractSession {

  private static final long SESSION_LIFETIME = 10 * 60 * 1000;

  private String ghttpSessionId;
  private long lastAccess;

  private final Map replies = new ReplyMap();

  public GHttpSession(String ghttpSessionId, String user, String ip) {
    super((ListenPort)CaProfile.MULTIPLE.getListenPorts().get(0), ProxyConfig.getInstance().getDefaultMsgListener());
    this.ghttpSessionId = ghttpSessionId;
    this.user = user;
    this.loginName = user;
    this.remoteAddress = ip;
  }

  protected int sendEcmReplyNative(CamdNetMessage ecmRequest, CamdNetMessage ecmReply) {
    ecmReply.setSent(remoteAddress, ecmReply.getCustomData(), "GHttp");
    endTransaction(ecmRequest, ecmReply, 0);
    synchronized(replies) {
      replies.put(ecmRequest, ecmReply);
      replies.notify();
    }
    return 0;
  }

  public CamdNetMessage waitForReply(CamdNetMessage request) {
    synchronized(replies) {
      if(replies.containsKey(request)) return (CamdNetMessage)replies.get(request);
      try {
        long start = System.currentTimeMillis();
        long maxWait = request.getMaxWait();
        CamdNetMessage reply = null;
        while(System.currentTimeMillis() - start < maxWait && reply == null) {
          replies.wait(maxWait);
          reply = (CamdNetMessage)replies.get(request);
        }
        return reply;
      } catch(InterruptedException e) {
        e.printStackTrace();
      }
      return null;
    }
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

  public void run() {}

  public String getGhttpSessionId() {
    return ghttpSessionId;
  }

  public void touch() {
    lastAccess = System.currentTimeMillis();
  }

  public boolean isExpired() {
    return System.currentTimeMillis() - lastAccess > SESSION_LIFETIME;
  }

  static class ReplyMap extends LinkedHashMap {
    protected boolean removeEldestEntry(Map.Entry eldest) {
      return System.currentTimeMillis() - ((CamdNetMessage)eldest.getKey()).getTimeStamp() > 10000;
    }
  }
}
