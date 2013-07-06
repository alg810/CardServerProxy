package com.bowman.cardserv.cws;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.*;
import com.bowman.cardserv.crypto.DESUtil;

import java.util.*;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2007-mar-04
 * Time: 18:27:09
 * @noinspection SynchronizeOnNonFinalField
 */
public abstract class AbstractCwsConnector implements Comparable, Runnable, CwsConnector {

  private static final int RAMPUP_ECM_TIME = 200, BEST_ECM_TIME = 50;

  protected static final int LOGIN_SO_TIMEOUT = 30 * 1000;
  protected static final int SESSION_SO_TIMEOUT = 0;
  protected static final int CONNECT_TIMEOUT = 10 * 1000;
  protected static final int MIN_PROBE_INTERVAL = 3 * 1000;

  protected String name, host;
  protected int port, qosClass, timeoutCount,  minDelay;
  private int metric, maxQueue, ecmCount, successEcmCount, totalFailures;
  protected boolean enabled, noProfile, asynchronous = false, replyPlugins = true;
  protected long connectTimeStamp, lastEcmTimeStamp, lastDisconnectTimeStamp;
  private long totalTime, lastReadTimeStamp, lastAttemptTimeStamp;

  protected Thread readerThread;
  private Thread timeoutThread;
  private CacheHandler cacheHandler;
  private TimedAverageList avgList = new TimedAverageList(60), sidList = new TimedAverageList(60);

  protected Set auUsers = new HashSet(), providerIdents;
  protected CaProfile profile;
  protected ProxyLogger logger;
  protected ProxyConfig config;
  protected CwsConnectorManager connManager;

  private List sendQueue = Collections.synchronizedList(new ArrayList());
  private List blackList = Collections.synchronizedList(new ArrayList());
  private List replyQueue = new ArrayList();
  private Map sentMap = Collections.synchronizedMap(new LinkedHashMap());
  private Set sentSet = Collections.synchronizedSet(new HashSet());
  protected Set predefinedProviders = new HashSet();

  protected QueueEntry lastSent;
  protected boolean connecting, alive;

  public AbstractCwsConnector() {
    lastEcmTimeStamp = System.currentTimeMillis();     
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    config = ProxyConfig.getInstance();
    name = xml.getStringValue("name");
    metric = xml.getIntValue("metric", 1);
    maxQueue = xml.getIntValue("max-queue", config.getDefaultConnectorMaxQueue());
    minDelay = xml.getTimeValue("min-delay", config.getDefaultConnectorMinDelay(), "ms");

    try {
      String qosClassStr = xml.getStringValue("qos-class");
      if("none".equalsIgnoreCase(qosClassStr) || qosClassStr.length() == 0) qosClass = -1;
      else qosClass = xml.getIntValue("qos-class");
    } catch (ConfigException e) {
      qosClass = 0x10; // minimize delay
    }

    String profileName = null;
    try {
      profileName = xml.getStringValue("profile");
      profile = config.getProfile(profileName);
    } catch (ConfigException e) {}

    if(profileName != null && this instanceof MultiCwsConnector)
      throw new ConfigException(xml.getFullName(), "profile", "Illegal to have profile specified for connector type: " + getProtocol());

    if(profileName != null && profile == null) {
      if("true".equalsIgnoreCase(xml.getStringValue("enabled", "true"))) {
        if(!config.isProfileDisabled(profileName)) // profile doesnt exist
          throw new ConfigException(xml.getFullName(), "profile", "Unknown profile for '" + name +"': " + profileName);
        else {
          if(logger != null) logger.warning("Profile '" + profileName + "' disabled, disabling connector as well.");
          xml.setStringOverride("enabled", "false"); // profile is disabled, force disable for connector
        }
      }
    }

    if("true".equalsIgnoreCase(xml.getStringValue("enabled", "true"))) providersUpdated(xml);
    else predefinedProviders.clear();

    noProfile = (profileName == null);
    replyPlugins = true; // assume there are replyFilter plugins until otherwise discovered

    logger = ProxyLogger.getLabeledLogger(getClass().getName(), getLabel());
  }

