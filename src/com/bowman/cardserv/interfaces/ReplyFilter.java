package com.bowman.cardserv.interfaces;

import com.bowman.cardserv.CamdNetMessage;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Dec 31, 2009
 * Time: 12:23:15 AM
 */
public interface ReplyFilter {

  CamdNetMessage doReplyFilter(CwsConnector connector, CamdNetMessage msg);

}
