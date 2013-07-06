package com.bowman.cardserv.util;

import com.bowman.cardserv.web.*;

import java.util.*;
import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Jun 18, 2008
 * Time: 5:48:16 AM
 */
public class ProxyXmlTracer {

  private Map nodes = new TreeMap();

  public ProxyXmlTracer() {
    try {
      new CtrlCommand("dump", "Dump trace", "Write gathered trace data to file.").register(this);
    } catch(NoSuchMethodException e) {
      e.printStackTrace();
    }
  }

  public void trace(String node, String name, Object def, String typeInfo, StackTraceElement caller) {
    String sourceClass = caller == null?"<unknown>":stripClassName(caller.getClassName());
    String sourceMethod = caller == null?"<unknown>":caller.getMethodName();
    sourceMethod += "(" + caller.getFileName() + ":" + caller.getLineNumber() + ")";

    Set values = new TreeSet();
    if(nodes.containsKey(node)) values = (Set)nodes.get(node);
    else nodes.put(node, values);
    if(name != null) {
      StringBuffer value = new StringBuffer(name);
      value.append("\n\t\ttype: ").append(typeInfo);
      if(def != null) value.append("\n\t\tdefault: '").append(def).append("'");
      value.append("\n\t\tcaller: ").append(sourceClass).append('.').append(sourceMethod);
      values.add(value.toString());
    }    
  }

  private static String stripClassName(String name) {
    if(name == null) return null;
    int i = name.lastIndexOf('.');
    if(i == -1) return name;
    else return name.substring(i + 1);
  }


  public void dump() throws IOException {
    PrintWriter pw = new PrintWriter(new FileWriter("etc/xmlcfg.txt"));
    String node;
    for(Iterator iter = nodes.keySet().iterator(); iter.hasNext();) {
      node = (String)iter.next();
      pw.println("\n" + node);
      for(Iterator i = ((Set)nodes.get(node)).iterator(); i.hasNext();) {
        pw.println("\t" + i.next());
      }
    }
    pw.close();
  }

  public CtrlCommandResult runCtrlCmdDump() {
    try {
      dump();
      return new CtrlCommandResult(true, "Dump written to file (etc/xmlcfg.txt)");
    } catch(IOException e) {
      e.printStackTrace();
      return new CtrlCommandResult(false, "Dump failed: " + e);
    }
  }


}