  public void providersUpdated(ProxyXmlConfig xml) throws ConfigException {
    predefinedProviders.clear();
    String[] providerIdents = xml.getStringValue("provider-idents", "").split(",");
    for(int i = 0; i < providerIdents.length; i++) {
      providerIdents[i] = providerIdents[i].trim();
      try {
        if(providerIdents[i].length() > 0) {
          predefinedProviders.add(new Integer(DESUtil.byteStringToInt(providerIdents[i])));
        }
      } catch (NumberFormatException e) {
        throw new ConfigException(xml.getFullName(), "provider-idents", "Bad provider value: " + e.getMessage());
      }
    }
  }

  public String getName() {
    return name;
  }

  public int getMetric() {
    return metric;
  }

  public void setMetric(int metric) {
    this.metric = metric;
  }

  public String getUser() {
    return null;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
    if(enabled && connManager != null) {
      synchronized(connManager) {
        connManager.notifyAll();
      }
    }
  }

  public long getConnectTimeStamp() {
    return connectTimeStamp;
  }

  public int getTimeoutCount() {
    return timeoutCount;
  }

  public int getEcmCount(boolean total) {
    if(total) return ecmCount;
    else {
      if(successEcmCount == 0) return 0;
      else return avgList.size(connManager.getMaxCwWait(profile)); // ecmCount over the last cw period secs
    }
  }

  public int[] getRecentSids() {
    if(successEcmCount == 0) return new int[0];
    else {
      Set sids = sidList.getCurrentSet(connManager.getMaxCwWait(profile) + getAverageEcmTime());
      int[] result = new int[sids.size()];
      int i = 0;
      for(Iterator iter = sids.iterator(); iter.hasNext(); ) result[i++] = ((Integer)iter.next()).intValue();
      return result;
    }
  }

  public int getEmmCount() {
    return 0;
  }

  public int getKeepAliveCount() {
    return 0;
  }

  public int getKeepAliveInterval() {
    return -1;
  }

  public int getTotalFailures() {
    return totalFailures;
  }

  public CaProfile getProfile() {
    return profile;
  }

  public String getProfileName() {
    if(profile == null) return "?";
    else return profile.getName();
  }

  public int getCurrentEcmTime() {
    if(successEcmCount == 0) return -1;
    else return avgList.getAverage(false);
  }

  public int getCapacity() {
    int time = getAverageEcmTime();
    if(time == -1 || !isConnected()) return -1;
    else {
      int lowest = avgList.getLowest();
      if(lowest < time) time = lowest; // use the most optimistic processing time for the capacity estimate
      if(time == 0) time = 1;
      return (int)(connManager.getMaxCwWait(profile) / time);
    }
  }

  public int getAverageEcmTime() {
    if(successEcmCount == 0) return -1;
    else return (int)(totalTime / successEcmCount);
  }

  public int getUtilization(boolean average) {
    if(successEcmCount == 0) return 0;
    else {
      double duration = average?System.currentTimeMillis() - connectTimeStamp:avgList.getMaxAge(); // time since connect, or last min
      double totalEcmTime;
      if(!asynchronous) totalEcmTime = average?successEcmCount * getAverageEcmTime():avgList.getTotal(true); // all * avg time, or total time for last min
      else totalEcmTime = average?successEcmCount * getAverageEcmTime():avgList.size(true) * avgList.getLowest();

      if(totalEcmTime <= 0) return 0;
      double percent = totalEcmTime / duration;
      return (int)(percent * 100);
    }
  }

  public int getEstimatedQueueTime() {
    if(successEcmCount == 0) return RAMPUP_ECM_TIME;
    else if(config.isCatchAll() && getQueueSize() == 0) return BEST_ECM_TIME; // be optimistic
    else return (getQueueSize() + 1) * getCurrentEcmTime();
  }

