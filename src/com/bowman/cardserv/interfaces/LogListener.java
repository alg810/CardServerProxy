package com.bowman.cardserv.interfaces;

import java.util.logging.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Aug 6, 2010
 * Time: 9:57:14 PM
 */
public interface LogListener {

  void onLog(Level l, String label, String message);

}
