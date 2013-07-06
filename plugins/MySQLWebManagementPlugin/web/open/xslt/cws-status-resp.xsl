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

	<xsl:template match="mysql-users">
		<div align="center" style="width: 750px;">
			<input type="button" id="btnAddUser" value="Add..." style="width: 100px;" />
			<input type="button" id="btnEditUser" value="Edit..." style="width: 100px;" />
			<input type="button" id="btnDeleteUser" value="Delete" style="width: 100px;" />
			<input type="button" id="btnDeleteAllUsers" value="Delete All" style="width: 100px;" />
			<input type="button" id="btnImport" value="Import..." style="width: 100px;" />
			<input type="button" id="btnProfiles" value="Profiles..." style="width: 100px;" />
		</div>
		<xsl:if test="count(user) > 0">
			<br />
			<div class="cwsheader" style="width: 750px;">
				<table id="tableMySQLUsers" border="0" width="100%" cellspacing="2" cellpadding="0">
					<thead>
						<tr>
							<th width="4"></th>
							<th width="15%">
								<Strong>Username</Strong>
							</th>
							<th width="25%">
								<Strong>Displayname</Strong>
							</th>
							<th width="20%"><Strong>E-Mail</Strong></th>
							<th width="15%"><strong>IP-Mask</strong></th>
							<th width="5%"><strong>MC</strong></th>
							<th width="5%"><strong>E</strong></th>
							<th width="5%"><strong>A</strong></th>
							<th width="5%"><strong>D</strong></th>
							<th width="5%"><strong>ME</strong></th>
						</tr>
					</thead>
					<tbody>
						<xsl:call-template name="mysql-users"/>
					</tbody>
				</table>
			</div>
		</xsl:if>
		<xsl:if test="count(page) > 0">
			<div align="center" style="width: 750px;">
				Page-Number:&#160;
				<xsl:for-each select="page">
					<a>
						<xsl:attribute name="href">javascript:selectPage('<xsl:value-of select="@num - 1"/>');</xsl:attribute>
						<xsl:value-of select="@num"/>
					</a>&#160;
				</xsl:for-each>
			</div>
		</xsl:if>
	</xsl:template>

	<xsl:template name="mysql-users">
		<xsl:for-each select="user">
			<xsl:sort select="@username" order="ascending" data-type="text" />
			<tr>
				<xsl:attribute name="id"><xsl:value-of select="@username"/></xsl:attribute>
				<xsl:if test="position() mod 2 = 1">
					<xsl:attribute name="bgcolor">#ffffff</xsl:attribute>
				</xsl:if>
				<td align="center">
					<input type="radio" name="rdSelectedUser"> 
						<xsl:attribute name="id"><xsl:value-of select="@username"/></xsl:attribute>
						<xsl:if test="position() = 1"><xsl:attribute name="checked">checked</xsl:attribute></xsl:if>
					</input>
				</td>
				<td>
					&#160;
					<a target="_blank">
						<xsl:attribute name="href">/xmlHandler?command=mysql-users&amp;username=<xsl:value-of select="@username"/></xsl:attribute>
						<xsl:attribute name="title">Show account details for user</xsl:attribute>
						<xsl:value-of select="@username"/>
					</a>
				</td>
				<td >
					&#160;
					<xsl:value-of select="@displayname"/>
				</td>
				<td>
					&#160;
					<xsl:value-of select="@mail"/>
				</td>
				<td>
					&#160;
					<xsl:value-of select="@ipmask"/>
				</td>
				<td align="center">
					<xsl:value-of select="@maxconnections"/>
				</td>
				<td align="center">
					<xsl:value-of select="@enabled"/>
				</td>
				<td align="center">
					<xsl:value-of select="@admin"/>
				</td>
				<td align="center">
					<xsl:value-of select="@debug"/>
				</td>
				<td align="center">
					<xsl:value-of select="@mapexcluded"/>
				</td>
			</tr>
		</xsl:for-each>
	</xsl:template>

	<xsl:template match="mysql-add-user">
		<strong>Return to "MySQL-Users": </strong><a href="javascript:clickSection('mysqlusers');">here</a><br /><br />
		<h1>Add new User:</h1>
		<form action="" id="mysql-add-user">
			<div class="cwsheader" style="width: 750px;">
				<table border="0" width="100%" cellspacing="5" cellpadding="2">
					<tr>
						<th align="right" width="15%">Username:</th>
						<td><input name="username" type="text" size="60" title="User name, avoid long names, spaces and special characters. There are no particular limitations as far as the proxy is concerned, but the camd clients may have them."/></td>
					</tr>
					<tr>
						<th align="right">Password:</th>
						<td><input name="password" id="inputPassword" type="password" size="60" title="Avoid special characters."/></td>
					</tr>
					<tr>
						<th align="right">Retype Password:</th>
						<td><input name="passwordretyped" id="inputPasswordRetyped" type="password" size="60" title="Avoid special characters."/></td>
					</tr>
					<tr>
						<th align="right">Displayname:</th>
						<td><input name="displayname" type="text" size="60" title="An optional non-unique alias for the user (used by the http/xml api)."/></td>
					</tr>
					<tr>
						<th align="right">Profiles:</th>
						<td>
							<table id="profiles" border="0" width="320" cellspacing="1" cellpadding="1"><tbody>
								<tr>
									<td><input type="checkbox" id="chkbxAllProfiles" value="ALL"/>ALL</td>
									<td>&#160;</td>
								</tr>
								<xsl:if test="count(profile) > 0">
									<xsl:for-each select="profile">
										<xsl:if test="position() mod 2 = 1">
											<tr />
										</xsl:if>
										<td>
											<input type="checkbox" name="chkbxProfiles">
												<xsl:attribute name="id"><xsl:value-of select="@id"/></xsl:attribute>
											</input><xsl:value-of select="@profilename"/>
										</td>
									</xsl:for-each>
								</xsl:if>
							</tbody></table>
						</td>
					</tr>
					<tr>
						<th align="right">E-Mail Address:</th>
						<td colspan="2"><input name="mail" type="text" size="60" title="An optional parameter used in the MessagingPlugin."/></td>
					</tr>
					<tr>
						<th align="right">IP-Mask:</th>
						<td><input name="ipmask" type="text" size="60" title="Only allow connections from a particular ip or ip range, for this user. This applies only to the newcamd protocol, not http/xml. Masks can use glob wildcards (? *), but this should typically not be used for users with dynamic ips - fixed only (no dns reverse lookups are performed, hostname masks will not be allowed)."/></td>
					</tr>
					<tr>
						<th align="right">Max Connections:</th>
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
						<th align="right">Enabled:</th>
						<td>
							<select name="enabled" size="1" title="true/false (default: true). Allows disabling of accounts without deleting them.">
								<option selected="selected">TRUE</option>
								<option>FALSE</option>
							</select>
						</td>
					</tr>
					<tr>
						<th align="right">Admin:</th>
						<td>
							<select name="admin" size="1" title="true/false (default: false). Is this user an administrator? Affects access to http/xml api features only.">
								<option>TRUE</option>
								<option selected="selected">FALSE</option>
							</select>
						</td>
					</tr>
					<tr>
						<th align="right">Debug:</th>
						<td>
							<select name="debug" size="1" title="true/false (default: false). Set to true to enable ecm/emm/zap logging for this user only (has no effect if these are already enabled globally).">
								<option>TRUE</option>
								<option selected="selected">FALSE</option>
							</select>
						</td>
					</tr>
					<tr>
						<th align="right">Map Excluded:</th>
						<td>
							<select name="mapexcluded" size="1" title="true/false (default: false). Set to true to prevent the user from causing changes to the service maps. If a particular user is sending bad ecms or is otherwise misbehaving, this will protect the service mappings and ensure no other users are affected. Only use this if you are sure a particular client is misbehaving, the service mapping can't work if no clients are allowed to update the map.">
								<option>TRUE</option>
								<option selected="selected">FALSE</option>
							</select>
						</td>
					</tr>
					<tr>
						<th>&#160;</th>
						<td>
							<input id="btnAddUser" type="submit" value="OK"/>
							<input id="btnReset" type="reset" value="Reset"/>
							<input id="btnAbort" type="button" value="Abort"/>
						</td>
					</tr>
				</table>
			</div>
		</form>
	</xsl:template>

	<xsl:template match="mysql-edit-user">
		<strong>Return to "MySQL-Users": </strong><a href="javascript:clickSection('mysqlusers');">here</a>
		<br /><br />
		<h1>Edit User:</h1>
		<form action="" id="mysql-edit-user">
			<div class="cwsheader" style="width: 750px;">
				<xsl:for-each select="user">  	
					<table border="0" width="100%" cellspacing="5" cellpadding="2">
						<tr>
							<th align="right" width="15%">Username:</th>
							<td>
								<input name="id" type="hidden">
									<xsl:attribute name="value">
										<xsl:value-of select="@id"/>
									</xsl:attribute>
								</input>
								<input name="username" type="text" size="60" disabled="disabled" title="User name, avoid long names, spaces and special characters. There are no particular limitations as far as the proxy is concerned, but the camd clients may have them.">
									<xsl:attribute name="value">
										<xsl:value-of select="@username"/>
									</xsl:attribute>
								</input>
							</td>
						</tr>
						<tr>
							<th align="right">Password:</th>
							<td>
								<input name="password" id="inputPassword" type="password" size="60" title="Avoid special characters.">
									<xsl:attribute name="value">
										<xsl:value-of select="@password"/>
									</xsl:attribute>
								</input>
							</td>
						</tr>
						<tr>
							<th align="right">Retype Password:</th>
							<td>
								<input name="passwordretyped" id="inputPasswordRetyped" type="password" size="60" title="Avoid special characters.">
									<xsl:attribute name="value">
										<xsl:value-of select="@password"/>
									</xsl:attribute>
								</input>
							</td>
						</tr>
						<tr>
							<th align="right">Displayname:</th>
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
							<th align="right">Profiles:</th>
							<td>
								<table id="profiles" border="0" width="320" cellspacing="1" cellpadding="1"><tbody>
									<tr>
										<td><input type="checkbox" id="chkbxAllProfiles" value="ALL"/>ALL</td>
										<td>&#160;</td>
									</tr>
									<xsl:if test="count(//mysql-edit-user/profile) > 0">
										<xsl:variable name="profiles" select="concat(@profiles, ' ')"/>
										<xsl:for-each select="//mysql-edit-user/profile">
											<xsl:if test="position() mod 2 = 1">
												<tr />
											</xsl:if>
											<td>
												<input type="checkbox" name="chkbxProfiles">
													<xsl:attribute name="id"><xsl:value-of select="@id"/></xsl:attribute>
													<xsl:if test="contains($profiles, concat(@profilename,' '))">
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
							<th align="right">E-Mail Address:</th>
							<td>
								<input name="mail" type="text" size="60" title="An optional parameter used in the MessagingPlugin.">
									<xsl:attribute name="value">
										<xsl:value-of select="@mail"/>
									</xsl:attribute>
								</input>
							</td>
						</tr>
						<tr>
							<th align="right">IP-Mask:</th>
							<td>
								<input name="ipmask" type="text" size="60" title="Only allow connections from a particular ip or ip range, for this user. This applies only to the newcamd protocol, not http/xml. Masks can use glob wildcards (? *), but this should typically not be used for users with dynamic ips - fixed only (no dns reverse lookups are performed, hostname masks will not be allowed).">
									<xsl:attribute name="value">
										<xsl:value-of select="@ipmask"/>
									</xsl:attribute>
								</input>
							</td>
						</tr>
						<tr>
							<th align="right">Max Connections:</th>
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
							<th align="right">Enabled:</th>
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
							<th align="right">Admin:</th>
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
							<th align="right">Debug:</th>
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
							<th align="right">Map Excluded:</th>
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
							<th>&#160;</th>
							<td>
								<input id="btnEditUser" type="submit" value="OK"/>
								<input id="btnReset" type="reset" value="Reset"/>
								<input id="btnAbort" type="button" value="Abort"/>
							</td>
						</tr>
					</table>
				</xsl:for-each>
			</div>
		</form>
	</xsl:template>

	<xsl:template match="mysql-profiles">
		<strong>Return to "MySQL-Users": </strong><a href="javascript:clickSection('mysqlusers');">here</a><br /><br />
		<h1>Manage Profiles:</h1>
		<div class="cwsheader" style="width: 750px;">
			<table border="0" cellspacing="4" width="100%"><tbody>
				<tr>
					<td width="10%">Profile-Id:</td>
					<td width="45%" colspan="4">
						<input id="txtProfileId" type="text" disabled="disabled" style="width: 97%" />
					</td>
					<td rowspan="4" width="45%" align="right">
						<select id="selectProfileName" size="5" style="width: 100%">
							<option selected="selected">
								<xsl:attribute name="value">NEW</xsl:attribute>
								NEW PROFILE
							</option>
							<xsl:for-each select="profile">
								<option>
									<xsl:attribute name="value"><xsl:value-of select="@id"/>%<xsl:value-of select="@profilename"/></xsl:attribute>
									<xsl:value-of select="@profilename"/>
								</option>
							</xsl:for-each>
						</select>
					</td>
				</tr>
				<tr>
					<td>Profile-Name:</td>
					<td colspan="4">
						<input id="txtProfileName" type="text" style="width: 97%" />
					</td>
				</tr>
				<tr>
					<td></td>
					<td>
						<input id="btnAddProfile" type="button" value="Add" style="width: 97%" />
					</td>
					<td>
						<input id="btnEditProfile" type="button" value="Edit" style="width: 97%" disabled="disabled" />
					</td>
					<td>
						<input id="btnDeleteProfile" type="button" value="Delete" style="width: 97%" disabled="disabled" />
					</td>
					<td>
						<input id="btnDeleteAllProfiles" type="button" value="Delete All" style="width: 97%" />
						<xsl:if test="count(profile) = 0"><xsl:attribute name="disabled">disabled</xsl:attribute></xsl:if>
					</td>
				</tr>
			</tbody></table>
		</div>
		<br /><br />
	</xsl:template>

	<xsl:template match="mysql-import">
		<strong>Return to "MySQL-Users": </strong><a href="javascript:clickSection('mysqlusers');">here</a><br /><br />
		<h1>Import Users and Profiles (xml):</h1>
		<form action="" id="mysql-import">  	
			<div class="cwsheader" style="width: 750px;">
				<table border="0" width="100%" cellspacing="5" cellpadding="2"><tbody>
					<tr>
						<td width="5%"><strong>URL:</strong></td>
						<td width="95%"><input name="url" type="text" size="132" /></td>
					</tr>
					<tr>
						<td><strong>Key:</strong></td>
						<td><input name="key" type="text" size="132" /></td>
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
		</form>
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
