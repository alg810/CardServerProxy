package com.bowman.cardserv.interfaces;

import com.bowman.cardserv.tv.TvService;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 16, 2005
 * Time: 1:39:52 PM
 */
public interface UserStatusListener {

  void userStatusChanged(String userName, TvService service, String profile, String sessionId);
  void userLogin(String userName, String profile, String ip, String sessionId);
  void userLogout(String userName, String profile, String sessionId);
  void userLoginFailed(String userName, String profile, String ip, String reason);

}