  public synchronized boolean connect(CwsConnectorManager manager) throws IOException {
    if(!enabled || isConnected()) return false;

    if(connManager != null) {
      // this is a re-connect attempt, wait an extra 3 secs before proceeding.
      try {
        Thread.sleep(MIN_PROBE_INTERVAL);
      } catch (InterruptedException e) {
        return false;
      }
    }

    lastAttemptTimeStamp = System.currentTimeMillis();

    try {
      if(host != null)
        logger.info("Connecting to " + com.bowman.cardserv.util.CustomFormatter.formatAddress(host) + ":" + port + " ...");
      connManager = manager;
      cacheHandler = ProxyConfig.getInstance().getCacheHandler();
      reset();
      connecting = true;

      connectNative();

      timeoutCount = 0;
      ecmCount = 0;
      successEcmCount = 0;
      totalTime = 0;
      avgList.clear();

      readerThread = new Thread(this, getProtocol() + "CwsConnectorReaderThread-" + name);
      readerThread.start();

      timeoutThread = new Thread("CwsConnectorDispatcherThread-" + name) {
        public void run() {
          while(Thread.currentThread() == timeoutThread) {
            try {
              checkSentMap(getMaxWait());
              sendReplies();
              synchronized(replyQueue) {
                try {
                  replyQueue.wait(50); // todo ? 100
                } catch (InterruptedException e) {
                  return;
                }
              }
            } catch (Exception e) {
              logger.severe("Uncaught exception in CwsConnectorDispatcherThread loop: " + e, e);
              e.printStackTrace();
            }
          }
        }
      };
      timeoutThread.start();
      
      return true;
    } catch (IOException e) {
      connecting = false;
      connManager.cwsConnectionFailed(this, e.toString());
      throw e;
    }
  }

  protected abstract void connectNative() throws IOException;

  public boolean isAuAllowed(String userName) {
    return false;
  }

  public String[] getAuUsers() {
    return new String[0];
  }

  public String getLabel() {
    return getProtocol() + "Cws[" + name + ":" + getProfileName() + "]";
  }

  protected synchronized QueueEntry getSentEntry(int sequenceNr) {
    return (QueueEntry)sentMap.remove(new Integer(sequenceNr));
  }

  protected void reportChannelStatus(QueueEntry qe) {
    ProxySession session = qe.getTargetSession();
    boolean success = !qe.getReply().isEmpty();
    if(session != null) {
      if(config.getUserManager().isMapExcluded(session.getUser())) return; // excluded user
    } else { // probe
      if(this instanceof CspCwsConnector) {
        if(success) logger.fine("Changed out-come for csp-connector probe to cannot-decode");
        success = false; // assume all probes to cspcws fail
      }
    }
    connManager.reportChannelStatus(this, qe.getRequest(), success, session);
  }

  protected CamdNetMessage applyFilters(CamdNetMessage msg) {
    ProxyPlugin plugin; int count = 0;
    for(Iterator iter = config.getProxyPlugins().values().iterator(); iter.hasNext(); ) {
      plugin = (ProxyPlugin)iter.next();
      try {
        if(plugin instanceof ReplyFilter) {
          msg = ((ReplyFilter)plugin).doReplyFilter(this, msg);
          count++;
        }
        if(msg == null) break;                 
      } catch (Throwable t) {
        logger.severe("Exception in plugin filtering: " + t, t);
      }
    }
    if(count == 0) replyPlugins = false;
    return msg;
  }

