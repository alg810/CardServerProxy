package com.bowman.cardserv.interfaces;

import com.bowman.cardserv.CamdNetMessage;

/**
 * Created by IntelliJ IDEA.
 * User: johan
 * Date: May 1, 2010
 * Time: 11:56:32 PM
 */
public interface CacheListener {

  void onRequest(CamdNetMessage req);
  void onReply(CamdNetMessage req, CamdNetMessage reply);

}
