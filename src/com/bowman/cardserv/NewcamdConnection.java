package com.bowman.cardserv;

import com.bowman.cardserv.crypto.DESUtil;
import com.bowman.cardserv.util.ProxyLogger;
import com.bowman.cardserv.interfaces.CamdConstants;

import java.io.*;
import java.net.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 15, 2005
 * Time: 1:35:22 PM
 */
public class NewcamdConnection {

  private int sequenceNr = 0, maxSize = CamdConstants.CWS_NETMSGSIZE;
  private Socket conn;
  private DataInputStream is;
  private BufferedOutputStream os;
  private byte[] desKey16;
  private long lastTrafficTimeStamp = System.currentTimeMillis();

  private CamdNetMessage lastSent;
  private CamdNetMessage lastRead;

  private boolean noEncrypt = false;
  private boolean logDupes = false;

  private ProxyLogger logger;
  private String traceLabel;

  public NewcamdConnection(Socket conn, boolean noEncrypt, boolean logDupes) {
    this(conn, noEncrypt, logDupes, null);
  }

  private NewcamdConnection(Socket conn, boolean noEncrypt, boolean logDupes, String traceLabel) {
    this.conn = conn;
    this.noEncrypt = noEncrypt;
    this.logDupes = logDupes;
    this.traceLabel = traceLabel;
  }

  public void setDesKey16(byte[] desKey16) {
    this.desKey16 = desKey16;
  }

  public void init(ProxyLogger logger) throws IOException {
    this.logger = logger;
    if(noEncrypt) logger.warning("Newcamd protocol encryption is disabled.");
    is = new DataInputStream(conn.getInputStream());
    os = new BufferedOutputStream(conn.getOutputStream());
  }

  public void clientHandshake(int timeout, byte[] configKey14) throws IOException {
    os.write(generateLoginKey(configKey14));
    os.flush();
    conn.setSoTimeout(timeout);
  }

  public void serverHandshake(byte[] configKey14) throws IOException {
    byte[] random14 = new byte[14];
    is.readFully(random14);
    desKey16 = DESUtil.desKeySpread((DESUtil.xorKey(configKey14, random14))); // loginKey
    logger.finest("Read random 14: " + DESUtil.bytesToString(random14));
    logger.finest("Des key spread: " + DESUtil.bytesToString(desKey16));
  }

  private byte[] generateLoginKey(byte[] configKey14) {
    byte[] random = DESUtil.getRandomBytes(14);
    desKey16 = DESUtil.desKeySpread(DESUtil.xorKey(configKey14, random)); // make the key
    return random; // return the random 14 bytes to be sent back to the client
  }

  public CamdNetMessage readMessage() throws IOException {
    if(is == null) return null;
    int len = is.read();
    if(len == -1) {
      close();
      return null;
    } else {
      len *= 256;
      len += is.read();
      logger.finest("Incoming message length: " + len + " bytes");

      if(len < 0 || len > maxSize) throw new IOException("Bad length: " + len);
      byte[] data = new byte[len];
      is.readFully(data);

      byte[] decrypted;
      if(noEncrypt) decrypted = data;
      else decrypted = DESUtil.desDecrypt(data, len, desKey16);

      if(decrypted == null) throw new IOException("Decryption failed, bad checksum.");
      else {
        CamdNetMessage msg = CamdNetMessage.parseNewcamd(decrypted, getRemoteAddress());
        logger.finer("Received message: " + msg.toString());
        logger.finest("Decrypted message payload (" + decrypted.length + " bytes) -> " + DESUtil.bytesToString(decrypted));
        if(traceLabel != null) System.out.println("Recv [" + traceLabel + "] " + DESUtil.bytesToString(decrypted));
        lastTrafficTimeStamp = System.currentTimeMillis();

        if(logDupes) {
          // check for duplicate ecm replies, newcs bug?
          if(lastRead != null && lastRead.getSequenceNr() != 0 && lastRead.getSequenceNr() == msg.getSequenceNr()) {
            if(lastRead.isEcm() && msg.isEcm()) {
              long time = msg.getTimeStamp() - lastRead.getTimeStamp();
              logger.warning("Duplicate newcamd message received.\n " + time + "\tPrevious: " + lastRead + "\n\tCurrent:  " + msg);
            }
          }
        }

        lastRead = msg;
        return msg;
      }
    }
  }

