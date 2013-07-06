package com.bowman.cardserv.session;

import com.bowman.cardserv.*;
import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.interfaces.CamdMessageListener;

import java.net.Socket;
import java.io.*;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2007-mar-04
 * Time: 15:50:35
 */
public class RadegastSession extends AbstractSession {

  private Socket conn;
  private DataInputStream is;
  private BufferedOutputStream os;

  private int caId;

  public RadegastSession(Socket conn, ListenPort listenPort, CamdMessageListener listener) {
    super(listenPort, listener);
    this.conn = conn;

    sessionThread = new Thread(this, "RadegastSessionThread-" + sessionId);
    sessionThread.start();
  }

  public void run() {
    logger.fine("Starting...");

    alive = true;
    remoteAddress = conn.getInetAddress().getHostAddress();
    int originId = ProxyConfig.getInstance().getProxyOriginId();
    boolean first = true, checksOk;
    CamdNetMessage msg;
    try {

      is = new DataInputStream(conn.getInputStream());
      os = new BufferedOutputStream(conn.getOutputStream());

      Thread.currentThread().setName(Thread.currentThread().getName() + "[" + getUser() + "]");

      while(alive) {
        msg = readMessage();
        if(msg == null) {
          alive = false;
          logger.info("Connection closed");
        } else {
          msg.setOriginId(originId);
          if(first) {
            if(!"0.0.0.0".equals(getRemoteAddress())) {
              SessionManager.getInstance().addSession(this); // dont add session until something valid has been received
            } else {
              close();
              return;
            }
            first = false;
          }
          checksOk = checkLimits(msg);
          checksOk = checksOk && handleMessage(msg);
          fireCamdMessage(msg, false);
          if(!checksOk) {
            setFlag(msg, 'B');
            if(isConnected()) sendEcmReply(msg, msg.getEmptyReply()); // nothing elsewhere will acknowledge a filtered message so do it here
          }          
        }
      }

    } catch (Exception e) {
      logger.throwing("Exception reading/parsing message: " + e, e);
      alive = false;
    }

    endSession();
  }

  private boolean handleMessage(CamdNetMessage msg) {
    CaProfile profile = getProfile();
    if(profile.getCaId() != msg.getCaId()) {
      msg.setFilteredBy("Wrong ca-id in request: " + DESUtil.intToHexString(msg.getCaId(), 4) + " (expected: " +
          DESUtil.intToHexString(profile.getCaId(), 4) + ")");
      return false;
    }
    if(!profile.getProviderSet().contains(new Integer(msg.getProviderIdent()))) {
      msg.setFilteredBy("Unknown provider-ident in request: " + DESUtil.intToByteString(msg.getProviderIdent(), 3));
      return false;
    }
    return true;
  }

