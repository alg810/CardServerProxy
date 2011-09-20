package com.bowman.cardserv.util;

import com.bowman.cardserv.crypto.DESUtil;

import javax.management.modelmbean.DescriptorSupport;
import java.io.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2010-06-04
 * Time: 17:35
 */
public class SimpleTlvBlob {

  private Map contents = new LinkedHashMap();

  public SimpleTlvBlob() {}

  public SimpleTlvBlob(byte[] buf) {
    int pos = 0;
    int numRecords = buf[pos++], type, length, count;
    byte[] value;
    for(int i = 0; i < numRecords; i++) {
      type = buf[pos++];
      count = buf[pos++];
      length = buf[pos++];
      for(int n = 0; n < count; n++) {
        value = new byte[length];
        System.arraycopy(buf, pos, value, 0, length);
        add(type, value);
        pos += length;
      }
    }
  }

  public void add(int key, byte[] value) {
    Integer k = new Integer(key);
    List values = (List)contents.get(k);
    if(values == null) {
      values = new ArrayList();
      contents.put(k, values);
    }
    values.add(value);
  }

  public void addInt(int key, int value) {
    add(key, DESUtil.intToBytes(value, 4));
  }

  public void addShort(int key, short value) {
    add(key, DESUtil.intToBytes(value, 2));
  }

  public void addShort(int key, int[] values) {
    for(int i = 0; i < values.length; i++) addShort(key, (short)values[i]);
  }

  public List get(int key) {
    return (List)contents.get(new Integer(key));
  }

  public int[] getIntArray(int key) {
    List values = get(key);
    int[] ia = new int[values.size()];
    for(int i = 0; i < ia.length; i++) ia[i] = DESUtil.bytesToInt((byte[])values.get(i));
    return ia;
  }

  public byte[] getSingle(int key) {
    List values = get(key);
    return (byte[])values.get(0);
  }

  public Set keySet() {
    return contents.keySet();
  }

  public byte[] getBytes() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    baos.write(contents.size());
    Integer key; List values; byte[] value;
    for(Iterator iter = contents.keySet().iterator(); iter.hasNext(); ) {
      key = (Integer)iter.next();
      values = (List)contents.get(key);
      baos.write(key.intValue());
      baos.write(values.size());
      value = (byte[])values.get(0);
      baos.write(value.length);
      for(int i = 0; i < values.size(); i++) {
        try {
          baos.write((byte[])values.get(i));
        } catch(IOException ignored) {}
      }
    }
    return baos.toByteArray();
  }

  public boolean isEmpty() {
    return contents.isEmpty();
  }
}