  public boolean reportReply(CamdNetMessage reply) {

    // something received, cancel timeout state if set
    reply.setConnectorName(getName());
    if(timeoutCount > 0) {
      logger.info("Message received, cancelling timeout-state");
      timeoutCount = 0;
    }
    lastEcmTimeStamp = reply.getTimeStamp();
    lastReadTimeStamp = reply.getTimeStamp();

    QueueEntry qe = getSentEntry(reply.getSequenceNr());
    if(qe == null) {
      if(reply.isKeepAlive()) return false;

      if(sentMap.size() >= 1) { // check for out of sequence data, not sure why this seems to occur sometimes
        Integer i = (Integer)sentMap.keySet().iterator().next();
        qe = (QueueEntry)sentMap.get(i);
        if(qe.getRequest().getServiceId() != reply.getServiceId()) qe = null; // first pending wasn't for this reply
        else {
          sentMap.remove(i);
          logger.warning("Bad sequence nr in reply? Expected [" + i + "] Got [" + reply.getSequenceNr() + "]");
        }
      }
    }

    if(qe == null) {
      logger.warning("No request found for reply: " + reply.getSequenceNr() + " (" + reply.getCommandName() + ", " +
          config.getService(reply) + ") pending are: " + sentMap.keySet());
      return false;
    } else {
      qe.setReply(reply); // valid reply received for this queue entry

      if(replyPlugins) {
        reply = applyFilters(reply);
        if(reply == null) { // blocked by reply plugin, reinsert it in sentmap and have it time out
          sentMap.put(new Integer(reply.getSequenceNr()), qe);
          blackListRequest(qe.getRequest()); // and blacklist request to prevent resends of the same one
          return true;
        }
      }      

      if(reply.getServiceId() != 0) {
        sidList.addRecord(reply.getServiceId());        

        if(qe.getRequest().getServiceId() != 0) {
          if(connManager.isLogSidMismatch())
            if(reply.getServiceId() != qe.getRequest().getServiceId())
              logger.warning("Service id in reply [" + reply.getSequenceNr() + "] does not match that of the request: " +
                  config.getService(reply) + " vs " + config.getService(qe.getRequest()));
        } else {
          logger.fine("Request [" + reply.getSequenceNr() + "] had service id 0, but reply had: " + reply.getServiceId());
          // qe.getRequest().setServiceId(reply.getServiceId()); // client didn't know the sid, but the server did
        }
      } else logger.fine("Reply [" + reply.getSequenceNr() + "] had service id 0.");
    }

    if(!qe.isKeepAlive()) {
      reportChannelStatus(qe);
      if(qe.isSuccessful(connManager.getMaxCwWait(profile))) {
        avgList.addRecord((int)qe.getDuration());
        totalTime += qe.getDuration();
        successEcmCount++;
      }
      ecmCount++;
      logger.fine("\t" + qe);

      if(reply.isEmpty()) blackListRequest(qe.getRequest());

      if(qe.isProbe()) {
        // dont report negative probe results to cache
        if(!reply.isEmpty()) cacheHandler.processReply(qe.getRequest(), reply);
      } else {
        // not a probe, result matters to someone
        cacheHandler.processReply(qe.getRequest(), reply);
        if(reply.isEmpty()) { // this request failed, see if any probes had better luck in the meantime
          CamdNetMessage cachedReply = cacheHandler.peekReply(qe.getRequest());
          if(cachedReply != null) {
            reply = cachedReply; // found one, return this instead
            qe.getTargetSession().setFlag(qe.getRequest(), 'X');
          }
        }

        synchronized(replyQueue) {
          qe.reply = reply;
          replyQueue.add(qe);
        }

      }
    }

    synchronized(replyQueue) {
      replyQueue.notifyAll();
    }
    return true;
  }

  protected void blackListRequest(CamdNetMessage msg) {
    // if(msg.getServiceId() == 0) {
      blackList.add(msg);
      logger.fine("Blacklisting failed request: " + msg + " (list size: " + blackList.size() + ")");
    // }
    if(!blackList.isEmpty()) {
      CamdNetMessage oldest = (CamdNetMessage)blackList.get(0);
      if(System.currentTimeMillis() - oldest.getTimeStamp() > (getMaxWait() * 3)) blackList.remove(oldest);
    }
  }

  public boolean isBlackListed(CamdNetMessage request) {
    return blackList.contains(request);
  }

  public boolean canDecode(CamdNetMessage request) {
    if(request.getProviderIdent() != -1 && profile.isRequireProviderMatch())
      return getProviderIdents().contains(new Integer(request.getProviderIdent()));
    else return true; 
  }

  protected void sendReplies() {
    synchronized(replyQueue) {
      QueueEntry qe;
      for(Iterator iter = replyQueue.iterator(); iter.hasNext(); ) {
        qe = (QueueEntry)iter.next();
        iter.remove();
        try {
          sentSet.remove(qe.getRequest());
          qe.sendReply();
        } catch (Exception e) {
          logger.severe("Uncaught exception in session sendEcmReply: " + e, e);
        }
      }
    }
  }

  public void reset() {
    logger.fine("Connector reset, " + getQueueSize() + " requests affected (" + sentMap.size() +
        " sent, " + sendQueue.size() + " unsent).");
    timeoutCount = 0;
    if(!sentMap.isEmpty()) checkSentMap(-1);
    lastSent = null;
    sendQueue.clear();
    blackList.clear();
  }

  protected boolean isNext() {
    return sendQueue.size() == 0 || ((QueueEntry)sendQueue.get(0)).isMe(Thread.currentThread());
  }

