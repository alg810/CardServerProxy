package com.bowman.cardserv.rmi;

import java.io.Serializable;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 24, 2005
 * Time: 2:07:33 PM
 */
public class RemoteEvent extends AbstractStatus implements Serializable {

  private static final long serialVersionUID = -8437139570142317227L;

  public static final int USER_LOGINFAIL = -2, USER_LOGOUT = -1;
  public static final int USER_LOGIN = 0, USER_STATUS_CHANGED = 1;
  public static final int CWS_CONNECTED = 2, CWS_DISCONNECTED = 3;
  public static final int CWS_CONNECTION_FAILED = 4, CWS_WARNING = 5;
  public static final int CWS_LOST_SERVICE = 6, ECM_TRANSACTION = 7;
  public static final int CWS_INVALID_CARD = 8;
  public static final int PROXY_STARTUP = 10, LOG_EVENT = 11;

  private final long timeStamp;
  private final int type;
  private final String message, label, profile;

  public RemoteEvent(int type, String label, String message, String profile) {
    this.timeStamp = System.currentTimeMillis();
    this.type = type;
    this.message = message;
    this.label = label;
    this.profile = profile;
  }

  public RemoteEvent(RemoteEvent event) {
    this.timeStamp = event.timeStamp;
    this.type = event.type;
    this.message = event.message;
    this.label = event.label;
    this.profile = event.profile;
    this.data = event.data;
  }

  public int getType() {
    return type;
  }

  public String getLabel() {
    return label;
  }

  public String getMessage() {
    return message;
  }

  public String getProfile() {
    return profile;
  }

  public long getTimeStamp() {
    return timeStamp;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RemoteEvent event = (RemoteEvent) o;

    if (type != event.type) return false;
    if (label != null ? !label.equals(event.label) : event.label != null) return false;
    if (message != null ? !message.equals(event.message) : event.message != null) return false;
    if (profile != null ? !profile.equals(event.profile) : event.profile != null) return false;
    if("true".equalsIgnoreCase(data.getProperty("warning")))
      if(!getProperty("flags").equals(event.getProperty("flags"))) return false;
    return true;
  }

  public int hashCode() {
    int result;
    result = type;
    result = 31 * result + (message != null ? message.hashCode() : 0);
    result = 31 * result + (label != null ? label.hashCode() : 0);
    result = 31 * result + (profile != null ? profile.hashCode() : 0);
    if("true".equalsIgnoreCase(getProperty("warning"))) result = 31 * result + getProperty("flags").hashCode();
    return result;
  }
}
