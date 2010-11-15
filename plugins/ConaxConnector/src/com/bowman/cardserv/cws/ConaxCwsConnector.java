package com.bowman.cardserv.cws;

import com.bowman.cardserv.*;
import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.interfaces.UserManager;
import com.bowman.cardserv.util.ProxyXmlConfig;

import java.io.*;
import java.util.*;

import javax.smartcardio.*;
import java.util.concurrent.Semaphore;

public class ConaxCwsConnector extends AbstractCwsConnector {

  private static final String CONNECTOR_PROTOCOL = "Conax";

  // Output parameters
  
  private static final int CA_SYS_ID           = 0x28;
  private static final int CA_DESC_EMM         = 0x22;
  private static final int CW                  = 0x25;
  private static final int ACCESS_STATUS       = 0x31;
  private static final int SUBSCRIPTION_STATUS = 0x32;
  private static final int CARD_NUMBER         = 0x74;

  // Input parameters
  private static final int HOST_VER            = 0x10;
  private static final int CAT                 = 0x11;
  private static final int EMM                 = 0x12;
  private static final int ECM                 = 0x14;

  // Basic data types
  private static final int ASCII_TEXT          = 0x01;
  private static final int OCTET_STR           = 0x20;
  private static final int ADDRESS             = 0x23;
  private static final int TIME                = 0x30;

  private static byte[] INIT_OAA = new byte[]{CAT, (byte)0x12,(byte)0x01, (byte)0xD0, (byte)0x0F, (byte)0xFF, (byte)0xFF, (byte)0xDD, (byte)0x00, (byte)0x00, (byte)0x09, (byte)0x04, (byte)0x0B, (byte)0x00, (byte)0xE0, (byte)0x30, (byte)0xF4, (byte)0xDD, (byte)0x44, (byte)0x3F};
  private static final CommandAPDU INIT_CASS = new CommandAPDU(0xDD, 0x26, 0x00, 0x00, new byte[]{HOST_VER, 0x01, 0x40});
  private static final CommandAPDU CA_STATUS_SELECT = new CommandAPDU(0xDD, 0xC6, 0x00, 0x00, new byte[] { 0x1C, 0x01, 0x00 });
  private static final CommandAPDU REQ_CARD_NUMBER = new CommandAPDU(0xDD, 0xC2, 0x00, 0x00, new byte[] { 0x66, 0x00 });

  private static final byte[] HISTORICAL_BYTES = new byte[] {'0', 'B', '0', '0'};
  private int sequenceNr;

  byte[] DW = null;

  private Vector dcwReplies = new Vector();
  private Semaphore replyAvailable = new Semaphore(10, true);

  CardChannel channel;

  private int emmCount;

  private int nodeNumber;
  private byte[] nodeSerial;
  private String nodeName;

  private long lastTrafficTimeStamp = System.currentTimeMillis();

  private ConaxCardData internalCardData = new ConaxCardData(); 

  private class ConaxCardData {
    String connectorName = null;
    int caid;
    byte UA[] = new byte[8];
    byte SA[] = new byte[8];
    byte cardSerial[] = new byte[4];

    Set subscriptions = new HashSet();
    
    CardData getCardData() {
      byte rawCardData[] = new byte[1 + 2 + 8 + 1 + 3 + 8];

      rawCardData[0] = 1;
      rawCardData[1] = (byte)((caid>>8)&0xFF);
      rawCardData[2] = (byte)((caid)&0xFF);
      System.arraycopy(UA, 0, rawCardData, 3, 8);

      // One provider, id 00 00 00
      rawCardData[11] = 0x01;
      rawCardData[12] = 0x00;
      rawCardData[13] = 0x00;
      rawCardData[14] = 0x00;
      
      System.arraycopy(SA, 0, rawCardData, 15, 8);
      
      return new CardData(rawCardData, connectorName);
    }
  }
  
  private class SubStatus {
    String id, name, octet1, octet2, start1, start2, end1, end2;
    public String toString() {
      return "Provider: " + id + "," + name + "\r\n" + octet1;
    }
  }
  
  private class ConaxResponse {
    private int sw;
    private byte[] data;

    ConaxResponse(int sw) {
      this.sw = sw;
    }

    ConaxResponse(int sw, byte[] data) {
      this.sw = sw;
      this.data = data;
    }

    ConaxResponse(byte[] data) {
      this.sw = 0x9000;
      this.data = data;
    }

