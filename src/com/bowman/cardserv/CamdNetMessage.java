package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.CamdConstants;
import com.bowman.cardserv.crypto.DESUtil;

import java.lang.reflect.Field;
import java.util.*;
import java.io.*;

/*
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 8, 2005
 * Time: 4:12:17 PM
 */
public class CamdNetMessage implements CamdConstants, Serializable {

  private static final long serialVersionUID = -961908656343234789L;

  public static final int TYPE_NEW = 0, TYPE_SENT = -1, TYPE_RECEIVED = -2;

  public static String[] MSG_NAMES = new String[0x1D + 1];

  static {
    Field[] fields = CamdNetMessage.class.getFields();
    for(int i = 0; i < fields.length; i++)
      if(fields[i].getType() == int.class && fields[i].getName().startsWith("MSG_")) {
        try {
          MSG_NAMES[fields[i].getInt(null) - CWS_FIRSTCMDNO] = fields[i].getName();
        } catch(IllegalAccessException e) {}
      }
  }

  public static CamdNetMessage getNewcamdLoginMessage(String user, String password, byte[] clientId) {
    CamdNetMessage loginMsg = new CamdNetMessage(MSG_CLIENT_2_SERVER_LOGIN);
    password = DESUtil.cryptPassword(password);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      baos.write(user.getBytes());
      baos.write(0);
      baos.write(password.getBytes());
      baos.write(0);
    } catch (IOException e) {
      e.printStackTrace();
    }
    loginMsg.customData = baos.toByteArray();
    if(clientId.length == 2) {
      loginMsg.fixedData[2] = clientId[0];
      loginMsg.fixedData[3] = clientId[1];
    }    
    return loginMsg;
  }

  public static CamdNetMessage parseCspEcmReq(DataInputStream is, String remoteAddr, int seqNr) throws IOException {
    CamdNetMessage msg = new CamdNetMessage(is.readUnsignedByte()); // 1 byte, old newcamd command tag/dvb table id        

    msg.protocol = "Csp";
    msg.type = TYPE_RECEIVED;
    msg.rawIn = new byte[0]; // todo
    msg.remoteAddress = remoteAddr;
    msg.setSequenceNr(seqNr); // use seqnr from CspNetMessage wrapper, for logging purposes only

    msg.originId = is.readInt(); // 4 bytes, id for the proxy that first received this
    msg.networkId = is.readUnsignedShort(); // 2 bytes, dvb original network id
    msg.caId = is.readUnsignedShort(); // 2 bytes, ca-id
    msg.providerIdent = is.readInt(); // 4 bytes, provider ident associated with this message, if any (otherwise 0)

    msg.setServiceId(is.readUnsignedShort()); // 2 bytes, sid
    msg.dataLength = is.readUnsignedShort(); // 2 bytes, ecm length
    if(msg.dataLength == 0) throw new IOException("0 byte ecm received");
    msg.customData = new byte[msg.dataLength];
    is.readFully(msg.customData);
    msg.refreshDataHash();
    if(!msg.isEcm()) throw new IOException("bad command byte: " + msg.getCommandName());
    
    return msg;
  }

  public static CamdNetMessage parseCspDcwRpl(DataInputStream is, String remoteAddr, int seqNr) throws IOException {
    CamdNetMessage msg = new CamdNetMessage(is.readUnsignedByte()); // 1 byte, old command id/dvb table id
    msg.protocol = "Csp";
    msg.type = TYPE_RECEIVED;
    msg.rawIn = new byte[0]; // todo
    msg.remoteAddress = remoteAddr;
    msg.setSequenceNr(seqNr); // use seqnr from CspNetMessage wrapper, for logging purposes only

    msg.setServiceId(is.readUnsignedShort()); // 2 bytes, sid - not strictly necessary, just a sanity check
    msg.dataLength = is.readUnsignedByte(); // 1 byte, length
    if(msg.dataLength > 16) throw new IOException(msg.dataLength + " byte dcw received");
    msg.customData = new byte[msg.dataLength]; // 16 or 0
    is.readFully(msg.customData);
    msg.refreshDataHash(); // just in case
    if(!msg.isEcm()) throw new IOException("bad command byte: " + msg.getCommandName());

    return msg;
  }

  public static CamdNetMessage parseNewcamd(byte[] raw, String remoteAddr) {
    CamdNetMessage msg = new CamdNetMessage(raw, "Newcamd");
    msg.commandTag = raw[10] & 0xFF;
    msg.dataLength = (raw[11] & 0x0F) * 256 + (raw[12] & 0xFF);
    msg.upperBits = raw[11] & 0xF0;
    msg.customData = new byte[msg.dataLength];
    msg.fixedData = new byte[10];
    System.arraycopy(raw, 13, msg.customData, 0, msg.dataLength);
    System.arraycopy(raw, 0, msg.fixedData, 0, 10);
    msg.parseStringData();
    msg.refreshDataHash();
    msg.type = TYPE_RECEIVED;
    msg.remoteAddress = remoteAddr;
    return msg;
  }

  public static CamdNetMessage parseRadegast(int commandTag, byte[] data, byte[] raw, String remoteAddr) {
    CamdNetMessage msg = new CamdNetMessage(raw, "Radegast");
    msg.commandTag = commandTag;
    msg.fixedData = new byte[10];
    msg.dataLength = data.length;
    msg.customData = new byte[msg.dataLength];
    System.arraycopy(data, 0, msg.customData, 0, data.length);
    msg.stringData = new String[0];
    msg.refreshDataHash();
    msg.type = TYPE_RECEIVED;
    msg.remoteAddress = remoteAddr;
    if(msg.isEcm()) msg.upperBits = 0x70; // no idea what these mean, just trying to mimic what mgcamd puts there
    return msg;
  }

  public static CamdNetMessage parseCacheReq(DataInputStream dais) throws IOException {
    CamdNetMessage msg = new CamdNetMessage(dais.readUnsignedByte());
    msg.setServiceId(dais.readUnsignedShort());
    msg.protocol = "Dummy";
    msg.dataHash = dais.readInt();
    if(dais.available() == 8) { // this is a request with arbitration set
      msg.arbiterNumber = new Double(dais.readDouble());
    }
    return msg;
  }

  public static CamdNetMessage parseCacheRpl(DataInputStream dais) throws IOException {
    CamdNetMessage msg = new CamdNetMessage(dais.readUnsignedByte());
    msg.type = TYPE_RECEIVED;
    msg.protocol = "Dummy";

    if(dais.available() > 0) { // this is a cw reply
      msg.setServiceId(dais.readUnsignedShort());
      msg.caId = dais.readInt();
      msg.customData = new byte[16];
      dais.readFully(msg.customData);
      msg.refreshDataHash(); // just in case
      msg.dataLength = 16;
      try {
        if(dais.available() > 0) msg.connectorName = dais.readUTF();
      } catch (EOFException e) {
      }
    }
    return msg;
  }


  private long timeStamp;
  private int commandTag;
  private int dataLength;
  private byte[] fixedData, customData;
  private byte[] rdgProviderId = "00000000".getBytes(), rdgKeyNumber = "0000".getBytes();
  private int upperBits;
  private String[] stringData;
  private Set providerContext = new HashSet();
  private int caId, networkId, providerIdent = -1;
  private int customId;
  private int dataHash;
  private int type;
  private String remoteAddress, originAddress;
  private String connectorName;

  // cache metadata
  private boolean instant, timeOut;
  private Double arbiterNumber;

  // original unparsed protocol-specific data
  private transient byte[] rawIn, rawOut;
  private transient String protocol, filteredBy, profileName;
  
  private transient long cacheTime, queueTime, cwsTime, clientTime;
  private transient int originId;

  private CamdNetMessage() {
    this.timeStamp = System.currentTimeMillis();
  }

  private CamdNetMessage(byte[] raw, String protocol) {
    this();
    this.rawIn = raw;
    this.protocol = protocol;
  }

  public CamdNetMessage(CamdNetMessage msg) {
    this();
    this.protocol = msg.protocol;
    this.rawIn = msg.rawIn;
    this.rawOut = msg.rawOut;
    this.commandTag = msg.commandTag;
    this.upperBits = msg.upperBits;
    setCustomData(msg.customData);
    setFixedData(msg.fixedData);
    this.dataHash = msg.dataHash;
    this.remoteAddress = msg.remoteAddress;
    this.originAddress = msg.originAddress;
    this.rdgKeyNumber = msg.rdgKeyNumber;
    this.rdgProviderId = msg.rdgProviderId;
    this.arbiterNumber = msg.arbiterNumber;
    this.connectorName = msg.connectorName;
    this.type = msg.type;
    this.caId = msg.caId;
    this.providerIdent = msg.providerIdent;
    this.filteredBy = msg.filteredBy;
    this.providerContext.addAll(msg.providerContext);
    this.originId = msg.originId;
    this.networkId = msg.networkId;
    this.customId = msg.customId;
    this.profileName = msg.profileName;
  }

  public CamdNetMessage(int commandTag) {
    this();
    this.commandTag = commandTag;
    this.dataLength = 0;
    this.customData = new byte[0];
    this.stringData = new String[0];
    this.fixedData = new byte[10];
    this.type = TYPE_NEW;
  }

  public int getCommandTag() {
    return commandTag;
  }

  public void setCommandTag(int commandTag) {
    this.commandTag = commandTag;
  }

  public int getDataLength() {
    return dataLength;
  }

  public byte[] getCustomData() {
    return customData;
  }

  public byte[] getRdgProviderId() {
    return rdgProviderId;
  }

  public byte[] getRdgKeyNumber() {
    return rdgKeyNumber;
  }

  public void setRdgProviderId(byte[] rdgProviderId) {
    this.rdgProviderId = rdgProviderId;
    providerContext.add(DESUtil.bytesToString(rdgProviderId));
    providerIdent = DESUtil.bytesToInt(rdgProviderId);
  }

  public void setRdgKeyNumber(byte[] rdgKeyNumber) {
    this.rdgKeyNumber = rdgKeyNumber;
  }

  public int getUpperBits() {
    return upperBits;
  }

  public void setCustomData(byte[] customData) {
    this.customData = new byte[customData.length];
    System.arraycopy(customData, 0, this.customData, 0, customData.length);
    dataLength = customData.length;
    if(isOsdMsg()) parseStringData();
  }

  public void refreshDataHash() {
    try {
      dataHash = new String(customData, "ISO-8859-1").hashCode();
    } catch(UnsupportedEncodingException e) {
      e.printStackTrace();
    }
  }

  public void clearData() {
    this.customData = new byte[0];
  }

  public void setFixedData(byte[] fixedData) {
    this.fixedData = new byte[fixedData.length];
    System.arraycopy(fixedData, 0, this.fixedData, 0, fixedData.length);
  }

  public void setCaId(int caId) {
    this.caId = caId;
  }

  public void setProviderContext(String[] providers) {
    if(providers != null) {
      providerContext = new HashSet();
      providerContext.addAll(Arrays.asList(providers));
      if(providers.length == 1) setProviderIdent(DESUtil.byteStringToInt(providers[0]));
    }
  }

  public Set getProviderContext() {
    return providerContext;
  }

  public int getCaId() {
    return caId;
  }

  public int getCaIdFromHdr() {
    int cId = (fixedData[4] & 0xFF) << 8;
    cId |= fixedData[5] & 0xFF;
    return cId;  
  }

  public int getProviderFromHdr() {
    byte[] pi = new byte[3];
    System.arraycopy(fixedData, 6, pi, 0, 3);
    return DESUtil.bytesToInt(pi);
  }

  public void setProviderInHdr(int provider) {
    byte[] buf = DESUtil.intToBytes(provider, 3);
    System.arraycopy(buf, 0, fixedData, 6, 3);
  }

  public void setCaIdInHdr(int id) {
    fixedData[4] = (byte)((id >> 8 ) & 0xFF);
    fixedData[5] = (byte)(id & 0xFF);
  }

  public byte[] getFixedData() {
    return fixedData;
  }

  public String[] getStringData() {
    return stringData;
  }

  public int getType() {
    return type;
  }

  public long getTimeStamp() {
    return timeStamp;
  }

  public int getClientId() {
    int cId = (fixedData[2] & 0xFF) << 8;
    cId |= fixedData[3] & 0xFF;
    return cId;
  }

  public String getClientIdStr() {
    int cId = getClientId();
    for(int i = 0; i < CL_IDS.length; i++)
      if(CL_IDS[i] == cId) return CL_NAMES[i];
    String unknown = Integer.toHexString(cId);
    while(unknown.length() < 4) unknown = "0" + unknown;
    return "? (0x" + unknown + ")"; 
  }

  public void setSent(String remoteAddr, byte[] rawOut, String protocol) {
    this.type = TYPE_SENT;
    this.remoteAddress = remoteAddr;
    this.rawOut = rawOut;
    this.protocol = protocol;
  }

  public void setSequenceNr(int sequenceNr) {
    fixedData[0] = (byte)((sequenceNr >> 8) & 0xFF);
    fixedData[1] = (byte)(sequenceNr & 0xFF);
  }

  public String getRemoteAddress() {
    return remoteAddress;
  }

  public CamdNetMessage getEmptyReply() {
    if(!isEcm()) throw new IllegalStateException("getEcmReply on a Non-ECM message: " + this);
    CamdNetMessage reply = new CamdNetMessage(getCommandTag());
    reply.setSequenceNr(getSequenceNr());
    reply.setCaId(getCaId());
    return reply;
  }

  public CamdNetMessage getEmmReply() {
    if(!isEmm()) throw new IllegalStateException("getEmmReply on a Non-EMM message: " + this);
    CamdNetMessage reply = new CamdNetMessage(getCommandTag());
    reply.setSequenceNr(getSequenceNr());
    reply.setCaId(getCaId());
    reply.upperBits = 0x10; // correct signature?
    return reply;
  }

  public boolean isEmm() {
    return commandTag >= 0x82 && commandTag <= 0x8F;
  }

  public boolean isEcm() {
    return commandTag == 0x80 || commandTag == 0x81;
  }

  public boolean isDcw() {
    return isEcm() && (dataLength == 16 || dataLength == 0);
  }

  public boolean isOsdMsg() {
    return commandTag == EXT_OSD_MESSAGE;
  }

  public boolean isKeepAlive() {
    return commandTag == MSG_KEEPALIVE;
  }

  public int getSequenceNr() {
    int nr = 0;
    nr |= (fixedData[0] & 0xFF) << 8;
    nr |= (fixedData[1] & 0xFF);
    return nr;
  }

  public int getServiceId() {
    int id = 0;
    id |= (fixedData[2] & 0xFF) << 8;
    id |= (fixedData[3] & 0xFF);
    return id;
  }

  public void setServiceId(int id) {
    fixedData[2] = (byte)((id >> 8 ) & 0xFF);
    fixedData[3] = (byte)(id & 0xFF);
  }

  public String getCommandName() {
    switch(commandTag) {
      case EXT_OSD_MESSAGE: return "EXT_OSD_MESSAGE (" + stringData[0] + ")";
      case EXT_ADD_CARD: return "EXT_ADD_CARD";
      case EXT_REMOVE_CARD: return "EXT_REMOVE_CARD";
      case EXT_GET_VERSION: return "EXT_GET_VERSION" + (stringData.length > 0 ? " (" + stringData[0] + ")":"");
      case EXT_SID_LIST: return "EXT_SID_LIST";
    }
    int index = commandTag - CWS_FIRSTCMDNO;
    if(index >= 0 && index < MSG_NAMES.length) return MSG_NAMES[index];
    else if(isEcm()) {
      if(dataLength == 16 || dataLength == 0) return "DCW (0x" + Integer.toHexString(commandTag) + ")";
      else return "ECM (0x" + Integer.toHexString(commandTag) + ")";
    }
    else if(isEmm()) return "EMM (0x" + Integer.toHexString(commandTag) + ")";
    else return "*UNKNOWN* (0x" + Integer.toHexString(commandTag) + ")";
  }

  private void parseStringData() {
    StringBuffer sb = new StringBuffer();
    List strings = new ArrayList();
    for(int i = 0; i < dataLength; i++) {
      if(customData[i] == 0) {
        strings.add(sb.toString());
        sb = new StringBuffer();
      } else sb.append((char)customData[i]);
    }
    // if(isOsdMsg())
    if(sb.length() > 0) strings.add(sb.toString());
    stringData = (String[])strings.toArray(new String[strings.size()]);
  }

  public String toString() {
    String data;
    if(isEcm()) data = " >> " + hashCodeStr();
    else data = (getDataLength()==0?"":" >> Custom data: " + getDataLength() + " bytes >> " +
            DESUtil.bytesToString(customData, 16) + (getDataLength()>16?" ...":""));
    return "[" + getSequenceNr() + " " + Integer.toHexString(getServiceId()) + "] " + getCommandName() + " >> " +
        DESUtil.bytesToString(fixedData) + " >> " + getUpperBitsStr() + data;
  }

  public String toDebugString() {
    return hashCodeStr() + " " + getCommandName() + " [" + DESUtil.bytesToString(customData) + "] (" +
        (connectorName==null?"unknown":connectorName) + ")";
  }

  private String getUpperBitsStr() {
    String s = Integer.toBinaryString(upperBits >> 4);
    while(s.length() < 4) s = "0" + s;
    return s + " (" + DESUtil.byteToString((byte)upperBits) + ")";
  }

  public int getDataHash() {
    return dataHash;
  }

  public String getOriginAddress() {
    return originAddress;
  }

  public void setOriginAddress(String originAddress) {
    this.originAddress = originAddress;
  }

  public String getConnectorName() {
    return connectorName;
  }

  public void setConnectorName(String connectorName) {
    this.connectorName = connectorName;
  }

  public boolean isEmpty() {
    return dataLength == 0;
  }

  public boolean equals(Object o) {
    if(this == o) return true;
    if(o == null || getClass() != o.getClass()) return false;
    final CamdNetMessage that = (CamdNetMessage)o;
    return hashCode() == that.hashCode();
  }

  public int hashCode() {
    // int result;
    // result = commandTag;
    // result = 29 * result + dataHash;
    // return result;
    return dataHash;
  }

  public String hashCodeStr() {
    StringBuffer sb = new StringBuffer(Integer.toHexString(hashCode()));
    while(sb.length() < 8) sb.insert(0, '0');
    return sb.toString().toUpperCase();
  }

  public void setInstant(boolean instant) {
    this.instant = instant;
  }

  public boolean isInstant() {
    return instant;
  }

  public void setTimeOut(boolean timeOut) {
    this.timeOut = timeOut;
  }

  public boolean isTimeOut() {
    return timeOut;
  }

  public void setArbiterNumber(Double arbiterNumber) {
    this.arbiterNumber = arbiterNumber;
  }

  public Double getArbiterNumber() {
    return arbiterNumber;
  }

  public byte[] getRawIn() {
    return rawIn;
  }

  public byte[] getRawOut() {
    return rawOut;
  }

  public String getProtocol() {
    return protocol;
  }

  public String getLabel() {
    return protocol + " SEQ:" + getSequenceNr() + " SID:" + Integer.toHexString(getServiceId()) + " " +
        getCommandName() + " " + dataLength + " bytes";
  }

  public void setCacheTime(long cacheTime) {
    this.cacheTime = cacheTime;
  }

  public void setQueueTime(long queueTime) {
    this.queueTime = queueTime;
  }

  public void setCWSTime(long cwsTime) {
    this.cwsTime = cwsTime;
  }

  public void setClientTime(long clientTime) {
    this.clientTime = clientTime;
  }

  public long getCacheTime() {
    return cacheTime;
  }

  public long getQueueTime() {
    return queueTime;
  }

  public long getCWSTime() {
    return cwsTime;
  }

  public long getClientTime() {
    return clientTime;
  }

  public void setFilteredBy(String filteredBy) {
    this.filteredBy = filteredBy;
  }

  public String getFilteredBy() {
    return filteredBy;
  }

  public boolean isFiltered() {
    return filteredBy != null;
  }

  public int getOriginId() {
    return originId;
  }

  public void setOriginId(int originId) {
    this.originId = originId;
  }

  public int getNetworkId() {
    return networkId;
  }

  public void setNetworkId(int networkId) {
    this.networkId = networkId;
  }

  public int getProviderIdent() {
    return providerIdent;
  }

  public void setProviderIdent(int providerIdent) {
    this.providerIdent = providerIdent;
  }

  public int getCustomId() {
    return customId;
  }

  public void setCustomId(int customId) {
    this.customId = customId;
  }

  public void setProfileName(String profileName) {
    this.profileName = profileName;
  }

  public String getProfileName() {
    return profileName;
  }

}
