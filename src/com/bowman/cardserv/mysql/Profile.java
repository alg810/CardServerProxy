package com.bowman.cardserv.mysql;

/**
 * A simple profile object
 *
 * @author DonCarlo
 * @since 02.01.2011
 */
public class Profile {

  private int id;
  private String profileName;

  public Profile(String name) {
    this(-1, name);
  }

  public Profile(int id, String profileName) {
    this.id = id;
    this.profileName = profileName;
  }

  public int getId() {
    return id;
  }

  public String getProfileName() {
    return profileName;
  }
}