    ConaxResponse(byte[] data, int pos, int len) {
      this.sw = 0x9000;
      this.data = new byte[len];
      System.arraycopy(data, pos, this.data, 0, len);
    }

    int getSW() {
      return sw;
    }

    byte[] getData() {
      return data;
    }
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    super.configUpdated(xml);

    xml.getStringValue("profile"); // mandatory
    boolean enabled = "true".equalsIgnoreCase(xml.getStringValue("enabled", "true"));

    try {
      auUsers.clear();
      UserManager um = config.getUserManager();
      String auUsersStr = xml.getStringValue("au-users");
      StringTokenizer st = new StringTokenizer(auUsersStr);
      String auUser;
      while(st.hasMoreTokens()) {
        auUser = st.nextToken();
        if(!um.exists(auUser)) logger.warning("AU-user '" + auUser + "' for " + getLabel() + " doesn't exist, skipping...");
        else auUsers.add(auUser.trim());
      }
    } catch (ConfigException e) {
      auUsers.clear();
    }

    String nodeName = xml.getStringValue("node");

    try {
      nodeSerial = xml.getBytesValue("node");
      if(nodeSerial.length < 4) {
        nodeSerial = null;
      }
    } catch (ConfigException e) {
      nodeSerial = null;
    }

    try {
      nodeNumber = xml.getIntValue("node");
    } catch (ConfigException e) {
      nodeNumber = -1;
    }

    boolean changed = !(nodeName.equals(this.nodeName) && enabled == this.enabled);

    this.nodeName = nodeName;
    this.enabled = enabled;

    if(!enabled) close();

    //    if(channel != null && enabled && !changed) {
    //      try {
    //        // read subscription info from card, shouldn't be done here in the future
    //        parseConaxResponse(sendConaxCommand(CA_STATUS_SELECT), -1);
    //      } catch (CardException e) {
    //      } catch (IllegalStateException e) {
    //      } catch (IOException e) {
    //      }
    //    }

    if(changed && enabled) {
      close();
      if(connManager != null) {
        synchronized(connManager) {
          connManager.notifyAll();
        }
      }
    }

    logger.fine("Configuration updated. Enabled: " + enabled + " Changed: " + changed + " AU-users: " + auUsers);
  }

  public void run() {

    alive = true;

    try {
      initCard();

      connectTimeStamp = System.currentTimeMillis();
      connManager.cwsConnected(this);
      connecting = false;

      CamdNetMessage msg;

      while(alive) {
        msg = readReply();

        if(msg == null) {
          alive = false;
          logger.warning("Connection closed");
        } else {
          if(msg.isEcm()) { // ecm reply, report to listener
            synchronized(this) { // synchronize here to avoid race-condition with AbstractCwsConnector.sendEcmRequest()
              if(!reportReply(msg)) logger.info("No listener found for ECM reply: " + msg);
            }
          } else if(msg.isEmm()) { // emm reply, do nothing
            logger.fine("EMM reply ignored: " + msg);
          }
        }
      }
    } catch (IOException e) {
      logger.throwing("Exception reading/parsing message: " + e, e);
    } catch (Exception e) {
      e.printStackTrace();
    }

    readerThread = null;
    internalClose();

    reset();

    connManager.cwsDisconnected(this);
    synchronized(connManager) {
      connManager.notify();
    }

    connecting = false;
    if(noProfile) profile = null; // reset this to trigger auto-detect in case card has changed when reconnecting later
    logger.info("Connector dying");
  }

  CamdNetMessage readReply() throws IOException {
    CamdNetMessage msg = null;
    try {
      replyAvailable.acquire();
      logger.finest("Replies in buffer : " + dcwReplies.size());
      if(!dcwReplies.isEmpty()) msg = (CamdNetMessage) dcwReplies.remove(0);
    } catch(InterruptedException e) {
    }

    return msg;
  }

  public boolean isConnecting() {
    return connecting;
  }

  public boolean isReady() {
    return isConnected();
  }

  public CardData getRemoteCard() {
    internalCardData.connectorName = getName();
    return internalCardData.getCardData();
  }

  public boolean isAuAllowed(String userName) {
    return auUsers.contains(userName);
  }

  public String[] getAuUsers() {
    return (String[])auUsers.toArray(new String[auUsers.size()]);
  }

  public long getLastTrafficTimeStamp() {
    return lastTrafficTimeStamp;
  }

