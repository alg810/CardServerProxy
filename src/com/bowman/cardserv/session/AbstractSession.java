package com.bowman.cardserv.session;

import com.bowman.cardserv.util.*;
import com.bowman.cardserv.*;
import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.interfaces.*;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2007-mar-04
 * Time: 17:30:48
 */
public abstract class AbstractSession implements CamdConstants, ProxySession, Runnable {

  protected static final int MAX_LOG_SIZE = 100;

  private Set msgListeners = new HashSet(), trListeners = new HashSet();

  int sessionId, maxPending, maxSessions;
  String user, loginName, clientId;
  String remoteAddress;

  Set allowedServices, blockedServices, allowedConnectors;
  int allowedRate;

  private long connectTimeStamp, lastZapTimeStamp;
  int msgCount, ecmCount;

  private final Map transactions = new LinkedHashMap();
  private EcmTransaction lastTransaction;
  private EcmTransaction currentTransaction;

  Thread sessionThread;
  private TimedAverageList avgList;

  ListenPort listenPort;
  ProxyLogger logger;

  boolean alive;

  AbstractSession(ListenPort listenPort, CamdMessageListener listener) {
    this.listenPort = listenPort;
    this.sessionId = SessionManager.getInstance().getNewSessionId();
    if(listener != null) addCamdMessageListener(listener);
    logger = ProxyLogger.getLabeledLogger(getClass().getName(), getLabel());
    avgList = new TimedAverageList(60);

    maxPending = ProxyConfig.getInstance().getMaxPending();
    connectTimeStamp = System.currentTimeMillis();
  }

  public int getId() {
    return sessionId;
  }

  public void addCamdMessageListener(CamdMessageListener listener) {
    msgListeners.add(listener);
  }

  public void fireCamdMessage(CamdNetMessage msg, boolean sent) {
    if(msg == null) return;
    for(Iterator iter = msgListeners.iterator(); iter.hasNext(); ) {
      try {
        if(!sent) {
          if(msg.isEcm()) {            
            synchronized(transactions) {

              if(transactions.size() >= maxPending) {
                if(ProxyConfig.getInstance().isDebug())
                  logger.warning("max-pending exceeded for user '" + user + "', replying with empty...");
                else logger.fine("max-pending exceeded for user '" + user + "', replying with empty...");
                try {
                  Thread.sleep(100);
                  sendEcmReplyNative(msg, msg.getEmptyReply());
                } catch (InterruptedException e) {}
                msg.setFilteredBy("Max-pending exceeded");
              } else if(transactions.containsKey(msg)) {
                // client sent the same request again before receiving a reply or timeout to its previous attempt
                // change the request in the transaction so that the reply will have the last sequence number the
                // client sent, causing previous sequence numbers to be silently ignored
                if(ProxyConfig.getInstance().isDebug())
                  logger.warning("Duplicate request received for user '" + user +"', already pending: " + msg);
                else logger.fine("Duplicate request received for user '" + user +"', already pending: " + msg);
                currentTransaction = getTransaction(msg);
                currentTransaction.setRequest(msg);
                msg.setFilteredBy("Duplicate ECM");
              } else {
                String profileName = getProfileName();
                if(getProfile() == CaProfile.MULTIPLE) {
                  CaProfile profile = ProxyConfig.getInstance().getProfileById(msg.getNetworkId(), msg.getCaId());
                  if(profile != null) profileName = profile.getName();
                }
                msg.setProfileName(profileName);
                currentTransaction = new EcmTransaction(msg);
                transactions.put(msg, currentTransaction);
              }
              
            }
          }
          ((CamdMessageListener)iter.next()).messageReceived(this, msg);
        } else ((CamdMessageListener)iter.next()).messageSent(this, msg);
      } catch (Throwable t) {
        logger.severe("Exception in CamdNetMessage dispatching: " + t, t);
      }
    }
  }

  public void addTransactionListener(EcmTransactionListener listener) {
    trListeners.add(listener);
  }

  void fireTransactionCompleted(EcmTransaction transaction) {
    if(!getProfile().isDebug()) return;
    for(Iterator iter = trListeners.iterator(); iter.hasNext(); ) {
      try {
        ((EcmTransactionListener)iter.next()).transactionCompleted(transaction, this);
      } catch (Throwable t) {
        logger.severe("Exception in EcmTransaction dispatching: " + t, t);
      }
    }
  }

