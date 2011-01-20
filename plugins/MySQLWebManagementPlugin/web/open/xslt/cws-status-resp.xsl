<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="html"/>

  <xsl:template match="cws-status-resp">
    <div id="header">
      <div id="headerInfoLeft"><xsl:call-template name="jvm"/></div>
      <div id="headerInfo"><img id="busyImg" src="/images/bigrotation.gif" alt="loading" style="visibility: hidden;"/></div>
    </div>
    <div id="subheader">
      <a href="#" id="events">Events</a>&#160;
      <a href="#" id="channels">Channels</a>&#160;
      <a href="#" id="status">Status</a>&#160;
      <a href="#" id="sessions">Sessions</a>&#160;
      <a href="#" id="admin" style="visibility: hidden">Admin</a>&#160;
      <a href="#" id="config" style="visibility: hidden">Config</a>&#160;
      <a href="#" id="logout" style="visibility: visible; position: absolute; left: 745px;">Logout</a>
    </div>
    <div id="mainstart">&#160;</div>
    <div id="main">
      <div id="content">
        <xsl:apply-templates/>
      </div>
    </div>
    <div id="footer">&#160;</div>
  </xsl:template>

  <xsl:template match="mysql-database-details">
    <xsl:for-each select="//mysql-database-details">
      	MySQL Database <strong>host: </strong><xsl:value-of select="@host"/><br />
      	MySQL Database <strong>port: </strong><xsl:value-of select="@port"/><br />
      	MySQL Database <strong>name: </strong><xsl:value-of select="@name"/><br />
      	MySQL Database <strong>user: </strong><xsl:value-of select="@user"/><br />
      <br />
    </xsl:for-each>
  </xsl:template>

  <xsl:template match="mysql-users">    
    <strong>Add</strong> new database users: <a href="javascript:clickSection('adduser');">here</a><br />
    <strong>Edit</strong> database users: <a href="javascript:clickSection('edituser');">here</a><br />   
    <strong>Delete</strong> database users: <a href="javascript:clickSection('deleteuser');">here</a><br />
    <br />   
    <strong>Add/Delete</strong> database profiles: <a href="javascript:clickSection('profiles');">here</a><br />     
      <xsl:if test="count(user) > 0">
    	<br /><br />
        <fieldset>
          <legend><strong>User(s) stored in the MySQL database.</strong></legend>
          <div class="cwsheader">
            <table border="0" width="99%"><tbody>
              <tr>
                <td><strong>User</strong></td>
                <td><strong>Displayname</strong></td>
                <td><strong>Mail</strong></td>
                <td><strong>IP-Mask</strong></td>
                <td><strong>Profiles</strong></td>
                <td><strong>MC</strong></td>
                <td><strong>E</strong></td>
                <td><strong>A</strong></td>
                <td><strong>D</strong></td>
                <td><strong>ME</strong></td>
              </tr>
              <xsl:for-each select="user">
                <xsl:sort order="ascending" select="@username"/>
                <tr>
                  <xsl:if test="position() mod 2 = 1">
                    <xsl:attribute name="bgcolor">#ffffff</xsl:attribute>
                  </xsl:if>
	              <td><a target="_blank">
	                <xsl:attribute name="href">/xmlHandler?command=mysql-user-details&amp;username=<xsl:value-of select="@username"/></xsl:attribute>
	                <xsl:attribute name="title">Show account details for user</xsl:attribute>
	                <xsl:value-of select="@username"/>
	              </a></td>
                  <td><xsl:value-of select="@displayname"/></td>
                  <td><xsl:value-of select="@mail"/></td>
                  <td><xsl:value-of select="@ipmask"/></td>
                  <td><xsl:value-of select="@profiles"/></td>
                  <td><xsl:value-of select="@maxconnections"/></td>
                  <td><xsl:value-of select="@enabled"/></td>
                  <td><xsl:value-of select="@admin"/></td>
                  <td><xsl:value-of select="@debug"/></td>
                  <td><xsl:value-of select="@mapexcluded"/></td>
                </tr>
              </xsl:for-each>
            </tbody></table>
          </div>
          <dfn>MC = max connections / E = enabled / A = admin / D = debug / ME = map excluded</dfn>
        </fieldset>
      </xsl:if>
  </xsl:template>

  <xsl:template match="mysql-add-user">
    <strong>Return to "MySQL-Users": </strong><a href="javascript:clickSection('mysqlusers');">here</a><br /><br />
	<form action="" id="mysql-add-user">  	
      <fieldset>
	    <legend><strong>Add a new user to the MySQL Database.</strong></legend>
        <div class="cwsheader">
          <table border="0" width="99%" cellspacing="2" cellpadding="2"><tbody>
            <tr>
              <td width="15%"><strong>Username:</strong></td>
              <td width="85%"><input name="username" type="text" size="60" title="User name, avoid long names, spaces and special characters. There are no particular limitations as far as the proxy is concerned, but the camd clients may have them."/></td>
            </tr>
            <tr>
              <td><strong>Password:</strong></td>
              <td><input name="password" id="inputPassword" type="password" size="60" title="Avoid special characters."/></td>
            </tr>
            <tr>
              <td><strong>Retype Password:</strong></td>
              <td><input name="passwordretyped" id="inputPasswordRetyped" type="password" size="60" title="Avoid special characters."/></td>
            </tr>
            <tr>
              <td><strong>Displayname:</strong></td>
              <td><input name="displayname" type="text" size="60" title="An optional non-unique alias for the user (used by the http/xml api)."/></td>
            </tr>
            <tr>
              <td><strong>Profiles:</strong></td>
              <td>
                <table border="0" width="320" cellspacing="1" cellpadding="1"><tbody>
                <tr>
                  <td><input type="checkbox" id="chkbxProfiles" name="profiles" value="ALL"/>ALL</td>
                  <td>&#160;</td>
                </tr>
 				<xsl:if test="count(profile) > 0">
				  <xsl:for-each select="profile">
                    <xsl:if test="position() mod 2 = 1">
                      <tr />
                    </xsl:if>
 				    <td>
 				      <input type="checkbox" name="profiles">
				        <xsl:attribute name="value"><xsl:value-of select="@profilename"/></xsl:attribute>
				      </input><xsl:value-of select="@profilename"/>
                    </td>
				  </xsl:for-each>
				</xsl:if>
				</tbody></table>
              </td>
            </tr>
            <tr>
              <td><strong>E-Mail Address:</strong></td>
              <td><input name="mail" type="text" size="60" title="An optional parameter used in the MessagingPlugin."/></td>
            </tr>
            <tr>
              <td><strong>IP-Mask:</strong></td>
              <td><input name="ipmask" type="text" size="60" title="Only allow connections from a particular ip or ip range, for this user. This applies only to the newcamd protocol, not http/xml. Masks can use glob wildcards (? *), but this should typically not be used for users with dynamic ips - fixed only (no dns reverse lookups are performed, hostname masks will not be allowed)."/></td>
            </tr>
            <tr>
              <td><strong>Max Connections:</strong></td>
              <td>
    		    <select name="maxconnections" size="1" title="Number of connections to allow, if the user exceeds this then any older existing connections will be closed. NOTE: as of 0.9.0 this has changed to max-connections per profile, meaning and old values will likely need to be changed. Since it is no longer the total number of connections, the value should now reflect the number of clients/boxes the user is expected to connect with (regardless of how many profiles the user has access to).">
      			  <option>0</option>
      			  <option selected="selected">1</option>
      			  <option>2</option>
      			  <option>3</option>
      			  <option>4</option>
      			  <option>5</option>
      			  <option>6</option>
      			  <option>7</option>
      			  <option>8</option>
      			  <option>9</option>
    			</select>
              </td>
            </tr>
            <tr>
              <td><strong>Enabled:</strong></td>
              <td>
    		    <select name="enabled" size="1" title="true/false (default: true). Allows disabling of accounts without deleting them.">
      			  <option selected="selected">TRUE</option>
                  <option>FALSE</option>
    			</select>
              </td>
            </tr>
            <tr>
              <td><strong>Admin:</strong></td>
              <td>
                <select name="admin" size="1" title="true/false (default: false). Is this user an administrator? Affects access to http/xml api features only.">
      			  <option>TRUE</option>
      			  <option selected="selected">FALSE</option>
    	        </select>
              </td>
            </tr>
            <tr>
              <td><strong>Debug:</strong></td>
              <td>
                <select name="debug" size="1" title="true/false (default: false). Set to true to enable ecm/emm/zap logging for this user only (has no effect if these are already enabled globally).">
      			  <option>TRUE</option>
      			  <option selected="selected">FALSE</option>
                </select>
              </td>
            </tr>
            <tr>
              <td><strong>Map Excluded:</strong></td>
              <td>
                <select name="mapexcluded" size="1" title="true/false (default: false). Set to true to prevent the user from causing changes to the service maps. If a particular user is sending bad ecms or is otherwise misbehaving, this will protect the service mappings and ensure no other users are affected. Only use this if you are sure a particular client is misbehaving, the service mapping can't work if no clients are allowed to update the map.">
                  <option>TRUE</option>
                  <option selected="selected">FALSE</option>
                </select>
              </td>
            </tr>
            <tr>
              <td>&#160;</td>
              <td>
                <input id="btnReset" type="reset" value="Reset"/>&#160;
                <input id="btnAddUser" type="submit" value="Add User"/>
              </td>
            </tr>
          </tbody></table>
        </div>
	  </fieldset>
    </form>
	<br />
	<form action="" id="mysql-import-users">  	
      <fieldset>
	  <legend><strong>Import users from a xml source to the MySQL database.</strong></legend>
        <div class="cwsheader">
          <table border="0" width="99%" cellspacing="2" cellpadding="2"><tbody>
            <tr>
              <td width="5%"><strong>URL:</strong></td>
              <td width="95%">
                <input name="url" type="text" size="132" />
              </td>
            </tr>
            <tr>
              <td><strong>Key:</strong></td>
              <td>
                <input name="key" type="text" size="132" />
              </td>
            </tr>
            <tr>
              <td>&#160;</td>
              <td>
                <input type="reset" value="Reset"/>&#160;
        		<input type="submit" value="Import Users"/>
              </td>
            </tr>
          </tbody></table>
        </div>
	  </fieldset>    
    </form>
  </xsl:template>

  <xsl:template match="mysql-edit-user">
    <br /><br />
	<form action="" id="mysql-edit-user">
      <fieldset>
	  <legend><strong>Edit an existing MySQL database user.</strong></legend>
        <div class="cwsheader">
	      <xsl:for-each select="user">  	
            <table border="0" width="99%" cellspacing="2" cellpadding="2"><tbody>
              <tr>
                <td width="15%"><strong>Username:</strong></td>
                <td width="85%">
                  <input name="username" type="text" size="60" disabled="disabled" title="User name, avoid long names, spaces and special characters. There are no particular limitations as far as the proxy is concerned, but the camd clients may have them.">
                    <xsl:attribute name="value">
                      <xsl:value-of select="@username"/>
                    </xsl:attribute>
                  </input>
                </td>
              </tr>
              <tr>
                <td><strong>Password:</strong></td>
                <td>
                  <input name="password" id="inputPassword" type="password" size="60" title="Avoid special characters.">
                    <xsl:attribute name="value">
                      <xsl:value-of select="@password"/>
                    </xsl:attribute>
                  </input>
                </td>
              </tr>
              <tr>
                <td><strong>Retype Password:</strong></td>
                <td>
                  <input name="passwordretyped" id="inputPasswordRetyped" type="password" size="60" title="Avoid special characters.">
                    <xsl:attribute name="value">
                      <xsl:value-of select="@password"/>
                    </xsl:attribute>
                  </input>
                </td>
              </tr>
              <tr>
                <td><strong>Displayname:</strong></td>
                <td>
                  <input name="displayname" type="text" size="60" title="An optional non-unique alias for the user (used by the http/xml api).">
                    <xsl:attribute name="value">
                      <xsl:if test="@displayname != @username">
                        <xsl:value-of select="@displayname"/>
                      </xsl:if>
                    </xsl:attribute>
                  </input>
                </td>
              </tr>
              <tr>
	            <td><strong>Profiles:</strong></td>
	            <td>
	              <table border="0" width="320" cellspacing="1" cellpadding="1"><tbody>
	              <tr>
	                <td><input type="checkbox" name="profiles" value="ALL"/>ALL</td>
	                <td>&#160;</td>
	              </tr>
                  <xsl:if test="count(//mysql-edit-user/profile) > 0">
				    <xsl:for-each select="//mysql-edit-user/profile">
	                  <xsl:if test="position() mod 2 = 1">
	                    <tr />
	                  </xsl:if>
					  <td>
					    <input type="checkbox" name="profiles">
				          <xsl:attribute name="value"><xsl:value-of select="@profilename"/></xsl:attribute>
				          <xsl:if test="@checked = 'true'">
				      	    <xsl:attribute name="checked">checked</xsl:attribute>
				          </xsl:if>
				        </input><xsl:value-of select="@profilename"/>
	                  </td>
				    </xsl:for-each>
				  </xsl:if>
				  </tbody></table>
	            </td>
              </tr>
              <tr>
                <td><strong>E-Mail Address:</strong></td>
                <td>
                  <input name="mail" type="text" size="60" title="An optional parameter used in the MessagingPlugin.">
                    <xsl:attribute name="value">
                      <xsl:value-of select="@mail"/>
                    </xsl:attribute>
                  </input>
                </td>
              </tr>
              <tr>
                <td><strong>IP-Mask:</strong></td>
                <td>
                  <input name="ipmask" type="text" size="60" title="Only allow connections from a particular ip or ip range, for this user. This applies only to the newcamd protocol, not http/xml. Masks can use glob wildcards (? *), but this should typically not be used for users with dynamic ips - fixed only (no dns reverse lookups are performed, hostname masks will not be allowed).">
                    <xsl:attribute name="value">
                      <xsl:value-of select="@ipmask"/>
                    </xsl:attribute>
                  </input>
                </td>
              </tr>
              <tr>
                <td><strong>Max Connections:</strong></td>
                <td>
    			  <select name="maxconnections" size="1" title="Number of connections to allow, if the user exceeds this then any older existing connections will be closed. NOTE: as of 0.9.0 this has changed to max-connections per profile, meaning and old values will likely need to be changed. Since it is no longer the total number of connections, the value should now reflect the number of clients/boxes the user is expected to connect with (regardless of how many profiles the user has access to).">
      				<option>
                      <xsl:if test="@maxconnections = 0">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                      </xsl:if>
                      0
      				</option>
      				<option>
                      <xsl:if test="@maxconnections = 1">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                      </xsl:if>
                      1
      				</option>
      				<option>
                      <xsl:if test="@maxconnections = 2">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                      </xsl:if>
                      2
      				</option>
      				<option>
                      <xsl:if test="@maxconnections = 3">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                      </xsl:if>
                      3
      				</option>
      				<option>
                      <xsl:if test="@maxconnections = 4">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                      </xsl:if>
                      4
      				</option>
      				<option>
                      <xsl:if test="@maxconnections = 5">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                      </xsl:if>
                      5
      				</option>
      				<option>
                      <xsl:if test="@maxconnections = 6">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                      </xsl:if>
                      6
      				</option>
      				<option>
                      <xsl:if test="@maxconnections = 7">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                      </xsl:if>
                      7
      				</option>
      				<option>
                      <xsl:if test="@maxconnections = 8">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                      </xsl:if>
                      8
      				</option>
      				<option>
                      <xsl:if test="@maxconnections = 9">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                      </xsl:if>
                      9
      				</option>
    			  </select>
                </td>
              </tr>
              <tr>
                <td><strong>Enabled:</strong></td>
                <td>
    			  <select name="enabled" size="1" title="true/false (default: true). Allows disabling of accounts without deleting them.">
      				<option>
                      <xsl:if test="@enabled = 'true'">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                      </xsl:if>
      				  TRUE
      				</option>
      				<option>
                      <xsl:if test="@enabled = 'false'">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                      </xsl:if>
      				  FALSE
      				</option>
    			  </select>
                </td>
              </tr>
              <tr>
                <td><strong>Admin:</strong></td>
                <td>
    			  <select name="admin" size="1" title="true/false (default: false). Is this user an administrator? Affects access to http/xml api features only.">
      				<option>
                      <xsl:if test="@admin = 'true'">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                      </xsl:if>
      				  TRUE
      				</option>
      				<option>
                      <xsl:if test="@admin = 'false'">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                      </xsl:if>
      				  FALSE
      				</option>
    			  </select>
                </td>
              </tr>
              <tr>
                <td><strong>Debug:</strong></td>
                <td>
    			  <select name="debug" size="1" title="true/false (default: false). Set to true to enable ecm/emm/zap logging for this user only (has no effect if these are already enabled globally).">
      				<option>
                      <xsl:if test="@debug = 'true'">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                      </xsl:if>
      				  TRUE
      				</option>
      				<option>
                      <xsl:if test="@debug = 'false'">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                      </xsl:if>
      				  FALSE
      				</option>
    			  </select>
                </td>
              </tr>
              <tr>
                <td><strong>Map Excluded:</strong></td>
                <td>
    			  <select name="mapexcluded" size="1" title="true/false (default: false). Set to true to prevent the user from causing changes to the service maps. If a particular user is sending bad ecms or is otherwise misbehaving, this will protect the service mappings and ensure no other users are affected. Only use this if you are sure a particular client is misbehaving, the service mapping can't work if no clients are allowed to update the map.">
      				<option>
                      <xsl:if test="@mapexcluded = 'true'">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                      </xsl:if>
      				  TRUE
      				</option>
      				<option>
                      <xsl:if test="@mapexcluded = 'false'">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                      </xsl:if>
      				  FALSE
      				</option>
    			  </select>
                </td>
              </tr>
              <tr>
                <td>&#160;</td>
                <td>
        		  <input id="btnReset" type="reset" value="Reset"/>&#160;
        		  <input type="submit" value="Edit User"/>
                </td>
              </tr>
            </tbody></table>
          </xsl:for-each>
        </div>
	  </fieldset>
    </form>
  </xsl:template>
    
  <xsl:template match="mysql-select-user">
    <strong>Return to "MySQL-Users": </strong><a href="javascript:clickSection('mysqlusers');">here</a><br /><br />
    <fieldset>
	  <legend><strong>Select user in the MySQL database to edit.</strong></legend>
      <div class="cwsheader">
		Username:&#160;
        <xsl:if test="count(entry) > 0">
          <select name="username" id="selectUsername">
		    <xsl:for-each select="entry">
             <option>
               <xsl:attribute name="value"><xsl:value-of select="@username"/></xsl:attribute>
               <xsl:value-of select="@username"/>
             </option>
     	   </xsl:for-each>
   	     </select>&#160;
   	     <input id="loadUserBtn" type="button" value="Load User"/>
   	    </xsl:if>
        <xsl:if test="count(entry) = 0">
 	      <select name="username" id="selectUsername" disabled="disabled">
   	        <option>
     	      <xsl:attribute name="value">NONE</xsl:attribute>
     	      No users in database.
   	        </option>
   	      </select>&#160;
   	      <input id="loadUserBtn" type="button" value="Load User" disabled="disabled"/>
   	    </xsl:if>
      </div>
	</fieldset>    
  </xsl:template>
    
  <xsl:template match="mysql-delete-user">
    <strong>Return to "MySQL-Users": </strong><a href="javascript:clickSection('mysqlusers');">here</a><br />
	<form action="" id="mysql-delete-user">  	
      <fieldset>
	  <legend><strong>Delete user(s) in the MySQL database.</strong></legend>
        <div class="cwsheader">
		  Username:&#160;
          <xsl:if test="count(entry) > 0">
            <select name="username">
              <option>
                <xsl:attribute name="value">ALL</xsl:attribute>
                ALL
              </option>
              <xsl:for-each select="entry">
                <option>
                  <xsl:attribute name="value"><xsl:value-of select="@username"/></xsl:attribute>
                  <xsl:value-of select="@username"/>
                </option>
              </xsl:for-each>
            </select>&#160;
            <input type="hidden" name="confirm" value="true"/>
            <input type="submit" value="Delete User"/>
          </xsl:if>
          <xsl:if test="count(entry) = 0">
 	        <select name="username" disabled="disabled">
   	          <option>
     	        <xsl:attribute name="value">NONE</xsl:attribute>
     	        No users in database.
   	          </option>
   	        </select>&#160;
   	        <input type="submit" value="Delete User" disabled="disabled"/>
          </xsl:if>
        </div>
	  </fieldset>    
    </form>
  </xsl:template>
    
  <xsl:template match="mysql-profiles">
    <strong>Return to "MySQL-Users": </strong><a href="javascript:clickSection('mysqlusers');">here</a><br /><br />
    
      <xsl:if test="count(profile) > 0">
        <fieldset>
          <legend><strong>Profile(s) stored in the MySQL database.</strong></legend>
          <div class="cwsheader">
            <table border="0" width="99%"><tbody>
              <tr>
                <td><strong>Profilename</strong></td>
              </tr>
              <xsl:for-each select="profile">
                <xsl:sort order="ascending" select="@profilename"/>
                <tr>
                  <xsl:if test="position() mod 2 = 1">
                    <xsl:attribute name="bgcolor">#ffffff</xsl:attribute>
                  </xsl:if>
	              <td><xsl:value-of select="@profilename"/></td>
                </tr>
              </xsl:for-each>
            </tbody></table>
          </div>
        </fieldset>
    	<br /><br />
      </xsl:if>
      
	<form action="" id="mysql-add-profile">  	
    <fieldset>
	  <legend><strong>Add a new profile to the MySQL Database.</strong></legend>
      <div class="cwsheader">
        <table border="0" width="99%" cellspacing="2" cellpadding="2"><tbody>
          <tr>
            <td width="10%"><strong>Profilename:</strong></td>
            <td width="90%"><input name="profilename" type="text" size="50"/></td>
          </tr>
          <tr>
            <td>&#160;</td>
            <td>
        	  <input type="reset" value="Reset"/>&#160;
        	  <input type="submit" value="Add Profile"/>
            </td>
          </tr>
        </tbody></table>
      </div>
	</fieldset>
    </form>
    <br />
    
	<form action="" id="mysql-delete-profile">  	
      <fieldset>
	  <legend><strong>Delete profile(s) in the MySQL database.</strong></legend>
        <div class="cwsheader">
		  Profilename:&#160;
          <xsl:if test="count(profile) > 0">
            <select name="profilename">
              <option>
                <xsl:attribute name="value">ALL</xsl:attribute>
                ALL
              </option>
              <xsl:for-each select="profile">
                <option>
                  <xsl:attribute name="value"><xsl:value-of select="@profilename"/></xsl:attribute>
                  <xsl:value-of select="@profilename"/>
                </option>
              </xsl:for-each>
            </select>&#160;
            <input type="hidden" name="confirm" value="true"/>
            <input type="submit" value="Delete Profile"/>
          </xsl:if>
          <xsl:if test="count(profile) = 0">
 	        <select name="profilename" disabled="disabled">
   	          <option>
     	        <xsl:attribute name="value">NONE</xsl:attribute>
     	        No profiles in database.
   	          </option>
   	        </select>&#160;
   	        <input type="submit" value="Delete Profile" disabled="disabled"/>
          </xsl:if>
        </div>
	  </fieldset>    
    </form>
    <br /><br />

  </xsl:template>

  <xsl:template name="jvm">
    <span style="font-size: smaller">
      <xsl:for-each select="//proxy-status/jvm">
        <strong>CSP <xsl:value-of select="../@version"/> <xsl:value-of select="../@build"/></strong> - <strong>JVM: </strong>
        <span id="jvm">[<xsl:value-of select="@name"/> <xsl:value-of select="@version"/>]</span><strong> Heap: </strong>
        [<xsl:value-of select="@heap-total - @heap-free"/>k/<xsl:value-of select="@heap-total"/>k] <strong>TC: </strong>
        <span id="tc"> [<xsl:value-of select="@threads"/>]</span>
        <xsl:if test="@filedesc-open">
          <strong> FD: </strong> [<xsl:value-of select="@filedesc-open"/>/<xsl:value-of select="@filedesc-max"/>]
        </xsl:if>
        <strong> OS: </strong> [<xsl:value-of select="@os"/>]
        <div style="position: absolute; top: 30px; left: 120px;"><strong><xsl:value-of select="@time"/></strong></div>
        <div id="autoPollDiv" style="position: absolute; top: 26px; left: 660px;">
          <input type="checkbox" name="autoPollCb" id="autoPollCb" checked="checked"/>
          <label for="autoPollCb">Auto polling</label>
        </div>
      </xsl:for-each>
    </span>
  </xsl:template>

</xsl:stylesheet>

