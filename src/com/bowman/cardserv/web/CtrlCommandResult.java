package com.bowman.cardserv.web;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Feb 9, 2008
 * Time: 8:31:11 PM
 */
public class CtrlCommandResult {

  boolean success;
  String message;
  Object data;

  public CtrlCommandResult(boolean success, String message) {
    this.message = message;
    this.success = success;
  }

  public CtrlCommandResult(boolean success, String message, Object data) {
    this.success = success;
    this.message = message;
    this.data = data;
  }
  
}