  boolean checkLimits(CamdNetMessage msg) {
    if(msg.isEcm()) {
      long now = System.currentTimeMillis();
      long interval = now - (lastTransaction==null?connectTimeStamp:lastTransaction.getReadTime());

      int size = avgList.size(true);
      if(size < 1) size = 1;
      int rate = avgList.getMaxAge() / size;

      // int rate = avgList.getAverage(true);

      if(allowedRate != -1) {
        if(rate > 0 && rate < allowedRate) {
          logger.warning(this + " for user '" + user + "' exceeded rate limit: " + rate +
              " (limit: " + allowedRate + ")");
          msg.setFilteredBy("Rate limit exceeded: " + rate + " > " + allowedRate);
          return false;
        }
      }

      if(interval < 60000) avgList.addRecord(now, (int)interval); // avoid extreme entries after standby/idletime

      if(allowedServices != null) {
        if(!allowedServices.contains(new Integer(msg.getServiceId()))) {
          String name = ProxyConfig.getInstance().getServiceName(getProfile().getName(), msg.getServiceId());
          logger.info(name + " blocked for: " + this);
          msg.setFilteredBy("Service not in allow list: " + name);
          return false;
        }
      }
      if(blockedServices != null) {
	      if(blockedServices.contains(new Integer(msg.getServiceId()))) {
          String name = ProxyConfig.getInstance().getServiceName(getProfile().getName(), msg.getServiceId());
          logger.info(name + " blocked for: " + this);
          msg.setFilteredBy("Service in block list: " + name);
          return false;
        }
      }
    }
    return true;
  }

  public boolean isActive() {
     return getTrIdleTime() < ACTIVE_TIMEOUT;
  }

  long getTrIdleTime() {
    if(currentTransaction == null) return System.currentTimeMillis() - connectTimeStamp;
    else return System.currentTimeMillis() - currentTransaction.getReadTime();
  }

  public long getIdleTime() {
    return getTrIdleTime();
  }

  public String getUser() {
    return user;
  }

  public String getLoginName() {
    return loginName;
  }

  public boolean isTempUser() {
    if(loginName == null) return false;
    else return !loginName.equalsIgnoreCase(user);
  }

  public String getClientId() {
    return clientId;
  }

  public ListenPort getListenPort() {
    return listenPort;
  }

  public TvService getCurrentService() {
    if(currentTransaction == null) return ProxyConfig.getInstance().getService(getProfileName(), -1);
    else return currentTransaction.getService();
  }

  public TvService getLastTransactionService() {
    if(lastTransaction == null) return ProxyConfig.getInstance().getService(getProfileName(), -1);
    else return lastTransaction.getService();
  }

  public int getPendingCount() {
    return transactions.size();
  }

  public int getKeepAliveCount() {
    return 0;
  }

  public int getMsgCount() {
    return msgCount;
  }

  public int getEcmCount() {
    return ecmCount;
  }

  public int getEmmCount() {
    return 0;
  }

  public int getAverageEcmInterval() {
    if(ecmCount < 1) return -1;
    else {
      int current = avgList.getAverage(true);
      if(current == 0) return avgList.getAverage(false) / 1000; // return last known interval, rather than 0 or -1
      else return current / 1000;
    }
  }

  public int getTransactionTime() {
    return currentTransaction == null?-1:(int)(System.currentTimeMillis() - currentTransaction.getReadTime());
  }

  public int getLastTransactionTime() {
    return lastTransaction == null?-1:lastTransaction.getDuration();
  }

  public String getLastTransactionFlags() {
    return lastTransaction == null?"":lastTransaction.getFlags();
  }

  public String getRemoteAddress() {
    if(remoteAddress == null) return "0.0.0.0";
    else return remoteAddress;
  }

  public void setFlag(CamdNetMessage request, char f) {
    if(request.isEcm()) {
      synchronized(transactions) {
        if(!transactions.containsKey(request)) {
          logger.fine("Attempt to set flag '" + f + "' on non-existant transaction: " + request);
        } else {
          getTransaction(request).setFlag(f);
          if(f == 'Z') lastZapTimeStamp = System.currentTimeMillis();
        }
      }
    }
  }

  EcmTransaction getTransaction(CamdNetMessage request) {
    return (EcmTransaction)transactions.get(request);
  }

