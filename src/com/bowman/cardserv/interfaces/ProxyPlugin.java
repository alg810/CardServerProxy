package com.bowman.cardserv.interfaces;

import com.bowman.cardserv.*;

import java.util.Properties;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 8, 2005
 * Time: 4:11:37 PM
 */
public interface ProxyPlugin extends XmlConfigurable {

  void start(CardServProxy proxy);
  void stop();

  String getName();
  String getDescription();
  Properties getProperties();

  CamdNetMessage doFilter(ProxySession session, CamdNetMessage msg);

  byte[] getResource(String path, boolean admin);
  byte[] getResource(String path, byte[] inData, boolean admin);

}
