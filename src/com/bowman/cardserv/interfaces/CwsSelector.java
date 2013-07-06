package com.bowman.cardserv.interfaces;

import com.bowman.cardserv.CamdNetMessage;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Aug 22, 2009
 * Time: 2:01:38 PM
 */
public interface CwsSelector {

  Set doSelection(ProxySession session, CamdNetMessage msg, Set connectors);

}
