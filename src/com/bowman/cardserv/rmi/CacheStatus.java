package com.bowman.cardserv.rmi;

import java.util.Properties;
import java.io.Serializable;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2007-feb-04
 * Time: 02:37:17
 */
public class CacheStatus extends AbstractStatus implements Serializable {

  private final String type;

  public CacheStatus(String type, Properties usageStats) {
    this.type = type;
    this.data = usageStats;
  }

  public String getType() {
    return type;
  }

  public Properties getUsageStats() {
    return data;
  }

}