  public synchronized int sendMessage(CamdNetMessage msg) {
    if(msg.isKeepAlive()) return -1; // not supported
    if(msg.isOsdMsg()) return -1; // not supported
    if(!waitForPending()) return -1;
    if(msg.isEmm()) emmCount++;

    sequenceNr &= 0xFFFF; 
    msg.setSequenceNr(sequenceNr);

    int dataLength = msg.getDataLength();
    CommandAPDU apdu;

    if(msg.isEmm()) {
      logger.fine("Sequence number : " + sequenceNr);

      byte[] cardCommand = new byte[dataLength + 5];
      cardCommand[0] = EMM;
      cardCommand[1] = (byte) ((dataLength + 3)&0xFF);
      cardCommand[2] = (byte) (msg.getCommandTag()&0xFF);
      cardCommand[3] = 0x70;
      cardCommand[4] = (byte) ((dataLength)&0xFF);
      System.arraycopy(msg.getCustomData(), 0, cardCommand, 5, dataLength);
      apdu = new CommandAPDU(0xDD, 0x84, 0x00, 0x00, cardCommand);
    } else {
      int sid = msg.getServiceId();
      logger.fine("Sequence number : " + msg.getSequenceNr() + ", sid : " + Integer.toHexString(sid));

      byte[] cardCommand = new byte[dataLength + 6];
      cardCommand[0] = ECM;
      cardCommand[1] = (byte) ((dataLength + 4)&0xFF);
      cardCommand[2] = 0x00; //mode_in, 0x00 = Operational mode
      cardCommand[3] = (byte) (msg.getCommandTag()&0xFF); // table id
      cardCommand[4] = 0x70;
      cardCommand[5] = (byte) ((dataLength)&0xFF);
      System.arraycopy(msg.getCustomData(), 0, cardCommand, 6, dataLength);
      apdu = new CommandAPDU(0xDD, 0xA2, 0x00, 0x00, cardCommand);
    }

    try {
      ConaxResponse response = null;
      try {
        response = sendCommand(apdu);
      } catch (CardException e) {
        throw new IOException("Card communication failure");
      } catch (IllegalStateException e) {
        throw new IOException();
      }
      lastTrafficTimeStamp = System.currentTimeMillis();

      DW = null;

      if(response != null && response.getSW() == 0x9000 && response.getData() != null) {
        parseResponse(response, -1);
      }
      CamdNetMessage reply = null;

      if(DW != null) {
        reply = new CamdNetMessage(msg.getCommandTag());
        reply.setCustomData(DW);
        reply.setFixedData(msg.getFixedData());
      } else if(msg.isEmm()){
        reply = msg.getEmmReply();
      } else {
        reply = msg.getEmptyReply();
      }

      reply.setSequenceNr(sequenceNr);
      dcwReplies.add(reply);
    } catch (IOException e) {
    } finally {
      replyAvailable.release();
    }

    return sequenceNr++;
  }

  private CardTerminal probeCard(List terminals) {
    logger.info("Searching terminals : " + terminals + " for card: " + DESUtil.bytesToString(nodeSerial));

    CardTerminal tempTerminal = null;

    for(Iterator iter = terminals.iterator(); iter.hasNext();) {
      tempTerminal = (CardTerminal) iter.next();
      Card tempCard = null;

      try {
        String name = tempTerminal.getName();
        logger.info("Probing : " + name);

        if(tempTerminal.waitForCardPresent(10)) {
          tempCard = tempTerminal.connect("*");
          logger.fine("ATR Historical bytes:" + DESUtil.bytesToString(tempCard.getATR().getHistoricalBytes()));

          channel = tempCard.getBasicChannel();

          byte[] serial = getCardSerial();
          logger.fine("Card serial : " + DESUtil.bytesToString(serial));

          if(Arrays.equals(nodeSerial, serial)) {
            return tempTerminal;
          }
        }
      } catch (CardException e) {
        logger.finest("CardExeption");
      } catch (IllegalStateException e) {
        logger.finest("IllegalStateException");
      } catch (IOException e) {
        logger.finest("IOException");
      } finally {
        if(tempCard != null) {
          try {
            tempCard.disconnect(true);
          } catch (CardException e) {
          }
        }
        channel = null;
      }
    }
    return null;
  }

