package com.bowman.cardserv.util;

import java.util.logging.*;
import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Feb 10, 2010
 * Time: 10:59:40 PM
 */
public class WrappedFileHandler extends FileHandler {
  static FileHandler wrappedHandler;

  public WrappedFileHandler() throws IOException, SecurityException {
    if(wrappedHandler == null) {
      wrappedHandler = new FileHandler(new File("log", "external.log").getAbsolutePath(), true);
      wrappedHandler.setFormatter(new CustomFormatter());
    }
  }

  public void publish(LogRecord record) {
    wrappedHandler.publish(record);
  }

}
