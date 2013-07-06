package com.bowman.cardserv.cws;

import com.bowman.cardserv.interfaces.CwsConnector;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Jan 31, 2010
 * Time: 2:10:22 PM
 */
public class ConnectorSelection {

  public static final ConnectorSelection EMPTY = new ConnectorSelection(null, null, null);

  private CwsConnector primary;
  private List unknown, secondary;

  public ConnectorSelection(CwsConnector primary, List secondary, List unknown) {
    this.primary = primary;
    this.unknown = unknown;
    this.secondary = secondary;

    if(unknown != null && unknown.isEmpty()) this.unknown = null;
    if(secondary != null && secondary.isEmpty()) this.secondary = null;
  }

  public boolean isEmpty() {
    return primary == null && secondary == null && unknown == null;
  }

  public CwsConnector getPrimary() {
    return primary;
  }

  public List getUnknown() {
    return unknown;
  }

  public List getSecondary() {
    return secondary;
  }
}
