package com.bowman.cardserv.interfaces;

import com.bowman.cardserv.web.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Feb 14, 2008
 * Time: 5:37:45 AM
 */
public interface CommandManager {

  void registerCommand(Command command);
  void registerCommand(Command command, boolean override);
  void unregisterCommand(Command command);

}
