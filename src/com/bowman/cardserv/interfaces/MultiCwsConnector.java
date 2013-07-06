package com.bowman.cardserv.interfaces;

/**
 * Created by IntelliJ IDEA.
 * User: johan
 * Date: Jan 18, 2010
 * Time: 12:18:44 PM
 */
public interface MultiCwsConnector {

  boolean hasMatchingProfile(int networkId, int caId);
  void clearRemoteState(boolean all);

}
