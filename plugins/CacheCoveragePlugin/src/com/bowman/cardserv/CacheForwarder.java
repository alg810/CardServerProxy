package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.XmlConfigurable;

import java.util.Properties;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2012-02-11
 * Time: 11:35
 */
public interface CacheForwarder extends XmlConfigurable {
  String getName();

  boolean isConnected();

  Properties getProperties();

  void close();

  void forwardRequest(CamdNetMessage req);
  void forwardReply(CamdNetMessage req, CamdNetMessage reply);
}