  protected synchronized void connectNative() throws IOException {
    replyAvailable.drainPermits();
    // show the list of available terminals
    TerminalFactory factory = TerminalFactory.getDefault();
    try {
      CardTerminal terminal = null;
      List terminals = null;
//      factory.terminals().list();

      if(nodeSerial != null) {
        terminals = factory.terminals().list(CardTerminals.State.CARD_PRESENT);
        terminal = probeCard(terminals);
      } else if(nodeNumber != -1) {
        terminals = factory.terminals().list();
        if(nodeNumber > (terminals.size() - 1)) throw new IOException("Node not found");
        // get the selected terminal
        terminal = (CardTerminal) terminals.get(nodeNumber);
      } else if(nodeName != null) {
        terminals = factory.terminals().list();
        terminal = factory.terminals().getTerminal(nodeName);
      }

      if(terminal == null) {
        throw new IOException("Node not found");
      }

      host = terminal.getName();

      // establish a connection with the card
      channel = terminal.connect("*").getBasicChannel();

      logger.info("PCSC node: " + host);
      logger.info("Card : " + channel.getCard());
      logger.info("ATR :" + DESUtil.bytesToString(channel.getCard().getATR().getBytes()));
    } catch (CardException e) {
      channel = null;
      throw new IOException("Card communication failure");
    } catch (IllegalStateException e) {
      channel = null;
      throw new IOException();
    }
    emmCount = 0;
    logger.info("Connected");
  }

  public int getEmmCount() {
    return emmCount;
  }

  public String getProtocol() {
    return CONNECTOR_PROTOCOL;
  }

  public Properties getRemoteInfo() {
    Properties p = new Properties();

    p.setProperty("CAID: ", Integer.toHexString(internalCardData.caid));
    p.setProperty("Unique Address: ", DESUtil.bytesToString(internalCardData.UA));
    p.setProperty("Shared Address: ", DESUtil.bytesToString(internalCardData.SA));

    int i = 1;
    if(!internalCardData.subscriptions.isEmpty()) {
      Iterator iter = internalCardData.subscriptions.iterator();
      if(iter != null) while(iter.hasNext()) {
        p.setProperty("subscription " + i++, String.valueOf(iter.next()));
      }
    }
    return p;
  }
  
  public String getRemoteAddress() {
    return channel.getCard().toString();
  }

  private byte[] getCardSerial() throws IOException, CardException, IllegalStateException {
    parseResponse(sendCommand(REQ_CARD_NUMBER), -1);
    return internalCardData.cardSerial;
  }

  private void internalClose() {
    if(channel != null) {
      try {
        channel.getCard().disconnect(true);
      } catch (CardException e) {
      } catch (IllegalStateException e) {
      } finally {
        channel = null;
      }
    }
  }

  public void close() {
    dcwReplies.clear();
    internalClose();
    super.close();
  }

  private ConaxResponse getResponse(int sw) throws CardException, IllegalStateException {
    byte[] rawData = new byte[0];

    while((sw & 0x9800) == 0x9800) {
      ResponseAPDU r = channel.transmit(new CommandAPDU(0xDD, 0xCA, 0x00, 0x00, (sw&0xFF)));
      sw = r.getSW();
      logger.finest("SW : " + Integer.toHexString(sw) + " Data : " + DESUtil.bytesToString(r.getData()));
      byte[] newData = new byte[rawData.length + r.getNr()];
      System.arraycopy(rawData, 0, newData, 0, rawData.length);
      System.arraycopy(r.getData(), 0, newData, rawData.length, r.getNr());
      rawData = newData;
    }

    ConaxResponse response = new ConaxResponse(sw, rawData);
    return response;
  }

  private ConaxResponse sendCommand(CommandAPDU apdu) throws CardException, IllegalStateException {
    try {
      channel.getCard().beginExclusive();
      logger.finest("Sending to card : " + DESUtil.bytesToString(apdu.getBytes()));

      ResponseAPDU r = channel.transmit(apdu);
      logger.finest("SW: " + Integer.toHexString(r.getSW()));
      if((r.getSW1() & 0x98) == 0x98) {
        return getResponse(r.getSW());
      }
      return new ConaxResponse(r.getSW());
    } finally {
      channel.getCard().endExclusive();
    }
  }

  void initCard() throws IOException {
    try {
      parseResponse(sendCommand(INIT_CASS), -1);
//      parseConaxResponse(sendConaxCommand(INIT_OAA), -1, null);
      
      parseResponse(sendCommand(new CommandAPDU(0xDD, 0x82, 0x00, 0x00, INIT_OAA)), -1);
      parseResponse(sendCommand(CA_STATUS_SELECT), -1);
    } catch (CardException e) {
      throw new IOException("Card communication failure");
    } catch (IllegalStateException e) {
      throw new IOException();
    }
  }

