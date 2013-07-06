package com.bowman.cardserv;

import org.apache.sshd.common.Factory;
import org.apache.sshd.server.*;

import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: johan
 * Date: Feb 28, 2010
 * Time: 5:12:33 PM
 */
public class DummyShellFactory implements Factory<Command> {

  public Command create() {
    return new DummyShell();
  }

  public static class DummyShell implements Command {

    public void setInputStream(InputStream inputStream) {
      System.out.println("setInputStream(" + inputStream + ")");
    }

    public void setOutputStream(OutputStream outputStream) {
      System.out.println("setOutputStream(" + outputStream + ")");
    }

    public void setErrorStream(OutputStream outputStream) {
      System.out.println("setErrorStream(" + outputStream + ")");
    }

    public void setExitCallback(ExitCallback exitCallback) {
      System.out.println("setExitCallback(" + exitCallback + ")");
    }

    public void start(Environment environment) throws IOException {
      System.out.println("start(" + environment + ")");
    }

    public void destroy() {
      System.out.println("destroy()");
    }
  }
}
