package com.bowman.cardserv;

import com.bowman.cardserv.crypto.DESUtil;

import java.io.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 15, 2005
 * Time: 4:22:46 PM
 */
public class CardData {

  private byte[] data;
  private int caId;
  private int userId;
  private int providerCount;
  private String cardNumber, label;
  private List providers = new ArrayList();
  private List provIds = new ArrayList();
  private Exception parseException;

  public CardData(byte[] raw) {
    this(raw, null);
  }

  public CardData(byte[] raw, String label) {
    this.label = label;
    this.data = raw;
    this.userId = raw[0] & 0xFF;
    this.caId = (raw[1] & 0xFF) * 256 + (raw[2] & 0xFF);
    try {
      this.cardNumber = DESUtil.bytesToString(raw, 3, 8);
    } catch (Exception e) {
      parseException = e;
      e.printStackTrace();
      cardNumber = "00 00 00 00 00 00 00 00";
    }
    this.providerCount = raw[11];

    for(int i = 0; i < providerCount; i++) {
      try {
        providers.add(DESUtil.bytesToString(raw, 12 + i * 11, 3));
        provIds.add(DESUtil.bytesToString(raw, 15 + i * 11, 8));
      } catch (Exception e) {
        parseException = e;
        e.printStackTrace();
      }
    }
  }

  public byte[] getData(boolean anonymize) {
    if(!anonymize) return data;
    else {
      byte[] anonData = new byte[12 + (providerCount * 11)];
      System.arraycopy(data, 0, anonData, 0, Math.min(anonData.length, data.length));
      for(int i = 3; i < 11; i++) anonData[i] = 0;
      for(int i = 0; i < providerCount; i++) {
        for(int n = 0; n < 8; n++) anonData[15 + i * 11 + n] = 0;
      }
      if(anonData[0] == 1) anonData[0] = 2;
      return anonData;
    }
  }

  public int getUserId() {
    return userId;
  }

  public int getProviderCount() {
    return providerCount;
  }

  public int getCaId() {
    return caId;
  }

  public String getCaIdStr() {
    String s = Integer.toHexString(caId);
    while(s.length() < 4) s = "0" + s;
    return s;
  }

  public String getProvidersStr() {
    return providers.toString();
  }

  public String[] getProviders() {
    return (String[])providers.toArray(new String[providers.size()]);
  }

  public Integer[] getProvidersAsInt() {
    Integer[] i = new Integer[providers.size()];
    for(int n = 0; n < i.length; n++) i[n] = new Integer(DESUtil.byteStringToInt((String)providers.get(n)));
    return i;
  }

  public String getProvIdsStr() {
    return provIds.toString();
  }

  public String[] getProvIds() {
    return (String[])provIds.toArray(new String[provIds.size()]);
  }

  public String getCardNumber() {
    return cardNumber;
  }

  public String toString() {
    if(parseException != null) return "Failed parse: " + parseException;
    else {
      String label = isAnonymous()?"Yes":"No (" + this.label + ")";
      return "UserID [" + userId + "] CaID [" + getCaIdStr() + "] Providers [" + getProviderCount() + "] " +
          getProvidersStr() + " Anonymous [" + label  + "]";
    }        
  }

  public Exception getParseException() {
    return parseException;
  }

  public boolean isAnonymous() {
    return "00 00 00 00 00 00 00 00".equals(cardNumber);
  }

  public boolean equals(Object o) {
    if(this == o) return true;
    if(o == null || getClass() != o.getClass()) return false;
    CardData cardData = (CardData)o;
    return Arrays.equals(data, cardData.data);
  }

  public int hashCode() {
    return (data != null?data.hashCode():0);
  }

  public boolean dump(File f) {
    try {
      BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(f));
      bos.write(data);
      bos.flush();
      bos.close();
    } catch(IOException e) {
      return false;
    }
    return true;
  }

  public static CardData createEmptyData(int caId) {
    byte[] buf = new byte[23];
    buf[0] = 0x02; // user id, not 1
    buf[1] = (byte)((caId >> 8) & 0xFF); // caId
    buf[2] = (byte)(caId & 0xFF);
    // 3-10 = 0 (card number)
    buf[11] = 1; // provider count
    // ident & id = 0

    return new CardData(buf);
  }

  public static CardData createFromFile(File f) throws IOException {
    byte[] buf = new byte[(int)f.length()];
    DataInputStream dis = new DataInputStream(new FileInputStream(f));
    dis.readFully(buf);
    return new CardData(buf, f.getName());
  }

  public static CardData createMergedData(CardData card, Integer[] addProviders) {
    if(addProviders == null || addProviders.length == 0) return card;
    byte[] buf = new byte[card.data.length + (11 * addProviders.length)];
    System.arraycopy(card.data, 0, buf, 0, card.data.length);
    buf[11] += addProviders.length;
    byte[] prov; int n = 0;
    for(int i = card.getProviderCount(); i < buf[11]; i++) {
      prov = DESUtil.intToBytes(addProviders[n++].intValue(), 3);
      buf[12 + i * 11] = prov[0];
      buf[13 + i * 11] = prov[1];
      buf[14 + i * 11] = prov[2];
      // remaining 8 of the 11 = 0 (provider ids)
    }
    return new CardData(buf);
  }

  public static CardData createData(int caId, String[] providers) {
    Integer[] pis = new Integer[providers.length];
    for(int i = 0; i < pis.length; i++) pis[i] = new Integer(DESUtil.byteStringToInt(providers[i].trim()));
    return createData(caId, pis);
  }

  public static CardData createData(int caId, Integer[] providers) {
    if(providers == null || providers.length == 0) providers = new Integer[] {new Integer(0)};
    byte[] buf = new byte[12 + 11 * providers.length];
    buf[0] = 0x02; // user id, not 1
    buf[1] = (byte)((caId >> 8) & 0xFF); // caId
    buf[2] = (byte)(caId & 0xFF);
    // 3-10 = 0 (card number)
    buf[11] = (byte)(providers.length & 0xFF); // provider count
    byte[] prov;
    for(int i = 0; i < providers.length; i++) {
      prov = DESUtil.intToBytes(providers[i].intValue(), 3);
      buf[12 + i * 11] = prov[0];
      buf[13 + i * 11] = prov[1];
      buf[14 + i * 11] = prov[2];
      // remaining 8 of the 11 = 0 (provider ids)
    }

    return new CardData(buf);
  }

}
