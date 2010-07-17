package com.bowman.cardserv.crypto;

import com.bowman.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 8, 2005
 * Time: 8:46:40 PM
 */
public class DESUtil {

  public static final String PW_SALT = "$1$abcdefgh$";

  private static final int DES_KEYSCHED_SZ = 32;

  public static byte[] xorKey(byte[] desKey14, byte[] xorKey14) {
    byte[] newKey = new byte[14];
    for(int i = 0; i < 14; i++) newKey[i] = (byte)(desKey14[i] ^ xorKey14[i]);
    return newKey;
  }

  public static byte[] getRandomBytes(int len) {
    byte[] random = new byte[len];
    for(int i = 0; i < len; i++) random[i] = (byte)(Math.random() * 256.0);
    return random;
  }

  public static byte[] xorUserPass(byte[] desKey14, String cryptPw) {
    byte[] newKey = new byte[14];
    System.arraycopy(desKey14, 0, newKey, 0, 14);
    byte[] cryptBytes = cryptPw.getBytes();
    for(int i = 0; i < cryptPw.length(); i++) newKey[i%14] ^= cryptBytes[i];
    return newKey;
  }

  public static boolean checkPassword(String pass, String cryptPw) {
    return cryptPw.equals(cryptPassword(pass));
  }

  public static String cryptPassword(String pass) {
    return MD5Crypt.crypt(pass, PW_SALT);
  }

  public static byte[] desKeySpread(byte[] desKey14) {

    byte[] key1 = new byte[7], key2 = new byte[7];
    System.arraycopy(desKey14, 0, key1, 0, 7);
    System.arraycopy(desKey14, 7, key2, 0, 7);
    key1 = addParity(key1);
    key2 = addParity(key2);

    byte[] desKey16 = new byte[16];
    System.arraycopy(key1, 0, desKey16, 0, 8);
    System.arraycopy(key2, 0, desKey16, 8, 8);

    return desKey16;
  }

  public static String bytesToString(byte[] bytes, int offs, int len) {
    StringBuffer sb = new StringBuffer();
    String bt;
    if(len > bytes.length) len = bytes.length;
    for(int i = 0; i < len && (i + offs < bytes.length); i++) {
      bt = Integer.toHexString(bytes[offs + i] & 0xFF);
      if(bt.length() == 1) sb.append('0');
      sb.append(bt);
      sb.append(' ');
    }
    return sb.toString().trim().toUpperCase();
  }

  public static String bytesToString(byte[] bytes, int len) {
    return bytesToString(bytes, 0, len);
  }

  public static String byteToString(byte b) {
    String s = Integer.toHexString(b & 0xFF);
    if(s.length() == 1) s = "0" + s;
    return "0x" + s;
  }

  public static String bytesToString(byte[] bytes) {
    return bytesToString(bytes, bytes.length);
  }

  public static byte[] stringToBytes(String s) {
    String[] hex = s.trim().split(" ");
    if(hex.length == 1 && "".equals(hex[0])) return new byte[0];
    byte[] buf = new byte[hex.length];
    for(int i = 0; i < hex.length; i++) buf[i] = (byte)(Integer.parseInt(hex[i], 16) & 0xFF);
    return buf;
  }

  public static int byteStringToInt(String s) {
    return bytesToInt(stringToBytes(s));
  }

  public static byte[] intToBytes(int i, int bytes) {
    byte[] buf = new byte[bytes]; int shift;
    for(int n = 0; n < bytes; n++) {
      shift = (bytes - n - 1) * 8;
      buf[n] = (byte)((i >> shift) & 0xFF);
    }
    return buf;
  }

  public static String intToByteString(int i, int bytes) {
    return bytesToString(intToBytes(i, bytes));
  }

  public static int bytesToInt(byte[] bytes) {
    long l = 0;
    for(int i = 0; i < bytes.length; i++) {
      l |= (bytes[i] & 0xFF) << ((bytes.length - i - 1) * 8);
    }
    return (int)l;
  }

  public static String intToHexString(int i, int digits) {
    String s = Integer.toHexString(i);
    while(s.length() < digits) s = "0" + s;
    return s;
  }

  private static byte[] addParity(byte[] in) {
    byte[] result = new byte[8];
    int resultIx = 1;
    int bitCount = 0;

    for(int i = 0; i < 56; i++) {
      boolean bit = (in[6 - i / 8] & (1 << (i % 8))) > 0;
      if(bit) {
        result[7 - resultIx / 8] |= (1 << (resultIx % 8)) & 0xFF;
        bitCount++;
      }
      if((i + 1) % 7 == 0) {
        if(bitCount % 2 == 0) {
          result[7 - resultIx / 8] |= 1;
        }
        resultIx++;
        bitCount = 0;
      }
      resultIx++;
    }
    return result;
  }


