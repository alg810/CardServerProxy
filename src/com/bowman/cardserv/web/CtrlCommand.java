package com.bowman.cardserv.web;

import com.bowman.cardserv.ConfigException;
import com.bowman.cardserv.util.ProxyXmlConfig;

import java.lang.reflect.*;
import java.rmi.RemoteException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Feb 9, 2008
 * Time: 5:39:39 PM
 */
public class CtrlCommand extends Command {

  public static CtrlCommand createFromXml(ProxyXmlConfig def) throws ConfigException {
    boolean confirm = "true".equalsIgnoreCase(def.getStringValue("confirm", "false"));
    CtrlCommand cmd = new CtrlCommand(def.getStringValue("name"), def.getStringValue("label"), def.getStringValue("description"), confirm);
    Iterator iter = def.getMultipleSubConfigs("command-param");
    if(iter != null) {
      CommandParam prm;
      while(iter.hasNext()) {
        def = (ProxyXmlConfig)iter.next();
        prm = cmd.addParam(def.getStringValue("name"), def.getStringValue("label"));

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
          prm.setOptions(options, "true".equalsIgnoreCase(def.getStringValue("allow-arbitrary", "false")));
        }
      }
    }

    return cmd;
  }

  boolean confirm;

  public CtrlCommand(String name, String label, String description, boolean confirm) {
    super(name, label, description);
    this.confirm = confirm;
  }

  public CtrlCommand(String name, String label, String description) {
    this(name, label, description, false);
  }
      
  public void register(Object handler, String label, boolean override) throws NoSuchMethodException {
    this.handler = handler;
    this.groupLabel = label == null?getHandlerName():label;

    String methodName = methodifyName(name, "runCtrlCmd");
    try {
      this.method = handler.getClass().getMethod(methodName, null); // try parameterless first
    } catch (NoSuchMethodException e) {
      try {
        this.method = handler.getClass().getMethod(methodName, new Class[] {Map.class});
      } catch (NoSuchMethodException ex) {
        this.method = handler.getClass().getMethod(methodName, new Class[] {Map.class, String.class});
      }      
    }

    if(CtrlCommandResult.class != method.getReturnType())
      throw new NoSuchMethodException("Method '" + methodName +
          "' has wrong return type (should return CtrlCommandResult)");

    if(manager == null) commands.add(this);
    else manager.registerCommand(this, override);
  }

  public CtrlCommandResult invoke(Map params, String user) throws RemoteException {
    if(override != null) return ((CtrlCommand)override).invoke(params, user);
    else {
      try {
        if(method.getParameterTypes().length == 0) return (CtrlCommandResult)method.invoke(handler, null);
        else if(method.getParameterTypes().length == 1) return (CtrlCommandResult)method.invoke(handler, new Object[] {params});
        else return (CtrlCommandResult)method.invoke(handler, new Object[] {params, user});
      } catch (InvocationTargetException e) {
        if(e.getCause() instanceof RemoteException) throw (RemoteException)e.getCause();
        else throw new RemoteException("Uncaught exception in runCtrlCmdMethod", e.getCause()); // re-wrap
      } catch (IllegalAccessException e) {
        throw new RemoteException("No access to runCtrlCmdMethod (" + method + ")", e);
      }
    }
  }

}
