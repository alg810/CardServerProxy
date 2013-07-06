package com.bowman.cardserv.interfaces;

import com.bowman.cardserv.CamdNetMessage;

/**
 * Created by IntelliJ IDEA.
 * User: johan
 * Date: May 1, 2010
 * Time: 11:56:32 PM
 */
public interface CacheListener {

  boolean lockRequest(int successFactor, CamdNetMessage req);
  void onRequest(int successFactor, CamdNetMessage req);
  void onReply(CamdNetMessage req, CamdNetMessage reply);
  void onContested(CamdNetMessage req, CamdNetMessage reply);

}
