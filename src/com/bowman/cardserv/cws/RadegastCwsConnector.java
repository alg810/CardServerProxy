package com.bowman.cardserv.cws;

import com.bowman.cardserv.*;
import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.util.ProxyXmlConfig;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2007-apr-28
 * Time: 17:13:15
 * @noinspection SynchronizeOnNonFinalField
 */
public class RadegastCwsConnector extends AbstractCwsConnector {

  private int sequenceNr = 1;

  private Socket conn;
  private DataInputStream is;
  private BufferedOutputStream os;

  private CardData fakeCard;

  private long lastTrafficTimeStamp = System.currentTimeMillis();
  private boolean tracing;

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    super.configUpdated(xml);

    xml.getStringValue("profile"); // mandatory for radegast
    String host = xml.getStringValue("host");
    int port = xml.getPortValue("port");
    boolean enabled = "true".equalsIgnoreCase(xml.getStringValue("enabled", "true"));

    boolean changed = !(host.equals(this.host) && port == this.port && enabled == this.enabled);

    this.host = host;
    this.port = port;
    this.enabled = enabled;

    this.tracing = "true".equalsIgnoreCase(xml.getStringValue("tracing", "false"));

    if(!enabled) close();
    else {
      xml.getStringValue("provider-idents"); // mandatory for radegast
      fakeCard = CardData.createData(profile.getCaId(), (Integer[])predefinedProviders.toArray(new Integer[predefinedProviders.size()]));
    }

    if(changed && enabled) {
      close();
      if(connManager != null) {
        synchronized(connManager) {
          connManager.notifyAll();
        }
      }
    }    

