package com.bowman.cardserv.crypto;

import java.security.*;
import java.util.Arrays;

public class MD5Crypt {

  private static  final String B64 = "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

  private static String toBase64(long v, int size) {
    StringBuffer result = new StringBuffer();

    while(--size >= 0) {
      result.append(B64.charAt((int)(v & 0x3f)));
      v >>>= 6;
    }

    return result.toString();
  }

  private static int unsignExtend(byte b) {
    return (int)b & 0xff;
  }

  public static String crypt(String password, String salt) {
    String magic = "$1$";

    if(salt.startsWith(magic)) salt = salt.substring(magic.length());
    if(salt.indexOf('$') != -1) salt = salt.substring(0, salt.indexOf('$'));
    if(salt.length() > 8) salt = salt.substring(0, 8);

    try {
      MessageDigest md1 = MessageDigest.getInstance("MD5");
      md1.reset();
      md1.update(password.getBytes());
      md1.update(magic.getBytes());
      md1.update(salt.getBytes());

      MessageDigest md2 = MessageDigest.getInstance("MD5");
      md2.update(password.getBytes());
      md2.update(salt.getBytes());
      md2.update(password.getBytes());

      byte[] finalState = md2.digest();
      for(int pl = password.length(); pl > 0; pl -= 16) {
        md1.update(finalState, 0, pl > 16?16:pl);
      }
      Arrays.fill(finalState, (byte)0);

      for(int i = password.length(); i != 0; i >>>= 1) {
        if((i & 1) != 0) {
          md1.update(finalState, 0, 1);
        } else {
          md1.update(password.getBytes(), 0, 1);
        }
      }
      finalState = md1.digest();

      for(int i = 0; i < 1000; i++) {
        md2 = MessageDigest.getInstance("MD5");

        if((i & 1) != 0) {
          md2.update(password.getBytes());
        } else {
          md2.update(finalState, 0, 16);
        }
        if((i % 3) != 0) md2.update(salt.getBytes());
        if((i % 7) != 0) md2.update(password.getBytes());
        if((i & 1) != 0) {
          md2.update(finalState, 0, 16);
        } else {
          md2.update(password.getBytes());
        }

        finalState = md2.digest();
      }

      StringBuffer result = new StringBuffer();

      result.append(magic);
      result.append(salt);
      result.append("$");

      long l = (unsignExtend(finalState[0]) << 16) | (unsignExtend(finalState[6]) << 8) | unsignExtend(finalState[12]);
      result.append(toBase64(l, 4));
      l = (unsignExtend(finalState[1]) << 16) | (unsignExtend(finalState[7]) << 8) | unsignExtend(finalState[13]);
      result.append(toBase64(l, 4));
      l = (unsignExtend(finalState[2]) << 16) | (unsignExtend(finalState[8]) << 8) | unsignExtend(finalState[14]);
      result.append(toBase64(l, 4));
      l = (unsignExtend(finalState[3]) << 16) | (unsignExtend(finalState[9]) << 8) | unsignExtend(finalState[15]);
      result.append(toBase64(l, 4));
      l = (unsignExtend(finalState[4]) << 16) | (unsignExtend(finalState[10]) << 8) | unsignExtend(finalState[5]);
      result.append(toBase64(l, 4));
      l = unsignExtend(finalState[11]);
      result.append(toBase64(l, 2));

      Arrays.fill(finalState, (byte)0);

      return result.toString();

    } catch(NoSuchAlgorithmException e) {
      e.printStackTrace();
    }

    return null;
  }

}

