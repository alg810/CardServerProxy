package com.bowman.cardserv.interfaces;

import com.bowman.cardserv.util.ProxyXmlConfig;
import com.bowman.cardserv.ConfigException;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 8, 2005
 * Time: 4:09:34 PM
 */
public interface XmlConfigurable {

  void configUpdated(ProxyXmlConfig xml) throws ConfigException;

}