  protected synchronized boolean waitForPending() {
    if(sendQueue.isEmpty()) return true;
    long maxWait = getMaxWait();
    long start = System.currentTimeMillis();
    while(sentMap.size() > 0 || !isNext()) { // block until any previously pending replies have been received
      try {
        wait(500);
        if(!isConnected()) return false;
      } catch (InterruptedException e) {
        return false;
      }
      if(System.currentTimeMillis() - start > maxWait) {
        checkSentMap(maxWait);
        return false;
      }
    }
    return true;
  }

  public boolean isPending(CamdNetMessage request) {
    return sentMap.values().contains(request) || sendQueue.contains(request);
  }

  private void dumpQueue() {
    synchronized(System.out) {
      System.out.println("\n\n---- sendQueue (awaiting send) ----");
      try {
        for(Iterator iter = sendQueue.iterator(); iter.hasNext();) System.out.println(iter.next());
      } catch (Exception e) {}
      System.out.println("\n---- sentMap (already sent, awaiting reply ----");
      try {
        for(Iterator iter = sentMap.keySet().iterator(); iter.hasNext();) System.out.println(sentMap.get(iter.next()));
      } catch (Exception e) {}
      System.out.println();
    }
  }

  public boolean sendEcmRequest(CamdNetMessage request, ProxySession listener) {
    if(!isConnected()) return false;

    QueueEntry qe = new QueueEntry(request, listener, Thread.currentThread());
    lastEcmTimeStamp = qe.getTimeStamp();
    if(sentSet.contains(request) || sendQueue.contains(qe)) throw new IllegalStateException(getLabel());
    request.setConnectorName(getName());

    if(getQueueSize() > maxQueue) {
      logger.severe("Max queue size exceeded, closing...");
      dumpQueue();
      close();
      return false;
    }

    sendQueue.add(qe);

    int seqNr;
    synchronized(this) {
      if(!isConnected()) return false;
      CamdNetMessage req = new CamdNetMessage(request);
      if(connManager.getUnknownSid(req.getProfileName()) != -1) {
        if(connManager.isServiceUnknown(req.getProfileName(), req.getServiceId()))
          req.setServiceId(connManager.getUnknownSid(req.getProfileName())); // change outgoing sid for unknowns
      }
      seqNr = sendMessage(req); // send copy
      if(seqNr != -1) {
        if(sentMap.put(new Integer(seqNr), qe) != null) logger.severe("Overwrote existing pending seqNr: " + seqNr);
        sentSet.add(request);
        lastSent = qe;
      }

      // precaution: newcs seems to sometimes return duplicate messages when a client sends mulitiple requests
      // immediately after another
      if(asynchronous && minDelay > 0) try {
        Thread.sleep(minDelay);
      } catch (InterruptedException e) {
        return false;
      }
    }

    sendQueue.remove(qe);

    if(seqNr != -1) {
      long queueTime = qe.setSent();
      if(listener != null && (queueTime > connManager.getMaxCwWait(profile) / 2))
        listener.setFlag(request, 'G'); // waiting took longer than max-cw-wait/2 = congestion
      return true;
    }
    return false;
  }

  protected void checkSentMap(long maxAge) {
    QueueEntry qe; Integer key; boolean timeout = false; int timeoutRequests = 0; String timeoutService = null;
    for(Iterator iter = new ArrayList(sentMap.keySet()).iterator(); iter.hasNext(); ) {
      key = (Integer)iter.next();
      if(key == null) continue;
      qe = (QueueEntry)sentMap.get(key);
      if(qe == null) continue;
      if(qe.getDuration() > maxAge) {
        timeout = maxAge != -1;
        failRequest(qe, timeout);
        sentMap.remove(key);
        sentSet.remove(qe.getRequest());
        if(timeout) {
          timeoutRequests++;
          timeoutService = qe.getRequest().getProfileName() + ":" + Integer.toHexString(qe.getRequest().getServiceId());
        }
      }
      peekCache(qe); // check all pending messages against cache in case something has become available after forward
    }
    if(maxAge == -1) {
      sentMap.clear();
      sentSet.clear();
    } else {
      if(timeout) {
        if(System.currentTimeMillis() - lastReadTimeStamp > connManager.getMaxCwWait(profile)) timeoutCount++;
        connManager.cwsEcmTimeout(this, "ECM timeout for [" +
            (timeoutRequests == 1 && timeoutService != null?timeoutService:timeoutRequests + " queued requests") +"]: " + timeoutCount,
            timeoutCount);
      }

      if(timeoutCount >= connManager.getTimeoutThreshold()) {
        logger.warning("Too many failures, closing connection...");
        close();
      }
    }
  }

