package com.bowman.cardserv.web;

import com.bowman.cardserv.interfaces.CommandManager;

import java.util.*;
import java.lang.reflect.Method;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Jul 6, 2008
 * Time: 1:43:24 AM
 */
public abstract class Command {

  protected static CommandManager manager;
  protected static List commands = new ArrayList(); // commands registered before the manager
  public static void setManager(CommandManager commandManager) {
    manager = commandManager;
    for(Iterator iter = commands.iterator(); iter.hasNext(); ) {
      manager.registerCommand((Command)iter.next());
      iter.remove();
    }
  }

  protected static String methodifyName(String xmlName, String prefix) {
    String[] parts = xmlName.split("-");
    StringBuffer sb = new StringBuffer(prefix);
    for(int i = 0; i < parts.length; i++) {
      parts[i] = parts[i].toLowerCase();
      sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
    }
    return sb.toString();
  }

  String name, label, groupLabel;
  String description;
  Object handler;
  Command override;

  protected Method method;

  Map params = new LinkedHashMap();

  public Command(String name, String label, String description) {
    this.name = name;
    this.label = label;
    this.description = description;
  }

  public void unregister() {
    if(manager != null) manager.unregisterCommand(this);
    else commands.remove(this);
  }


  public void register(Object handler) throws NoSuchMethodException {
    register(handler, null, false);
  }

  public void register(Object handler, boolean override) throws NoSuchMethodException {
    register(handler, null, override);
  }

  public void register(Object handler, String label) throws NoSuchMethodException {
    register(handler, label, false);
  }

  public abstract void register(Object handler, String label, boolean override) throws NoSuchMethodException;

  public CommandParam addParam(String name, String label) {
    CommandParam cp = new CommandParam(name, label);
    params.put(name, cp);
    return cp;
  }

  public CommandParam getParam(String name) {
    return (CommandParam)params.get(name);
  }

  public void setOverride(Command override) {
    if(override != null) override.params = this.params;
    this.override = override;
  }

  protected String getHandlerName() {
    String name = handler.getClass().getName();
    if(name.indexOf('.') != -1) name = name.substring(name.lastIndexOf('.') + 1);
    return name;
  }

  public static class CommandParam {
    String name, label, value;
    Collection options;
    boolean allowArbitrary = true;
    int size = -1;
    
    boolean optional = false;
    boolean adminOnly = false;

    CommandParam(String name, String label) {
      this.name = name;
      this.label = label;
    }

    public CommandParam setOptions(Collection options, boolean allowArbitrary) {
      this.options = options;
      this.allowArbitrary = allowArbitrary;
      return this;
    }

    public CommandParam setOptions(String id, boolean allowArbitrary) {
      if(!id.startsWith("@")) id = "@" + id;
      this.options = Arrays.asList(new String[] {id});
      this.allowArbitrary = allowArbitrary;
      return this;
    }

    public String[] getOptions() {
      if(options == null) return new String[0];
      String[] opts = new String[options.size()];
      Iterator iter = options.iterator();
      for(int i = 0; i < opts.length; i++) opts[i] = iter.next().toString();
      return opts;
    }

    public void setSize(int size) {
      this.size = size;
    }

    public void setOptional(boolean optional) {
      this.optional = optional;
    }

    public void setAdminOnly(boolean adminOnly) {
      this.adminOnly = adminOnly;
    }

    public void setValue(String value) {
      this.value = value;
    }
  }
  
}
