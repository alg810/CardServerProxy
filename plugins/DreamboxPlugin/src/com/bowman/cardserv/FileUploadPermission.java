package com.bowman.cardserv;

import java.util.*;

/**
* Created by IntelliJ IDEA.
* User: bowman
* Date: 2011-06-06
* Time: 21:11
*/
public class FileUploadPermission {

  private String user;
  private Map namePathMap = new HashMap();

  public FileUploadPermission(String user, String fileName, String targetPath) {
    this.user = user;
    add(fileName, targetPath);
  }

  public void add(String fileName, String targetPath) {
    namePathMap.put(fileName, targetPath);
  }

  public boolean contains(String fileName) {
    return namePathMap.containsKey(fileName);
  }

  public String getPath(String fileName) {
    return (String)namePathMap.get(fileName);
  }

  public String getUser() {
    return user;
  }
}