  protected void peekCache(QueueEntry qe) {
    if(qe.reply == null) {
      CamdNetMessage reply = cacheHandler.peekReply(qe.getRequest());
      if(reply != null) {
        qe.reply = reply;
        qe.setFlag(reply.getOriginAddress()==null?'C':'R'); // cache match found while waiting for forward reply, creates FC or FR transactions
        replyQueue.add(qe);
      }
    }
  }

  protected void failRequest(QueueEntry qe, boolean timeout) {
    totalFailures++;
    if(!timeout) {
      if(qe.getDuration() < connManager.getMaxCwWait(profile) / 2) {
        logger.info("CWS send aborted, but there's still time left for: " + qe);        
        // todo, retry here?
      }
      qe.timeOut(this, 'A');
    } else {
      if(!qe.isProbe()) logger.warning("Timeout waiting for ecm reply, discarding request and returning empty (" +
          (timeoutCount + 1) + " failures) - " + qe);
      qe.timeOut(this, 'T');
    }
    // notify cache, there wont be any reply from this connector
    if(cacheHandler != null) cacheHandler.processReply(qe.getRequest(), null);
  }

  public void sendKeepAlive() {}

  public boolean equals(Object o) {
    if(this == o) return true;
    if(o == null || getClass() != o.getClass()) return false;
    final CwsConnector that = (CwsConnector)o;
    return enabled == that.isEnabled() && name.equals(that.getName());
  }

  public int hashCode() {
    int result;
    result = name.hashCode();
    result = 29 * result + (enabled?1:0);
    return result;
  }

  public int compareTo(Object o) { // ensure connectors are sorted by estimated queuetime first then longest idle time
    CwsConnector c = (CwsConnector)o;
    int a = getQueueSize();
    int b = c.getQueueSize();
    
    if(a == 0 && b == 0) return new Long(c.getLastEcmTimeStamp()).compareTo(new Long(lastEcmTimeStamp));
    else {
      a = getCurrentEcmTime() * a;
      b = c.getCurrentEcmTime() * b;

      if(a < 0 || b < 0) return new Long(c.getLastEcmTimeStamp()).compareTo(new Long(lastEcmTimeStamp));
      else return new Integer(b).compareTo(new Integer(a));
    }
  }

  protected long getMaxWait() {
    return connManager.getMaxCwWait(profile) - Math.max(500, getAverageEcmTime());
  }

  public long getLastEcmTimeStamp() {
    return lastEcmTimeStamp;
  }

  public long getLastAttemptTimeStamp() {
    return lastAttemptTimeStamp;
  }

  public long getLastDisconnectTimeStamp() {
    return lastDisconnectTimeStamp;
  }

  public void close() {
    connectTimeStamp = -1;
    timeoutCount = 0;

    if(readerThread != null) {
      readerThread.interrupt();
      readerThread = null;
    }
    if(timeoutThread != null) {
      timeoutThread.interrupt();
      timeoutThread = null;
    }
    alive = false;
    providerIdents = null;
    reset();
  }

  public int getQueueSize() {
    int qSize = sentMap.size();
    qSize += sendQueue.size();
    return qSize;
  }

  public String toString() {
    return getLabel() + (enabled?"":" (disabled)");
  }

  public boolean isConnected() {
    return readerThread != null;
  }

  public Properties getRemoteInfo() {
    return null;
  }

  public Set getProviderIdents() {
    if(!predefinedProviders.isEmpty()) return predefinedProviders;
    else if(providerIdents == null) {
      // be backwards compatible with older external connectors
      CardData card = getRemoteCard();
      if(card == null) return Collections.EMPTY_SET;
      else return new HashSet(Arrays.asList(card.getProvidersAsInt()));
    } else return providerIdents;
  }
}
