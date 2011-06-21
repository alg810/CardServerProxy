package com.bowman.cardserv;

import java.net.Socket;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Feb 14, 2010
 * Time: 2:06:00 AM
 */
public class BoxOperation {

  private static int counter = 0;

  private String cmdLine, scriptName, params, outFile;
  private int id;
  private long startTimeStamp = -1, stopTimeStamp = -1;
  private StringBuffer output;
  private Socket conn;

  public BoxOperation(String s, String params, String outFile) {
    if(s.startsWith("cmd:")) cmdLine = s.substring(4);
    else if(s.startsWith("script:")) scriptName = s.substring(7);
    else throw new IllegalArgumentException(s);
    this.params = params;
    this.outFile = outFile;
    id = counter++;
  }

  public String toString() {
    if(scriptName != null) return scriptName + (outFile==null?"":" > f");
    else if(cmdLine != null) return cmdLine.length() > 10 ? cmdLine.substring(0, 10) + "..." : cmdLine;
    else return "(invalid)";
  }

  public String getCmdLine() {
    return cmdLine;
  }

  public String getScriptName() {
    return scriptName;
  }

  public String getParams() {
    if(params == null) return "";
    else return params;
  }

  public String getOutFile() {
    return outFile;
  }

  public boolean isScript() {
    return scriptName != null;
  }

  public int getId() {
    return id;
  }

  public void start(Socket conn) {
    if(startTimeStamp != -1) throw new IllegalStateException("start() on already started op: " + id);
    startTimeStamp = System.currentTimeMillis();
    output = new StringBuffer();
    this.conn = conn;
  }

  public void end() {
    stopTimeStamp = System.currentTimeMillis();
    conn = null;
  }

  public void abort() {
    if(conn != null) try {
      conn.close();
    } catch(IOException e) {}
  }

  public boolean isRunning() {
    return startTimeStamp != -1 && stopTimeStamp == -1;
  }
  
  public boolean isStarted() {
    return startTimeStamp != -1;
  }

  public long getStartTime() {
    return startTimeStamp;
  }

  public long getStopTime() {
    return stopTimeStamp;
  }

  public void appendOutput(String line) {
    output.append(line).append("\n");
  }

  public String getOutput() {
    if(output == null) return null;
    else return output.toString();
  }

  public boolean equals(Object o) {
    if(this == o) return true;
    if(o == null || getClass() != o.getClass()) return false;
    BoxOperation that = (BoxOperation)o;
    if(id != that.id) return false;
    return true;
  }

  public int hashCode() {
    return id;
  }
}
