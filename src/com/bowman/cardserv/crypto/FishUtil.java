package com.bowman.cardserv.crypto;

import com.bowman.util.Blowfish;

import java.io.UnsupportedEncodingException;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2007-jan-28
 * Time: 00:35:08
 */
public class FishUtil {

  private static final String B64 = "./0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

  private static byte[] b64ToByte(String s) {

    char[] ec = s.toCharArray();
    StringBuffer dc = new StringBuffer();

    int k = -1;
    while(k < (ec.length - 1)) {

      int right = 0, left = 0, v, w, z;

      for(int i = 0; i < 6; i++) {
        v = B64.indexOf(ec[++k]);
        right |= v << (i * 6);
      }
      for(int i = 0; i < 6; i++) {
        v = B64.indexOf(ec[++k]);
        left |= v << (i * 6);
      }
      for(int i = 3; i >= 0; i--) {
        w = left & (0xFF << (i * 8));
        z = (w >> (i * 8)) & 0xFF;
        dc.append((char)z);
      }
      for(int i = 3; i >= 0; i--) {
        w = right & (0xFF << (i * 8));
        z = (w >> (i * 8)) & 0xFF;
        dc.append((char)z);
      }
    }

    byte[] result = null;
    try {
      result = dc.toString().getBytes("ISO-8859-1");
    } catch(UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return result;
  }

  private static String byteToB64(byte[] ec) {

    StringBuffer dc = new StringBuffer();
    int left, right, k = -1;

    while(k < (ec.length - 1)) {
      if(ec[k + 1] == 0x00 && ec[k + 2] == 0x00) return dc.toString();

      left = (ec[++k] & 0xFF) << 24;
      left += (ec[++k] & 0xFF) << 16;
      left += (ec[++k] & 0xFF) << 8;
      left += (ec[++k] & 0xFF);
      right = (ec[++k] & 0xFF) << 24;
      right += (ec[++k] & 0xFF) << 16;
      right += (ec[++k] & 0xFF) << 8;
      right += (ec[++k] & 0xFF);

      for(int i = 0; i < 6; i++) {
        dc.append(B64.charAt(right & 0x3F));
        right = right >> 6;
      }
      for(int i = 0; i < 6; i++) {
        dc.append(B64.charAt(left & 0x3F));
        left = left >> 6;
      }
    }
    return dc.toString();
  }

  public static String decryptString(String keyStr, String str) {
    return decryptString(keyStr, str, null);
  }

  public static String decryptString(String keyStr, String str, String enc) {

    // remove trailing incomplete blocks
    while(str.length() % 12 != 0) str = str.substring(0, str.length() - 1);
    if(str.length() == 0) return "";

    byte[] key = keyStr.getBytes();
    byte[] cipher = b64ToByte(str);

    int len = cipher.length;
    int padding = cipher.length % 8; // should always be 0?

    byte[] buff = new byte[len + padding]; // pad just in case
    if(padding != 0) {
      System.arraycopy(cipher, 0, buff, 0, len);
    } else buff = cipher;

    Blowfish f = new Blowfish();
    f.init(key, 0, key.length, false);
    byte[] clear = new byte[4096 + str.length()];

    int offs = 0;
    for(int i = 0; i < buff.length / 8; i++) {
      f.ecb(false, buff, offs, clear, offs);
      offs += 8;
    }

    int end = clear.length - 1;
    for(int i = 0; i < clear.length; i++) {
      if(clear[i] == 0x00) {
        end = i;
        break;
      }
    }

    if(enc == null) return new String(clear, 0, end);
    try {
      return new String(clear, 0, end, enc);
    } catch(UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return null;
  }

  public static String encryptString(String keyStr, String str) {

    byte[] key = keyStr.getBytes();
    byte[] clear = str.getBytes();

    int len = clear.length;
    int padding = 8 - (clear.length % 8);

    byte[] buff = new byte[len + padding];
    if(padding != 0) {
      System.arraycopy(clear, 0, buff, 0, len);
    } else buff = clear;

    Blowfish f = new Blowfish();
    f.init(key, 0, key.length, false);
    byte[] cipher = new byte[4096 + (str.length() * 2)];

    int offs = 0;
    for(int i = 0; i < buff.length / 8; i++) {
      f.ecb(true, buff, offs, cipher, offs);
      offs += 8;
    }

    return byteToB64(cipher);
  }

}