  CamdNetMessage readMessage() throws IOException {

    int i = is.read();
    if(i == -1) return null;
    if(i != 1) throw new IOException("Unexpected header byte from rdg client: " + i);
    i = is.read(); // len
    byte[] buf = new byte[i];
    is.readFully(buf);

    logger.finer("Received message: " + DESUtil.bytesToString(buf));

    int commandTag = -1, sid = 0, caId = 0;
    byte[] providerId = new byte[8], keyNr = new byte[4], caData = null;

    for(int n = 0; n < buf.length; n++) {

      switch(buf[n]) {
        case 0x02: // Ignore for now: CAID_INDEX, LOOP, TRACK, CAID, SOURCE_TXT ?
        case 0x08:
        case 0x09:
        case 0x10:
          n++;
          n += buf[n];
          break;
        case 0x0a:
          n++;
          if(buf[n] != 2) throw new IOException("Unexpected caid length: " + buf[2]);
          caId = (buf[n + 1] & 0xFF) * 256 + (buf[n + 2] & 0xFF);
          n += buf[n];
          break;
        case 0x06: // PROVIDER
          n++;
          if(buf[n] != 8) throw new IOException("Unexpected providerid length: " + buf[n]);
          System.arraycopy(buf, n + 1, providerId, 0, 8);
          n += buf[n];
          break;
        case 0x07: // KEYNO
          n++;
          if(buf[n] != 4) throw new IOException("Unexpected keynr length: " + buf[n]);
          System.arraycopy(buf, n + 1, keyNr, 0, 4);
          n += buf[n];
          break;
        case 0x03: // PACKET
          n++;
          commandTag = buf[n + 1] & 0xFF;
          int len = buf[n] & 0xFF;
          caData = new byte[len - 3]; // seems to be an extra 2 bytes wrapper after 80/81, 70 + length? skipping
          System.arraycopy(buf, n + 4, caData, 0, caData.length);
          n += (caData.length + 3);
          break;
        case 0x21: // EXTRA (sid)
          n++;
          if(!"false".equalsIgnoreCase(listenPort.getStringProperty("sid-in-0x21"))) {
            if(buf[n] != 2) logger.fine("Radegast field 0x21 is " + buf[n] + " bytes, expected 2 for sid (ignoring).");
            else {
              sid |= (buf[n + 1] & 0xFF) << 8;
              sid |= (buf[n + 2] & 0xFF);
            }
          }
          n += buf[n];
          break;

        default:
          logger.fine("Unknown radegast field @ offset " + n + ": " + DESUtil.byteToString(buf[n]) + " len: " +
              buf[n + 1] + " data: " + DESUtil.bytesToString(buf, n + 2, buf[n + 1]));
          n++;
          n += buf[n];
          break;
      }

    }

    if(commandTag == -1) throw new IOException("Bad radegast request: " + DESUtil.bytesToString(buf));

    // recreate full raw request for logging
    byte[] full = new byte[buf.length + 2];
    full[0] = 1; full[1] = (byte)buf.length;
    System.arraycopy(buf, 0, full, 2, buf.length);

    CamdNetMessage msg = CamdNetMessage.parseRadegast(commandTag, caData, full, getRemoteAddress());
    msg.setRdgProviderId(providerId);
    msg.setRdgKeyNumber(keyNr);
    msg.setCaId(caId);
    msg.setNetworkId(getProfile().getNetworkId());    

    msgCount++;
    if(msg.isEcm()) { // always true
      ecmCount++;
      if(sid != 0) msg.setServiceId(sid);
      this.caId = msg.getCaId();
    }

    return msg;
  }

  public String getUser() {
    return "rdg@" + getRemoteAddress();
  }

  public String getClientId() {
    return "Unknown[rdg]";
  }

  public String getProtocol() {
    return "Radegast";
  }

  public String getLastContext() {
    if(caId == 0) return "?";
    else {
      String s = Integer.toHexString(caId);
      while(s.length() < 4) s = "0" + s;
      return "CaID [" + s + "] Providers [" + getProfile().getProviderSet().size() + "] " +
          ProxyConfig.providerIdentsToString(getProfile().getProviderSet());
    }
  }

  public void close() {
    try {
      if(conn != null) conn.close();
    } catch(IOException e) {}
    if(sessionThread != null) sessionThread.interrupt();
  }

  public boolean isConnected() {
    return sessionThread != null;
  }

  public boolean isTempUser() {
    return true;
  }

  public int sendMessage(CamdNetMessage msg) {
    return -1;
    // throw new UnsupportedOperationException("Not implemented.");
  }

  public synchronized int sendEcmReplyNative(CamdNetMessage ecmRequest, CamdNetMessage ecmReply) {

    int status = -1;
    byte[] buf = new byte[4 + ecmReply.getDataLength()];

    buf[0] = 2; // CMD_ECM_KEY_RESPONSE
    buf[1] = (byte)(buf.length - 2); // len
    if(ecmReply.isEmpty()) {
      buf[2] = 4;
      buf[3] = 0;
    } else {
      buf[2] = 5;
      buf[3] = (byte)ecmReply.getDataLength();
      System.arraycopy(ecmReply.getCustomData(), 0, buf, 4, ecmReply.getDataLength());
    }

    logger.finer("Sending reply: " + DESUtil.bytesToString(buf));

    try {
      os.write(buf);
      os.flush();
      ecmReply.setSent(getRemoteAddress(), buf, getProtocol());
      status = 1;
    } catch(IOException e) {
      logger.throwing("Connection closed while sending", e);
      close();
    }

    endTransaction(ecmRequest, ecmReply, status);
    return status;
  }

}
