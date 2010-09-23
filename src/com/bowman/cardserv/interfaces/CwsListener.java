package com.bowman.cardserv.interfaces;

import com.bowman.cardserv.tv.TvService;
import com.bowman.cardserv.CaProfile;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 25, 2005
 * Time: 10:04:21 AM
 */
public interface CwsListener {

  void cwsConnected(CwsConnector cws);
  void cwsDisconnected(CwsConnector cws);
  void cwsConnectionFailed(CwsConnector cws, String message);
  void cwsEcmTimeout(CwsConnector cws, String message, int failureCount);
  void cwsLostService(CwsConnector cws, TvService service, boolean show);
  void cwsFoundService(CwsConnector cws, TvService service, boolean show);
  void cwsInvalidCard(CwsConnector cws, String message);
  void cwsProfileChanged(CaProfile profile, boolean added);

}
