package com.bowman.cardserv.rmi;

import com.bowman.cardserv.interfaces.ProxyPlugin;

import java.io.Serializable;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Mar 16, 2010
 * Time: 2:45:40 PM
 */
public class PluginStatus extends AbstractStatus implements Serializable {

  private String name, description, className;

  public PluginStatus(ProxyPlugin plugin) {
    name = plugin.getName();
    description = plugin.getDescription();
    className = plugin.getClass().getName();
    try {
      if(plugin.getProperties() != null) data = plugin.getProperties();
    } catch (AbstractMethodError e) {
      // ignore, support older plugins
    } catch (Throwable t) {
      t.printStackTrace();      
    }
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getClassName() {
    return className;
  }
}
