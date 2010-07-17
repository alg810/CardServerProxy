package com.bowman.cardserv.rmi;

import java.io.Serializable;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Jul 8, 2008
 * Time: 7:03:16 PM
 */
public class AbstractStatus implements Serializable {

  Properties data = new Properties();

  public void setProperty(String name, String value) {
    data.setProperty(name, value);
  }

  public void setProperties(Properties p) {
    data.putAll(p);
  }

  public String getProperty(String name) {
    return data.getProperty(name);
  }

  public Iterator getPropertyNames() {
    return data.keySet().iterator();
  }  

}
