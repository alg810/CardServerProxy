package com.bowman.cardserv.interfaces;

import com.bowman.cardserv.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 9, 2005
 * Time: 5:00:41 AM
 */
public interface CamdMessageListener extends CamdConstants {

  void messageReceived(ProxySession session, CamdNetMessage msg);
  void messageSent(ProxySession session, CamdNetMessage msg);

}