  public int sendMessage(CamdNetMessage msg) {
    return sendMessage(msg, -1, true);
  }

  public synchronized int sendMessage(CamdNetMessage msg, int seq, boolean flush) {
    if(logger == null || desKey16 == null) return -1; // connection not yet initialized
    if(seq == -1) seq = sequenceNr++;
    msg.setSequenceNr(seq);

    logger.finer("Sending message " + msg.hashCodeStr() + ": " + msg.toString());
    if(sequenceNr > 0xFFFF) sequenceNr = 0;

    byte[] fixedData = msg.getFixedData();
    byte[] customData = msg.getCustomData();
    byte[] buffer = new byte[13 + customData.length];

    System.arraycopy(fixedData, 0, buffer, 0, 10);
    buffer[10] = (byte)msg.getCommandTag();
    buffer[11] = (byte)(customData.length >> 8);
    buffer[11] |= msg.getUpperBits();
    buffer[12] = (byte)(customData.length & 0xFF);
    System.arraycopy(customData, 0, buffer, 13, customData.length);

    logger.finest("Assembled message (" + buffer.length + " bytes) -> " + DESUtil.bytesToString(buffer));
    if(traceLabel != null) System.out.println("Sent [" + traceLabel + "] " + DESUtil.bytesToString(buffer));
    byte[] encrypted;
    if(noEncrypt) encrypted = buffer;
    else {
      encrypted = DESUtil.desEncrypt(buffer, buffer.length, desKey16, maxSize);
      logger.finest("Encrypted message (" + encrypted.length + " bytes) -> " + DESUtil.bytesToString(encrypted));
    }

    try {
      os.write(encrypted.length >> 8);
      os.write(encrypted.length & 0xFF);
      os.write(encrypted);
      if(flush) {
        os.flush();        
        lastTrafficTimeStamp = System.currentTimeMillis();
      }
      msg.setSent(getRemoteAddress(), buffer, "Newcamd");

      if(logDupes) {
        // check for sent ecm/dcw duplicates
        if(lastSent != null && lastSent.getSequenceNr() != 0 && lastSent.getSequenceNr() == seq) {
          if(lastSent.isEcm() && msg.isEcm()) {
            long time = msg.getTimeStamp() - lastSent.getTimeStamp();
            logger.warning("Duplicate newcamd message sent.\n " + time + "\tPrevious: " + lastSent + "\n\tCurrent:  " + msg);
          }
        }
      }

      lastSent = msg;
      return seq;
    } catch(IOException e) {
      logger.throwing("Connection closed while sending", e);
      close();
    }
    return -1;
  }

  public int sendMessage(CamdNetMessage msg, int seq, byte[] desKey16) { // send and switch key afterwards
    seq = sendMessage(msg, seq, false);
    this.desKey16 = desKey16;
    try {
      os.flush();
      lastTrafficTimeStamp = System.currentTimeMillis();
      return seq;
    } catch(IOException e) {
      logger.throwing("Connection closed while sending", e);
      close();
    }
    return -1;
  }

  public void close() {
    try {
      if(conn != null) conn.close();
      conn = null;
    } catch(IOException e) {
      if(logger != null) logger.throwing("Exception while closing", e);
    }
  }

  public boolean isConnected() {
    return conn != null;
  }

  public boolean isInitialized() {
    return logger != null;
  }

  public long getLastTrafficTimeStamp() {
    return lastTrafficTimeStamp;
  }

  public synchronized String getRemoteAddress() {
    if(isConnected()) return conn.getInetAddress().getHostAddress();
    else return "0.0.0.0";
  }

  public void setSoTimeout(int soTimeout) throws SocketException {
    if(isConnected()) conn.setSoTimeout(soTimeout);
  }

  public void setSequenceNr(int sequenceNr) {
    this.sequenceNr = sequenceNr;
  }

  public void setLogger(ProxyLogger logger) {
    this.logger = logger;
  }

  public void setMaxSize(int maxSize) {
    this.maxSize = maxSize;
  }

  public void setTraceLabel(String traceLabel) {
    this.traceLabel = traceLabel;
  }
}
