package com.bowman.cardserv.cws;

import com.bowman.cardserv.CamdNetMessage;
import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.interfaces.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Feb 16, 2008
 * Time: 12:18:45 PM
 */
public class QueueEntry {

  CamdNetMessage request, reply;
  private ProxySession targetSession;
  private Thread senderThread;

  private long timeStamp, sentTimeStamp, duration;

  public QueueEntry(CamdNetMessage request, ProxySession targetSession, Thread senderThread) {
    this.request = request;
    this.targetSession = targetSession;
    this.senderThread = senderThread;
    this.timeStamp = System.currentTimeMillis();
  }

  public long getTimeStamp() {
    return timeStamp;
  }

  public CamdNetMessage getRequest() {
    return request;
  }

  public ProxySession getTargetSession() {
    return targetSession;
  }

  public boolean isKeepAlive() {
    return request.isKeepAlive();
  }

  public boolean isMe(Thread thread) {
    return senderThread == thread;
  }

  public void timeOut(CwsConnector conn, char flag) {
    setReply(request.getEmptyReply());
    reply.setConnectorName(conn.getName());
    if(targetSession != null) {
      targetSession.setFlag(request, flag);
      targetSession.sendEcmReply(request, reply);
    }
  }

  public void setFlag(char flag) {
    if(targetSession != null) {
      targetSession.setFlag(request, flag);
    }
  }

  public void setReply(CamdNetMessage reply) {
    if(reply.getServiceId() == 0) reply.setServiceId(request.getServiceId());
    if(reply.getProfileName() == null) reply.setProfileName(request.getProfileName());
    this.reply = reply;
    duration = System.currentTimeMillis() - sentTimeStamp;
    request.setCWSTime(duration);
  }

  public long getDuration() {
    if(duration == 0) return System.currentTimeMillis() - timeStamp; // duration so far, transaction in progress
    else return duration; // final duration
  }

  public CamdNetMessage getReply() {
    return reply;
  }

  public long setSent() {
    sentTimeStamp = System.currentTimeMillis();

    long queueTime = sentTimeStamp - timeStamp;
    request.setQueueTime(queueTime);
    return queueTime;
  }

  public boolean isProbe() {
    return request.isEcm() && targetSession == null;
  }

  public boolean isStillValid() {
    return targetSession == null || targetSession.isInterested(request);
  }

  public boolean isSuccessful(long maxTime) {
    if(reply == null) return false;
    else if(reply.isEmpty()) return false;
    else if(duration >= maxTime) return false;
    return true;
  }

  public boolean equals(Object o) {
    if(this == o) return true;
    if(o == null || getClass() != o.getClass()) return false;
    QueueEntry that = (QueueEntry) o;
    return request.equals(that.request);
  }

  public int hashCode() {
    return request.hashCode();
  }

  public String toString() {
    return "[" + request.getSequenceNr() + " " + Integer.toHexString(request.getServiceId()) + " " +
        DESUtil.intToHexString(request.getNetworkId(), 4) + " " + DESUtil.intToHexString(request.getCaId(), 4) +
        "] " + (request.getProviderIdent()<=0?"":"["+ DESUtil.intToByteString(request.getProviderIdent(), 3) + "] ") +
        request.getCommandName() + "\t" + request.hashCodeStr() + " [" + getDuration() + " ms] \t[" +
        ((isStillValid()?"valid ":"") + (request.getServiceId() == 0?"bc ":"") + (reply != null && reply.isEmpty()?"empty":"")).trim() +
        "]" + (targetSession == null?"":"\t" + targetSession);
  }

  public void sendReply() {
    if(targetSession != null) {
      targetSession.sendEcmReply(request, new CamdNetMessage(reply));
      targetSession = null;
    }
  }
}