  public static byte[] desDecrypt(byte[] buffer, int len, byte[] desKey16) {

    len -= 8;
    byte[] decrypted = new byte[len];
    byte[] key1 = new byte[8], key2 = new byte[8];
    int[] ks1 = new int[DES_KEYSCHED_SZ], ks2 = new int[DES_KEYSCHED_SZ];
    System.arraycopy(desKey16, 0, key1, 0, 8);
    System.arraycopy(desKey16, 8, key2, 0, 8);

    DESAlgorithm des = new DESAlgorithm(false);
    des.des_set_key(key1, ks1);
    des.des_set_key(key2, ks2);

    byte[] encrypted8Bytes = new byte[8];
    byte[] decrypted8Bytes = new byte[8];
    byte[] ivec = new byte[8], nextIvec = new byte[8];

    System.arraycopy(buffer, len, nextIvec, 0, 8);

    for(int i = 0; i < len; i += 8) {

      System.arraycopy(nextIvec, 0, ivec, 0, 8);
      System.arraycopy(buffer, i, nextIvec, 0, 8);

      System.arraycopy(buffer, i, encrypted8Bytes, 0, 8);
      des.des_ecb_encrypt(encrypted8Bytes, decrypted8Bytes, ks1, false);

      System.arraycopy(decrypted8Bytes, 0, encrypted8Bytes, 0, 8);
      des.des_ecb_encrypt(encrypted8Bytes, decrypted8Bytes, ks2, true);

      System.arraycopy(decrypted8Bytes, 0, encrypted8Bytes, 0, 8);
      des.des_cbc_decrypt(encrypted8Bytes, decrypted8Bytes, ivec, ks1);

      System.arraycopy(decrypted8Bytes, 0, decrypted, i, 8);
    }

    byte checksum = 0;
    for(int i = 0; i < len; i++) checksum ^= decrypted[i];

    if(checksum != 0) return null;
    else return decrypted;
  }

  public static byte[] desEncrypt(byte[] buffer, int len, byte[] desKey16, int maxSize) {

    int noPadBytes = 8 - (len % 8);
    if(noPadBytes == 0) noPadBytes = 8;
    noPadBytes--; // make room for checksum

    byte[] padBytes = getRandomBytes(noPadBytes);

    if(len + noPadBytes + 1 >= maxSize - 8) return null; // too big

    byte[] tmp = new byte[len + noPadBytes + 1 + 8];
    byte[] encrypted = new byte[tmp.length];
    System.arraycopy(buffer, 0, tmp, 0, len);
    for(int i = 0; i < noPadBytes; i++) tmp[len++] = padBytes[i];

    byte checksum = 0;
    for(int i = 0; i < len; i++)	checksum ^= tmp[i];
    tmp[len++] = checksum;

    byte[] key1 = new byte[8], key2 = new byte[8];
    int[] ks1 = new int[DES_KEYSCHED_SZ], ks2 = new int[DES_KEYSCHED_SZ];
    System.arraycopy(desKey16, 0, key1, 0, 8);
    System.arraycopy(desKey16, 8, key2, 0, 8);

    DESAlgorithm des = new DESAlgorithm(false);
    des.des_set_key(key1, ks1);
    des.des_set_key(key2, ks2);

    byte[] decrypted8Bytes = new byte[8];
    byte[] encrypted8Bytes = new byte[8];
    byte[] ivec = getRandomBytes(8);

    System.arraycopy(ivec, 0, tmp, len, 8);

    for(int i = 0; i < len; i += 8) {
      System.arraycopy(tmp, i, decrypted8Bytes, 0, 8);
      des.des_cbc_encrypt(decrypted8Bytes, encrypted8Bytes, ivec, ks1);

      System.arraycopy(encrypted8Bytes, 0, decrypted8Bytes, 0, 8);
      des.des_ecb_encrypt(decrypted8Bytes, encrypted8Bytes, ks2, false);

      System.arraycopy(encrypted8Bytes, 0, decrypted8Bytes, 0, 8);
      des.des_ecb_encrypt(decrypted8Bytes, encrypted8Bytes, ks1, true);

      System.arraycopy(encrypted8Bytes, 0, ivec, 0, 8);
      System.arraycopy(encrypted8Bytes, 0, encrypted, i, 8);
    }
    System.arraycopy(tmp, len, encrypted, len, 8);

    return encrypted;
  }


}
