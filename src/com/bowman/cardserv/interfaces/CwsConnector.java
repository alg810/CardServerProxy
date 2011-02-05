package com.bowman.cardserv.interfaces;

import com.bowman.cardserv.*;
import com.bowman.cardserv.cws.CwsConnectorManager;

import java.io.IOException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2007-apr-26
 * Time: 12:45:48
 */
public interface CwsConnector extends XmlConfigurable {

  String getName();
  CaProfile getProfile();
  String getProfileName();

  int getMetric();
  void setMetric(int metric);
  String getUser();
  boolean isEnabled();
  void setEnabled(boolean enabled);
  boolean isConnecting();

  int getTimeoutCount();
  int getTotalFailures();
  int getEcmCount(boolean total);
  int[] getRecentSids();
  int getEmmCount();
  int getKeepAliveCount();
  int getCurrentEcmTime();
  int getAverageEcmTime();
  int getCapacity();
  int getQueueSize();
  long getLastEcmTimeStamp();
  long getLastTrafficTimeStamp();
  long getLastAttemptTimeStamp();
  long getLastDisconnectTimeStamp();

  int getUtilization(boolean average);

  long getConnectTimeStamp();
  boolean sendEcmRequest(CamdNetMessage request, ProxySession listener);
  boolean isReady();
  boolean isConnected();
  boolean isPending(CamdNetMessage request);
  boolean isBlackListed(CamdNetMessage request);
  boolean canDecode(CamdNetMessage request);

  CardData getRemoteCard();
  Set getProviderIdents();
  Properties getRemoteInfo();

  int sendMessage(CamdNetMessage msg);
  void sendKeepAlive();
  boolean connect(CwsConnectorManager manager) throws IOException;
  void close();
  void reset();

  boolean isAuAllowed(String userName);
  String[] getAuUsers();

  int getKeepAliveInterval();
  int getEstimatedQueueTime();

  String getLabel();
  String getProtocol();
  String getRemoteAddress();


}
