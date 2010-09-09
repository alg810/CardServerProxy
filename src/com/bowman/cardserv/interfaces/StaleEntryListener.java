package com.bowman.cardserv.interfaces;

import com.bowman.cardserv.CamdNetMessage;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Sep 9, 2010
 * Time: 3:23:22 PM
 */
public interface StaleEntryListener {

  void onRemoveStale(CamdNetMessage msg);

}
