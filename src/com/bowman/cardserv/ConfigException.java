package com.bowman.cardserv;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 8, 2005
 * Time: 4:31:14 PM
 */
public class ConfigException extends Exception {

  private String label, subLabel;

  public ConfigException(String msg) {
    super(msg);
  }

  public ConfigException(String msg, Throwable nestedException) {
    super(msg, nestedException);
  }

  public ConfigException(String label, String msg) {
    super(msg);
    this.label = label;
  }

  public ConfigException(String label, String subLabel, String msg) {
    super(msg);
    this.label = label;
    this.subLabel = subLabel;
  }

  public ConfigException(String label, String msg, Throwable nestedException) {
    super(msg, nestedException);
    this.label = label;
  }

  public ConfigException(String label, String subLabel, String msg, Throwable nestedException) {
    super(msg, nestedException);
    this.label = label;
    this.subLabel = subLabel;
  }

  public String getLabel() {
    return label;
  }

  public String getSubLabel() {
    return subLabel;
  }
}
