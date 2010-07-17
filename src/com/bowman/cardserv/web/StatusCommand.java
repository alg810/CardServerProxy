package com.bowman.cardserv.web;

import com.bowman.cardserv.util.*;
import com.bowman.cardserv.ConfigException;

import java.util.*;
import java.rmi.RemoteException;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Jul 6, 2008
 * Time: 2:02:58 AM
 */
public class StatusCommand extends Command {

  public static StatusCommand createFromXml(ProxyXmlConfig def) throws ConfigException {
    boolean adminOnly = "true".equalsIgnoreCase(def.getStringValue("admin-only", "false"));
    StatusCommand cmd = new StatusCommand(def.getStringValue("name"), def.getStringValue("label"), def.getStringValue("description"), adminOnly);
    Iterator iter = def.getMultipleSubConfigs("command-param");
    if(iter != null) {
      CommandParam prm;
      while(iter.hasNext()) {
        def = (ProxyXmlConfig)iter.next();
        prm = cmd.addParam(def.getStringValue("name"), def.getStringValue("label"));
        prm.adminOnly = "true".equals(def.getStringValue("admin-only", "false"));
        prm.optional = "true".equals(def.getStringValue("optional", "true"));

        Iterator opts = def.getMultipleSubConfigs("option");
        if(opts != null) {
          ProxyXmlConfig opt; List options = new ArrayList();
          while(opts.hasNext()) {
            opt = (ProxyXmlConfig)opts.next();
            try {
              options.add(opt.getStringValue("value"));
            } catch (ConfigException e) {}
            try {
              options.add(opt.getStringValue("source"));
            } catch (ConfigException e) {}
          }
          prm.setOptions(options, true);
        }

      }
    }

    return cmd;
  }

  boolean adminOnly;

  public StatusCommand(String name, String label, String description, boolean adminOnly) {
    super(name, label, description);
    this.adminOnly = adminOnly;
  }

  public void register(Object handler, String label, boolean override) throws NoSuchMethodException {
    this.handler = handler;
    this.groupLabel = label == null?getHandlerName():label;

    String methodName = methodifyName(name, "runStatusCmd");
    try {
      this.method = handler.getClass().getMethod(methodName, new Class[] {XmlStringBuffer.class});
    } catch (NoSuchMethodException e) {
      try {
        this.method = handler.getClass().getMethod(methodName, new Class[] {XmlStringBuffer.class, Map.class});
      } catch (NoSuchMethodException ex) {
        this.method = handler.getClass().getMethod(methodName, new Class[] {XmlStringBuffer.class, Map.class, String.class});
      }
    }
    if(manager == null) commands.add(this);
    else manager.registerCommand(this, override);
  }

  public void invoke(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    if(override != null) ((StatusCommand)override).invoke(xb, params, user);
    else {
      try {
        if(method.getParameterTypes().length == 1) method.invoke(handler, new Object[] {xb});
        else if(method.getParameterTypes().length == 2) method.invoke(handler, new Object[] {xb, params});
        else method.invoke(handler, new Object[] {xb, params, user});
      } catch (InvocationTargetException e) {
        if(e.getCause() instanceof RemoteException) throw (RemoteException)e.getCause();
        else throw new RemoteException("Uncaught exception in runStatusCmdMethod", e.getCause()); // re-wrap
      } catch (IllegalAccessException e) {
        throw new RemoteException("No access to runStatusCmdMethod (" + method + ")", e);
      }
    }
  }

}
