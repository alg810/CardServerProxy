package com.bowman.cardserv.interfaces;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2010-08-04
 * Time: 18:02
 */
public interface GHttpConstants {

  public static final int MAX_WAIT = 2400;

  public static final int T_INSTANCE_ID = 0, T_ECM_REQ = 1, T_CACHE_MISS = 2, T_STAT_UPDATE = 3;

  public static final String[] STAT_KEYS = {"isize", "msize", "ich", "mch", "iow", "me", "ct", "et", "da", "ch"};
  public static final int S_ISIZE = 0, S_MSIZE = 1, S_ICH = 2, S_MCH = 3, S_MOW = 4, S_ME = 5;
  public static final int S_CT = 6, S_ET = 7, S_DA = 8, S_CH = 9;

}