  public boolean isInterested(CamdNetMessage ecmRequest) {
    if(!isConnected()) return false;
    else return transactions.containsKey(ecmRequest);
  }

  public int sendEcmReply(CamdNetMessage ecmRequest, CamdNetMessage ecmReply) {
    if(!isInterested(ecmRequest)) {
      logger.fine("Not interested in reply: " + ecmReply);
      return -1;      
    } else {

      if(ecmReply.getCaId() != 0 && (ecmRequest.getCaId() != ecmReply.getCaId())) {
        setFlag(ecmRequest, 'M');
        String s = "Ca-id mismatch, response (from " + ecmReply.getConnectorName() + ") had ca-id " + Integer.toHexString(ecmReply.getCaId()) +
            " but request was for profile: " + ecmRequest.getProfileName() + " (from user '" + user + "' - " + clientId + ")";
        if(ProxyConfig.getInstance().isBlockCaidMismatch()) {
          ecmReply = ecmRequest.getEmptyReply();
          logger.warning(s);
        } else logger.fine(s);
      } else if(ecmReply.getProfileName() != null && !ecmReply.getProfileName().equals(ecmRequest.getProfileName())) {
        setFlag(ecmRequest, 'M');
        String s = "Profile mismatch, response (from " + ecmReply.getConnectorName() + ") had profile " + ecmReply.getProfileName() +
            " but request was for profile: " + ecmRequest.getProfileName() + " (from user '" + user + "' - " + clientId + ")";
        if(ProxyConfig.getInstance().isBlockCaidMismatch()) {
          ecmReply = ecmRequest.getEmptyReply();
          logger.warning(s);
        } else logger.fine(s);
      }

      ecmRequest = ((EcmTransaction)transactions.get(ecmRequest)).getRequest(); // make sure original instance is used
      if(ecmRequest.getCommandTag() != ecmReply.getCommandTag()) { // ensure table id is same as it was in request
        logger.fine("Table-id mismatch, response (from " + ecmReply.getConnectorName() + ") had table-id " +
            Integer.toHexString(ecmReply.getCommandTag()) + " but request had " + Integer.toHexString(ecmRequest.getCommandTag()) +
            " (from user '" + user + "' - " + clientId + ")");
        ecmReply.setCommandTag(ecmRequest.getCommandTag());
      }
      return sendEcmReplyNative(ecmRequest, ecmReply);
    }
  }

  protected abstract int sendEcmReplyNative(CamdNetMessage ecmRequest, CamdNetMessage ecmReply);

  void endTransaction(CamdNetMessage request, CamdNetMessage reply, int status) {
    synchronized(transactions) {
      EcmTransaction tr = getTransaction(request);
      if(tr == null) {
        if(lastTransaction != null && lastTransaction.getRequest().equals(request)) {
          logger.fine("Attempt to end already ended transaction: " + request + " (" + lastTransaction.getFlags() + ")");
        } else logger.fine("Attempt to end non-existant transaction: " + request);
      } else {
        tr.end(reply, status);
        transactions.remove(request);
        lastTransaction = tr;
        fireTransactionCompleted(tr);
      }
      if(status != -1) fireCamdMessage(reply, true);
    }
  }

  void endSession() {
    String summary = (getUser()==null?"ended":getUser() + " - ended") + " [pending transactions: " + transactions.size() +
      " idle time: " + getTrIdleTime() + " ecm count: " + ecmCount + " total count: " + msgCount + "]";
    if(getUser() != null) {
      if(ProxyConfig.getInstance().getUserManager().isDebug(getUser())) logger.info(summary);
    } else logger.fine(summary);
    SessionManager.getInstance().removeSession(this);
    transactions.clear();
    sessionThread = null;
  }

  public long getConnectTimeStamp() {
    return connectTimeStamp;
  }

  public long getLastZapTimeStamp() {
    return lastZapTimeStamp;
  }

  public Set getAllowedConnectors() {
    return allowedConnectors;
  }

  public int getMaxSessions() {
    return maxSessions;
  }

  public CaProfile getProfile() {
    return listenPort.getProfile();
  }

  public String getProfileName() {
    if(getProfile() == null) return "?";
    else return getProfile().getName();
  }

  public String getLabel() {
    return getProtocol() + "Session[" + sessionId + ":" + getProfileName() + "]";
  }

  public abstract String getProtocol();

  public String toString() {
    return getUser() + " (" + getLabel() + ")";
  }
  
}
