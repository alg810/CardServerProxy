package com.bowman.cardserv.session;

import com.bowman.cardserv.*;
import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.crypto.DESUtil;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Feb 16, 2008
 * Time: 9:23:26 AM
 */
public class EcmTransaction {

  private Set trFlags = new LinkedHashSet();

  private CamdNetMessage request;
  private CamdNetMessage reply;
  private int duration;
  private String profileName;

  public EcmTransaction(CamdNetMessage request) {
    this.request = request;
    this.profileName = request.getProfileName();
  }

  public long getReadTime() {
    return request.getTimeStamp();
  }

  public int getServiceId() {
    return request.getServiceId();
  }

  public int getReplyServiceId() {
    if(reply != null) return reply.getServiceId();
    else return -1;
  }

  public int getDuration() {
    return duration;
  }

  public String getProfileName() {
    return profileName;
  }

  public String getFlags() {
    if(trFlags.isEmpty()) return "";
    StringBuffer sb = new StringBuffer();
    for(Iterator iter = trFlags.iterator(); iter.hasNext();) sb.append(iter.next());
    return sb.toString();
  }

  public void setFlag(char f) {
    trFlags.add(String.valueOf(f));
  }

  public void end(CamdNetMessage reply, int status) {
    long now = System.currentTimeMillis();
    duration = (int)(now - request.getTimeStamp());
    request.setClientTime(now - reply.getTimeStamp());
    if(reply.isInstant()) setFlag('I');
    if(reply.isEmpty()) setFlag('E');
    if(status < 0) setFlag('D');
    this.reply = reply;
  }

  public String getReplyData() {
    if(reply == null) return null;
    else return DESUtil.bytesToString(reply.getCustomData());
  }

  public String getProviderContext() {
    if(reply == null) return null;
    else {
      Set providers = reply.getProviderContext();
      if(providers == null || providers.isEmpty()) return null;
      else return (String)providers.iterator().next();
    }
  }

  public void setRequest(CamdNetMessage request) {
    this.request = request;
  }  

  public CamdNetMessage getRequest() {
    return request;
  }

  public CamdNetMessage getReply() {
    return reply;
  }

  public Properties getTimings() {
    Properties p = new Properties();
    p.setProperty("time-cache", String.valueOf(request.getCacheTime()));
    p.setProperty("time-queue", String.valueOf(request.getQueueTime()));
    p.setProperty("time-cws", String.valueOf(request.getCWSTime()));
    p.setProperty("time-client", String.valueOf(request.getClientTime()));
    return p;
  }

  public String getConnectorName() {
    if(reply != null && reply.getConnectorName() != null) return reply.getConnectorName();
    else if(request != null && request.getConnectorName() != null) return request.getConnectorName();
    else return null;
  }

  public TvService getService() {
    return ProxyConfig.getInstance().getService(request);
  }
}