  void parseResponse(ConaxResponse response, int parentNano) throws IOException {
    if(response.getData() == null) return;    
    
    ByteArrayInputStream input = new ByteArrayInputStream(response.getData());

    while(input.available() > 0) {
      int cmd = input.read();
      int len = input.read();
      byte[] nanoData = new byte[len];
      input.read(nanoData);

      switch (cmd) {

      case ASCII_TEXT:
        if(parentNano != -1) {
          logger.info("ASCII_TEXT : " + new String(nanoData));
        }
        break;

      case OCTET_STR:
        if(parentNano != -1) {
          logger.fine("OCTET_STR : " + DESUtil.bytesToString(nanoData));
        }
        break;

      case TIME:
        if(parentNano != -1) {
          int year_offset = (nanoData[0]>>5)&0x07;
          int day = (nanoData[0])&0x1F;
          int year = (nanoData[1]>>4)&0x0F;
          int month = (nanoData[1])&0x0F;
          year += 1990+year_offset*10;
          logger.fine("TIME : " + year + "/" + month + "/" + day);
        }
        break;
        
      case CA_SYS_ID:
        INIT_OAA[12] = nanoData[0];
        INIT_OAA[13] = nanoData[1];
        internalCardData.caid = ((nanoData[0] & 0xFF) << 8) | (nanoData[1] & 0xFF);
        logger.fine("CA_SYS_ID: " + Integer.toHexString(internalCardData.caid));
        break;

      case ADDRESS: // card serial
        if(parentNano != -1) {
          logger.info("ADDRESS : " + DESUtil.bytesToString(nanoData));

          if(nanoData[3] != 0x00) {
            System.arraycopy(nanoData, 0, internalCardData.UA, 1, 7);
          } else {
            System.arraycopy(nanoData, 0, internalCardData.SA, 1, 7);
          }
        }
        break;

      case CW:
        if (DW == null) DW = new byte[16];

        if(nanoData[2] == 0x01) {
          System.arraycopy(nanoData, 5, DW, 8, 8);
        } else {
          System.arraycopy(nanoData, 5, DW, 0, 8);
        }
        break;

      case CA_DESC_EMM:
        parseResponse(new ConaxResponse(nanoData), cmd);
        break;

      case SUBSCRIPTION_STATUS:
        parseSubscription(nanoData);
        break;

      case CARD_NUMBER: 
        logger.info("CARD_NUMBER : " + DESUtil.bytesToString(nanoData));
        System.arraycopy(nanoData, 0, internalCardData.cardSerial, 0, 4);
        break;
      }
    }
  }

  /*
   * Provider: 6080, HD , TIME : 2010/4/1, TIME : 2010/4/30, OCTET_STR01 04 00 00, TIME : 2010/5/1, TIME : 2010/5/31, OCTET_STR01 04 00 00
   */
  void parseSubscription(byte[] subscriptionData) throws IOException {
    ByteArrayInputStream input = new ByteArrayInputStream(subscriptionData);
    String subscriptionString = "Provider : " + Integer.toHexString(input.read()) + Integer.toHexString(input.read());
    logger.fine(subscriptionString);

    while(input.available() > 0) {
      int cmd = input.read();
      int len = input.read();
      byte[] nanoData = new byte[len];
      input.read(nanoData);

      switch (cmd) {

      case ASCII_TEXT:
        logger.info("Provider name: " + new String(nanoData));
        subscriptionString += ", " + new String(nanoData);
        break;

      case OCTET_STR:
        logger.info("OCTET_STR : " + DESUtil.bytesToString(nanoData));
        subscriptionString += ", OCTET_STR : " + DESUtil.bytesToString(nanoData);
        break;

      case TIME:
        int year_offset = (nanoData[0]>>5)&0x07;
        int day = (nanoData[0])&0x1F;
        int year = (nanoData[1]>>4)&0x0F;
        int month = (nanoData[1])&0x0F;
        year += 1990+year_offset*10;
        logger.info("TIME : " + year + "/" + month + "/" + day);
        subscriptionString += ", TIME : " + year + "/" + month + "/" + day;
        break;
      }
    }
    internalCardData.subscriptions.add(subscriptionString);
  }
}
