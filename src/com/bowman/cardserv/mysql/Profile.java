package com.bowman.cardserv.mysql;

/**
 * A simple profile object
 * 
 * @author Pascal Nijhof
 * @since 02.01.2011
 */
public class Profile {

	private int id;
	private String name;
	
	public Profile(String name) {
		this(-1, name);
	}
	
	public Profile(int id, String name) {
		this.id = id;
		this.name = name;
	}
	
	/**
	 * @return the id
	 */
	public int getId() {
		return id;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

}
