package com.bowman.cardserv.rmi;

import com.bowman.cardserv.interfaces.CwsConnector;
import com.bowman.cardserv.ProxyConfig;

import java.io.Serializable;
import java.util.Properties;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 25, 2005
 * Time: 9:01:08 AM
 */
public class CwsStatus extends AbstractStatus implements Serializable {

  private static final long serialVersionUID = -1540786884183518436L;

  public static final int CWS_DISCONNECTED = 0, CWS_CONNECTED = 1, CWS_CONNECTING = 2;
  public static final int CWS_UNRESPONSIVE = 4, CWS_DISABLED = 5;

  private final String name, profileName, protocol;
  private String remoteHost, remoteUser;
  private String cardData1, cardData2;
  private String providerIdents;

  private int status;
  private final int metric, sendQ, utilization, avgUtilization, ecmCount, ecmLoad, emmCount, timeoutCount, capacity;
  private long connectTimeStamp, currentTime, averageTime, nextAttemptTimeStamp, disconnectTimeStamp;

  private int[] recentSids;

  public CwsStatus(CwsConnector cws) {
    name = cws.getName();
    if(cws.getRemoteInfo() != null) setProperties(cws.getRemoteInfo());
    profileName = cws.getProfileName();
    metric = cws.getMetric();
    sendQ = cws.getQueueSize();
    utilization = cws.getUtilization(false);
    avgUtilization = cws.getUtilization(true);
    capacity = cws.getCapacity();
    ecmCount = cws.getEcmCount(true);
    ecmLoad = cws.getEcmCount(false);
    emmCount = cws.getEmmCount();
    timeoutCount = cws.getTotalFailures();
    protocol = cws.getProtocol();
    providerIdents = ProxyConfig.providerIdentsToString(cws.getProviderIdents());
    if(!cws.isEnabled()) status = CWS_DISABLED;
    else {
      if(cws.isReady()) {
        status = CWS_CONNECTED;
        remoteHost = cws.getRemoteAddress();
        remoteUser = cws.getUser();
        if(cws.getRemoteCard() != null && "Newcamd".equals(cws.getProtocol())) {
          cardData1 = cws.getRemoteCard().toString();
          if(!cws.getRemoteCard().isAnonymous()) {
            cardData2 = cws.getRemoteCard().getCardNumber() + " (" + cws.getRemoteCard().getProvIdsStr() + ")";
          } else cardData2 = null;
        }
        connectTimeStamp = cws.getConnectTimeStamp();
        currentTime = cws.getCurrentEcmTime();
        averageTime = cws.getAverageEcmTime();
        recentSids = cws.getRecentSids();
        if(cws.getTimeoutCount() > 0) status = CWS_UNRESPONSIVE;
      } else if(cws.isConnecting()) {
        status = CWS_CONNECTING;
      } else {
        nextAttemptTimeStamp = ProxyConfig.getInstance().getConnManager().getNextConnectAttempt(name);
        disconnectTimeStamp = cws.getLastDisconnectTimeStamp();
      }
    }
  }

  public String getName() {
    return name;
  }

  public String getProfileName() {
    return profileName;
  }

  public int getMetric() {
    return metric;
  }

  public int getSendQ() {
    return sendQ;
  }

  public int getEcmCount() {
    return ecmCount;
  }

  public int getEcmLoad() {
    return ecmLoad;
  }

  public int[] getRecentSids() {
    return recentSids;
  }

  public int getEmmCount() {
    return emmCount;
  }

  public int getTimeoutCount() {
    return timeoutCount;
  }

  public String getRemoteHost() {
    return remoteHost;
  }

  public String getRemoteUser() {
    return remoteUser;
  }

  public int getStatus() {
    return status;
  }

  public long getConnectTimeStamp() {
    return connectTimeStamp;
  }

  public long getNextAttemptTimeStamp() {
    return nextAttemptTimeStamp;
  }

  public long getDisconnectTimeStamp() {
    return disconnectTimeStamp;
  }

  public long getCurrentEcmTime() {
    return currentTime;
  }

  public int getCapacity() {
    return capacity;
  }

  public long getAverageEcmTime() {
    return averageTime;
  }

  public int getUtilization() {
    return utilization;
  }

  public int getAvgUtilization() {
    return avgUtilization;
  }

  public String getProtocol() {
    return protocol;
  }

  public String getCardData1() {
    return cardData1;
  }

  public String getCardData2() {
    return cardData2;
  }

  public Properties getRemoteInfo() {
    return data;
  }

  public String getProviderIdents() {
    return providerIdents;
  }
}
