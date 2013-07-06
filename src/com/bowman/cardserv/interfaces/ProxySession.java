package com.bowman.cardserv.interfaces;

import com.bowman.cardserv.*;
import com.bowman.cardserv.tv.TvService;


import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2007-mar-04
 * Time: 15:23:32
 */
public interface ProxySession {

  static final long ACTIVE_TIMEOUT = 45 * 1000;

  int getId();
  boolean isActive();
  String getUser();
  String getLoginName();
  boolean isTempUser();
  String getClientId();
  String getProtocol();
  ListenPort getListenPort();
  String getLabel();

  TvService getCurrentService();
  TvService getLastTransactionService();

  int getEmmCount();
  int getEcmCount();
  int getMsgCount();
  int getMaxSessions();
  int getPendingCount();
  int getKeepAliveCount();
  int getAverageEcmInterval();
  int getTransactionTime();
  long getIdleTime();
  int getLastTransactionTime();
  String getLastTransactionFlags();
  String getLastContext();

  void setFlag(CamdNetMessage request, char f);
  long getConnectTimeStamp();

  void addCamdMessageListener(CamdMessageListener listener);
  void addTransactionListener(EcmTransactionListener listener);
  void close();
  boolean isConnected();

  String getRemoteAddress();
  String getProfileName();
  CaProfile getProfile();

  Set getAllowedConnectors();

  int sendMessage(CamdNetMessage msg);
  int sendEcmReply(CamdNetMessage ecmRequest, CamdNetMessage ecmReply);
  boolean isInterested(CamdNetMessage request);

  long getLastZapTimeStamp();
}
