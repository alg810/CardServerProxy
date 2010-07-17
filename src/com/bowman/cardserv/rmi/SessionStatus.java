package com.bowman.cardserv.rmi;

import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.interfaces.ProxySession;
import com.bowman.cardserv.session.NewcamdSession;

import java.io.Serializable;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2007-jan-16
 * Time: 10:06:30
 */
public class SessionStatus extends AbstractStatus implements Serializable {

  private final long connectTimeStamp, lastZapTimeStamp, idleTime;
  private final int ecmCount, emmCount, msgCount, avgEcmInterval, lastTransactionTime, pendingCount, sessionId, kaCount;
  private final String remoteHost, profileName, clientId, protocol, flags, context;
  private final TvService currentService, lastService;
  private final boolean active;
  private final int maxSessions;
  
  public SessionStatus(ProxySession session) {
    active = session.isActive();
    connectTimeStamp = session.getConnectTimeStamp();
    currentService = session.getCurrentService();
    lastService = session.getLastTransactionService();
    remoteHost = session.getRemoteAddress();
    profileName = session.getProfileName();
    clientId = session.getClientId();
    protocol = session.getProtocol();
    emmCount = session.getEmmCount();
    ecmCount = session.getEcmCount();
    msgCount = session.getMsgCount();
    maxSessions = session.getMaxSessions();
    // if(session instanceof NewcamdSession) 
    kaCount = session.getKeepAliveCount();
    pendingCount = session.getPendingCount();
    sessionId = session.getId();
    avgEcmInterval = session.getAverageEcmInterval();
    lastTransactionTime = session.getLastTransactionTime();
    lastZapTimeStamp = session.getLastZapTimeStamp();
    idleTime = session.getIdleTime();
    flags = session.getLastTransactionFlags();
    context = session.getLastContext();
  }

  public boolean isActive() {
    return active;
  }

  public long getConnectTimeStamp() {
    return connectTimeStamp;
  }

  public int getEcmCount() {
    return ecmCount;
  }

  public int getEmmCount() {
    return emmCount;
  }

  public int getMsgCount() {
    return msgCount;
  }

  public int getPendingCount() {
    return pendingCount;
  }

  public int getKaCount() {
    return kaCount;
  }

  public int getAvgEcmInterval() {
    return avgEcmInterval;
  }

  public int getLastTransactionTime() {
    return lastTransactionTime;
  }

  public String getFlags() {
    return flags;
  }

  public String getRemoteHost() {
    return remoteHost;
  }

  public String getProfileName() {
    return profileName;
  }

  public String getClientId() {
    return clientId;
  }

  public String getProtocol() {
    return protocol;
  }

  public TvService getCurrentService() {
    return currentService;
  }

  public TvService getLastService() {
    return lastService;
  }

  public long getLastZapTimeStamp() {
    return lastZapTimeStamp;
  }

  public long getIdleTime() {
    return idleTime;
  }

  public String getContext() {
    return context;
  }

  public int getSessionId() {
    return sessionId;
  }

  public int getMaxSessions() {
    return maxSessions;
  }
}
