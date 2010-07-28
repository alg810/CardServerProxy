package com.bowman.cardserv;

import com.bowman.cardserv.crypto.DESUtil;

import java.net.*;
import java.io.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Jan 6, 2010
 * Time: 7:27:03 AM
 */
public class CspConnection {

  private int sequenceNr = 0;
  private Socket conn;
  private DataInputStream is;
  private DataOutputStream os;

  private long lastTrafficTimeStamp = System.currentTimeMillis();

  public CspConnection(Socket conn) {
    this.conn = conn;
  }

  public void init() throws IOException {
    is = new DataInputStream(new BufferedInputStream(conn.getInputStream()));
    os = new DataOutputStream(new BufferedOutputStream(conn.getOutputStream()));
  }

  public int sendCspMessage(CspNetMessage msg) throws IOException { // send request
    int seq = sequenceNr++;
    if(sequenceNr > 0xFFFF) sequenceNr = 0;
    return sendCspMessage(msg, seq);
  }

  public synchronized int sendCspMessage(CspNetMessage msg, int seqNr) throws IOException { // send request or reply
    msg.setSeqNr(seqNr);

    os.writeByte(msg.getType()); // 1 byte, msg type
    os.writeShort(msg.getSeqNr()); // 2 bytes, sequence number
    switch(msg.getType()) {
      case CspNetMessage.TYPE_ECMREQ:
        writeEcmReq(msg.getCamdMessage());
        break;
      case CspNetMessage.TYPE_DCWRPL:
        writeDcwRpl(msg.getCamdMessage());
        break;
      case CspNetMessage.TYPE_FULLSTATE:
      case CspNetMessage.TYPE_INCRSTATE:
        writeStatusUpdate(msg);
        break;
      case CspNetMessage.TYPE_STATEACK:
        // dummy message, no contents
        break;
      default:
        throw new IOException("Unknown CspNetMessage type: " + DESUtil.byteToString((byte)msg.getType()));
    }
    os.flush();
    lastTrafficTimeStamp = System.currentTimeMillis();
    if(msg.getCamdMessage() != null)
      msg.getCamdMessage().setSent(conn.getInetAddress().getHostAddress(), new byte[0], "Csp"); // todo

    return msg.getSeqNr();
  }

  private void writeEcmReq(CamdNetMessage camdMsg) throws IOException {
    os.writeByte(camdMsg.getCommandTag()); // 1 byte, old newcamd command tag/dvb table id
    os.writeInt(camdMsg.getOriginId()); // 4 bytes, id of proxy that originally received this request from client
    os.writeShort(camdMsg.getNetworkId()); // 2 bytes, dvb original network id
    os.writeShort(camdMsg.getCaId()); // 2 bytes, ca id
    os.writeInt(camdMsg.getProviderIdent()); // 4 bytes, provider ident (if any, or 0)
    os.writeShort(camdMsg.getServiceId()); // 2 bytes, sid
    os.writeShort(camdMsg.getDataLength()); // 2 bytes, ecm length
    os.write(camdMsg.getCustomData());
  }

  private void writeDcwRpl(CamdNetMessage camdMsg) throws IOException {
    os.writeByte(camdMsg.getCommandTag()); // 1 byte, old newcamd command tag/dvb table id
    os.writeShort(camdMsg.getServiceId()); // 2 bytes, sid
    os.writeByte(camdMsg.getDataLength()); // 1 byte, length - should be 0 or 16
    os.write(camdMsg.getCustomData());
  }

  private void writeStatusUpdate(CspNetMessage msg) throws IOException {
    int count = msg.getUpdateCount();
    os.writeInt(msg.getOriginId()); // 4 bytes, id of proxy sending the update (i.e always this one)
    os.writeByte(count); // 1 byte, number of status updates in this message (can be 0, for a keep-alive msg)

    if(count > 0) { // 0 = keep-alive message, no contents
      CspNetMessage.StatusChange sc;
      for(Iterator iter = msg.getStatusUpdates().iterator(); iter.hasNext(); ) {
        sc = (CspNetMessage.StatusChange)iter.next();
        os.writeByte(sc.available?1:0); // 1 byte, operation type (1 = added)
        os.writeByte(sc.type); // 1 byte, type of state update
        os.writeShort(sc.key.onid); // 2 bytes, onid
        os.writeShort(sc.key.caid); // 2 bytes, caid
        switch(sc.type) {
          case CspNetMessage.STATE_SIDS: // sids and extra, send as short (2 bytes unsigned)
          case CspNetMessage.STATE_EXTRA:
            Integer[] shortItems = sc.getUpdatedItemsInt();
            os.writeShort(shortItems.length);
            for(int i = 0; i < shortItems.length; i++) os.writeShort(shortItems[i].intValue());
            break;
          case CspNetMessage.STATE_PROVIDERS: // provider idents, send as int (4 bytes signed)
            Integer[] items = sc.getUpdatedItemsInt();
            os.writeShort(items.length);
            for(int i = 0; i < items.length; i++) os.writeInt(items[i].intValue());
            break;
          case CspNetMessage.STATE_CUSTOM: // custom data, send as long (8 bytes signed)
            Long[] longItems = sc.getUpdatedItemsLong();
            os.writeShort(longItems.length);
            for(int i = 0; i < longItems.length; i++) os.writeLong(longItems[i].longValue());
            break;
          default:
            throw new IOException("Unknown state type:" + DESUtil.byteToString((byte)sc.type));
        }
      }
    }
  }

  public CspNetMessage readMessage() throws IOException {
    if(is == null) return null;

    // first byte = message type, next two bytes = sequence nr
    CspNetMessage msg = new CspNetMessage(is.readUnsignedByte(), is.readUnsignedShort());
    switch(msg.getType()) {
      case CspNetMessage.TYPE_ECMREQ:
        msg.setCamdMessage(CamdNetMessage.parseCspEcmReq(is, getRemoteAddress(), msg.getSeqNr()));
        break;
      case CspNetMessage.TYPE_DCWRPL:
        msg.setCamdMessage(CamdNetMessage.parseCspDcwRpl(is, getRemoteAddress(), msg.getSeqNr()));
        break;
      case CspNetMessage.TYPE_FULLSTATE:
      case CspNetMessage.TYPE_INCRSTATE:
        CspNetMessage.parseStatusChange(is, msg);
        break;
      case CspNetMessage.TYPE_STATEACK:
        // dummy message, no contents
        break;
      case 0x48: // H as in HTTP :) revert back to http to handle failed login
        readHttpReply();
        break;
      default:
        throw new IOException("Unknown CspNetMessage type: " + DESUtil.byteToString((byte)msg.getType()));
    }
    lastTrafficTimeStamp = System.currentTimeMillis();

    return msg;
  }

  private void readHttpReply() throws IOException {
    int b; StringBuffer sb = new StringBuffer("HTT");
    while( (b = is.read()) != -1) {
      sb.append((char)b);
    }
    String reply = sb.toString().split(String.valueOf((char)0x0D))[0];
    close();
    throw new IOException(reply);
  }

  public void close() {
    try {
      if(conn != null) conn.close();
      conn = null;
    } catch(IOException e) {}
  }

  public boolean isConnected() {
    return conn != null;
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

}
