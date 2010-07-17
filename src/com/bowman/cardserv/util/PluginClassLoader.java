package com.bowman.cardserv.util;

import com.bowman.cardserv.web.FileFetcher;

import java.io.*;
import java.util.jar.*;
import java.util.Arrays;
import java.net.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Jul 9, 2008
 * Time: 2:45:24 PM
 */
public class PluginClassLoader extends ClassLoader {

  private ClassLoader parent = null;
  private JarFile jar;

  public PluginClassLoader(File jar, ClassLoader parent) throws IOException {
    if(jar == null || !jar.exists() || !jar.isFile())
      throw new IllegalArgumentException("Invalid jar file specified: " + jar.getAbsolutePath());
    this.parent = parent;
    this.jar = new JarFile(jar);
  }

  protected Class findClass(String name) throws ClassNotFoundException {
    Class c = null;
    String fileName = name.replace('.', '/') + ".class";
    JarEntry classEntry;

    classEntry = jar.getJarEntry(fileName);
    if(classEntry != null) {
      try {
        InputStream is = jar.getInputStream(classEntry);
        byte[] classData = loadClassData(is, classEntry.getSize());
        is.close();
        c = defineClass(name, classData, 0, classData.length);
      } catch(IOException e) {
        e.printStackTrace();
        throw new ClassNotFoundException(name);
      }
    }

    if(c == null) throw new ClassNotFoundException(name);
    else return c;
  }

  private byte[] loadClassData(InputStream classData, long size) throws IOException {
    DataInputStream dis = new DataInputStream(classData);
    byte[] data = new byte[(int)size];
    dis.readFully(data);
    dis.close();
    return data;
  }

  protected synchronized Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
    Class c = null;
    try {
      c = findClass(name);
    } catch (ClassNotFoundException e) {
      if(parent != null) c = parent.loadClass(name);
    }
    if(resolve) resolveClass(c);
    return c;
  }

  public InputStream getResourceAsStream(String name) {
    JarEntry res = jar.getJarEntry(name);
    if(res != null) try {
      return jar.getInputStream(res);
    } catch(IOException e) {
      return parent.getResourceAsStream(name);
    } else return parent.getResourceAsStream(name);
  }

  public void flush() {
    if(jar != null) try {
      jar.close();
    } catch(IOException e) {
    }
    parent = null;
  }

  protected void finalize() throws Throwable {
    // System.out.println("finalize() " + this);
    super.finalize();
  }

  public void resolveDependencies(String[] urlStrings, ProxyLogger logger) throws IOException {
    URL[] urls = new URL[urlStrings.length];
    File jar;
    for(int i = 0; i < urlStrings.length; i++) {
      urls[i] = new URL(urlStrings[i]);
      jar = new File(urls[i].getFile());
      jar = new File("lib", jar.getName());
      if(!jar.exists()) {
        if(logger != null) logger.info("Fetching dependency jar: " + urlStrings[i]);
        FileFetcher.fetchBinary(urls[i], jar);
        if(jar.exists()) {
          if(logger != null) logger.info("Successfully fetched: " + jar.getName());
        } else throw new IOException("Unknown error fetching jar: " + urlStrings[i]);        
      }
      urls[i] = jar.toURI().toURL();
    }
    if(logger != null) logger.fine("Dependencies: " + Arrays.asList(urls));
    parent = new URLClassLoader(urls, parent); // :)
  }

}