    logger.fine("Configuration updated. Enabled: " + enabled + " Changed: " + changed);
  }

  public void run() {

    alive = true;
    CamdNetMessage msg;

    connectTimeStamp = System.currentTimeMillis();
    connManager.cwsConnected(this);
    connecting = false;

    try {
      while(alive && conn != null) {
        msg = readMessage();
        lastSent = null;
        if(msg == null) {
          alive = false;
          logger.warning("Connection closed");
        } else {
          msg.setSequenceNr(sequenceNr);
          msg.setCaId(profile.getCaId());
          if(!reportReply(msg)) logger.fine("No listener found for ECM reply: " + msg);
        }
      }
    } catch(SocketException e) {
      logger.warning("Connection closed: " + e);
    } catch (IOException e) {
      logger.throwing("Exception reading/parsing message: " + e, e);
    } catch (Exception e) {
      e.printStackTrace();
    }

    conn = null;
    readerThread = null;

    reset();

    lastDisconnectTimeStamp = System.currentTimeMillis();
    connManager.cwsDisconnected(this);
    synchronized(connManager) {
      connManager.notifyAll();
    }

    logger.info("Connector dying");
  }

  CamdNetMessage readMessage() throws IOException {
    int i = is.read();
    if(i == -1) return null;
    if(i != 2) throw new IOException("Unexpected header byte from rdg server: " + i);
    i = is.read(); // len
    byte[] buf = new byte[i];
    is.readFully(buf);

    String s = DESUtil.bytesToString(buf);
    logger.finer("Received reply: " + s);
    if(tracing) System.out.println("Recv [" + name + "] " + s);

    if(lastSent == null) System.err.println("lastSent was null when reading: " + s);
    int commandTag = lastSent==null?0x81:lastSent.getRequest().getCommandTag(); // todo

    if(buf[0] == 4) {
      // cannot decode
      return CamdNetMessage.parseRadegast(commandTag, new byte[0], buf, getRemoteAddress());
    } else if(buf[0] == 5)  {
      if(buf[1] != 16) throw new IOException("Unexpected CW length from rdg server: " + buf[1]);
      // successful reply
      byte[] cwData = new byte[16];
      System.arraycopy(buf, 2, cwData, 0, 16);
      return CamdNetMessage.parseRadegast(commandTag, cwData, buf, getRemoteAddress());
    } else throw new IOException("Unknown reply from rdg server: " + s);
  }

  public boolean isConnecting() {
    return connecting;
  }

  public boolean isReady() {
     return isConnected();
  }

  public CardData getRemoteCard() {
    return fakeCard;
  }

  public long getLastTrafficTimeStamp() {
    return lastTrafficTimeStamp;
  }

  public synchronized int sendMessage(CamdNetMessage msg) {
    if(msg.isKeepAlive()) return -1; // not supported
    if(msg.isEmm()) return -1; // not supported
    if(msg.isOsdMsg()) return -1; // not supported
    if(!waitForPending()) return -1;
    msg.setSequenceNr(sequenceNr);

    int caId = getProfile().getCaId(), sid = msg.getServiceId();
    int hdrSize = (sid == 0)?33:37;
    byte[] buf = new byte[hdrSize + msg.getDataLength()];

    buf[0] = 1; // CMD_ECM_KEY_ASK
    buf[1] = (byte)(buf.length - 2); // len
    buf[2] = 2; // CAID_INDEX
    buf[3] = 1; // len
    buf[4] = (byte)((caId >> 8) & 0xFF);
    buf[5] = 6; // PROVIDER
    buf[6] = 8; // len
    String providerStr = DESUtil.intToHexString(msg.getProviderIdent(), 8);
    System.arraycopy(providerStr.getBytes(), 0, buf, 7, 8); // always "00000000" if not known
    buf[15] = 7; // KEYNO;
    buf[16] = 4;  // len
    System.arraycopy(msg.getRdgKeyNumber(), 0, buf, 17, 4); // always "0000" if not known
    buf[21] = 8; // LOOP ?
    buf[22] = 1; // len
    buf[23] = 1;
    buf[24] = 0x0a; // CAID
    buf[25] = 2;  // len
    buf[26] = (byte)((caId >> 8) & 0xFF);
    buf[27] = (byte)(caId & 0xFF);

    int offs = 28;

    if(sid != 0) {
      buf[28] = 0x21; // EXTRA, use for sid
      buf[29] = 2; // len
      buf[30] = (byte)((sid >> 8 ) & 0xFF);
      buf[31] = (byte)(sid & 0xFF);
      offs = 32;
    }

    buf[offs] = 3; // PACKET
    buf[offs + 1] = (byte)(msg.getDataLength() + 3);
    buf[offs + 2] = (byte)msg.getCommandTag();

    // these two bytes are not in newcamd customdata, but at least one of them appears to be the remaining length
    buf[offs + 3] = 0x70; // hopefully this is always 0x70?
    buf[offs + 4] = (byte)msg.getDataLength();

    System.arraycopy(msg.getCustomData(), 0, buf, hdrSize, msg.getDataLength());

    try {
      String s = DESUtil.bytesToString(buf);
      logger.finer("Sending message [" + msg.getSequenceNr() + "]: " + s);
      if(tracing) System.out.println("Sent [" + name + "] " + s);

      os.write(buf);
      os.flush();
      msg.setSent(getRemoteAddress(), buf, getProtocol());
      
      return 1;
    } catch(IOException e) {
      logger.throwing("Connection closed while sending", e);
      close();
    }
    return sequenceNr;
  }

  protected synchronized void connectNative() throws IOException {
    conn = new Socket();
    if(qosClass != -1) conn.setTrafficClass(qosClass);
    conn.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT);
    if(conn == null) throw new IOException("Connection aborted");
    is = new DataInputStream(conn.getInputStream());
    os = new BufferedOutputStream(conn.getOutputStream());
    conn.setSoTimeout(SESSION_SO_TIMEOUT);

    logger.info("Connected");
  }

  public String getProtocol() {
    return "Radegast";
  }

  public Set getProviderIdents() {
    return predefinedProviders;
  }

  public String getRemoteAddress() {
    if(conn == null) return "0.0.0.0";
    else return conn.getInetAddress().getHostAddress();
  }

  public void close() {
    try {
      if(conn != null) conn.close();
    } catch (IOException e) {}
    conn = null;
    super.close();
  }


}
