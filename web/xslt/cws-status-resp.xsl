<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="html"/>

  <xsl:template match="cws-status-resp">
    <div id="header">
      <div id="headerInfoLeft"><xsl:call-template name="jvm"/></div>
      <div id="headerInfo">
        <img id="busyImg" src="/images/bigrotation.gif" alt="loading" style="visibility: hidden;"/>
      </div>

    </div>
    <div id="subheader">
      <a href="#" id="events">Events</a>&#160;
      <a href="#" id="channels">Channels</a>&#160;
      <a href="#" id="status">Status</a>&#160;
      <a href="#" id="sessions">Sessions</a>&#160;
      <a href="#" id="admin" style="visibility: hidden">Admin</a>&#160;
      <a href="#" id="config" style="visibility: hidden">Config</a>&#160;
      <!-- <a href="#" id="viewXml">View XML</a>&#160; -->
      <a href="#" id="logout" style="visibility: visible; position: absolute; left: 745px">Logout</a>
    </div>
    <div id="mainstart">&#160;</div>
    <div id="main">
      <div id="content">
        <xsl:apply-templates/>
      </div>
    </div>
    <div id="footer">&#160;</div>
  </xsl:template>

  <xsl:template match="error-log">
    <xsl:call-template name="profile-filter"/>
    <fieldset>
      <legend><strong>CWS Events</strong> (connector activities) <input id="clearEventsBtn" type="button" value="Clear"/> </legend>
      <table class="error-log">
        <tbody>
          <tr><td>
            <xsl:for-each select="event">
              <xsl:value-of select="@timestamp"/> -
              <strong> <xsl:value-of select="@label"/>: </strong>
              <strong>
                <xsl:choose>
                  <xsl:when test="@type = 1">Channel changed</xsl:when>
                  <xsl:when test="@type = 2">Successfully connected</xsl:when>
                  <xsl:when test="@type = 3">Disconnected</xsl:when>
                  <xsl:when test="@type = 4">Connection attempt failed</xsl:when>
                  <xsl:when test="@type = 5">Warning (timeout)</xsl:when>
                  <xsl:when test="@type = 6">Lost service</xsl:when>
                  <xsl:when test="@type = 9">Found service</xsl:when>
                  <xsl:when test="@type = 8">Invalid card data</xsl:when>
                  <xsl:when test="@type = 10">Startup</xsl:when>
                  <xsl:otherwise>Unknown</xsl:otherwise>
                </xsl:choose>
              </strong> - <xsl:value-of select="@msg"/><br />
            </xsl:for-each>
            <xsl:if test="@size = 0">No events</xsl:if>
          </td></tr>
        </tbody>
      </table>
    </fieldset><br /><br />
  </xsl:template>

  <xsl:template match="file-log">
    <xsl:if test="@size > -1">
    <fieldset>
      <legend><strong>File Log Events</strong> (recent WARNING and SEVERE level loggings) <input id="clearFileLogBtn" type="button" value="Clear"/> </legend>
      <table class="error-log">
        <tbody>
          <tr><td>
            <xsl:for-each select="event">
              <xsl:value-of select="@timestamp"/> -
              <strong> <xsl:value-of select="@log-level"/>: </strong>
              <strong><xsl:value-of select="@label"/></strong> - <xsl:value-of select="@msg"/><br />
            </xsl:for-each>
            <xsl:if test="@size = 0">No events</xsl:if>
          </td></tr>
        </tbody>
      </table>
    </fieldset><br /><br />
    </xsl:if>
  </xsl:template>

  <xsl:key name="ev-by-time" match="ecm" use="substring(@timestamp, 1, string-length(@timestamp) - 3)"/>

  <xsl:template match="user-warning-log">
    <fieldset>
      <legend><strong>User Transaction Warnings</strong> (recent potential traffic problems) <input id="clearWarningsBtn" type="button" value="Clear"/> </legend>
      <table class="error-log">
        <tbody>
          <tr><td> <!-- bwahaha, this is just an insane amount of xsl crud just to get grouping of events per minute -->
            <xsl:for-each select="ecm[generate-id(.) = generate-id(key('ev-by-time', substring(@timestamp, 1, string-length(@timestamp) - 3))[1])]">
              <xsl:variable name="curcount" select="count(key('ev-by-time', substring(@timestamp, 1, string-length(@timestamp) - 3)))"/>
              <xsl:variable name="trunctime" select="substring(@timestamp, 1, string-length(@timestamp) - 3)"/>
              <xsl:choose>
                <xsl:when test="$curcount = 1"> <!-- show as single event, no indentation -->
                  <xsl:call-template name="user-warning-entry">
                    <xsl:with-param name="indent" select="''"/>
                  </xsl:call-template>
                </xsl:when>
                <xsl:otherwise>
                  <a id="openhref"> <!-- show as group, indented and collapsible with the minute-timestamp as href -->
                    <xsl:attribute name="href"><xsl:value-of select="translate($trunctime, ' ', '_')"/></xsl:attribute>
                    <xsl:value-of select="$trunctime"/> (<xsl:value-of select="$curcount"/> events)
                  </a>
                  <br /><div>
                  <xsl:attribute name="style">display: <xsl:value-of select="@display"/>;</xsl:attribute>
                  <xsl:attribute name="id"><xsl:value-of select="translate($trunctime, ' ', '_')"/></xsl:attribute>
                  <xsl:for-each select="key('ev-by-time', $trunctime)">
                    <xsl:call-template name="user-warning-entry">
                      <xsl:with-param name="indent" select="'&#160;&#160;&#160;'"/>
                    </xsl:call-template>
                  </xsl:for-each>
                </div>
                </xsl:otherwise>
              </xsl:choose>
            </xsl:for-each>
            <xsl:if test="count(ecm) = 0">No warnings (debugging must be enabled per ca-profile for these to be logged)</xsl:if>
          </td></tr>
        </tbody>
      </table>
    </fieldset><br /><br />
    <xsl:if test="count(ecm) &gt; 0">
      <xsl:call-template name="flag-legend"/>
    </xsl:if>
  </xsl:template>

  <xsl:template match="cws-connectors">
    <xsl:call-template name="proxy-status"/>
    <br />
    <xsl:call-template name="cache-status"/>
    <xsl:call-template name="proxy-plugins"/>
    <br />
    <xsl:call-template name="ca-profiles"/>
    <br />
    <fieldset>
      <legend><strong>Connectors (<xsl:value-of select="count(connector)"/>)</strong></legend>
      <xsl:for-each select="connector">
        <xsl:sort select="@profile"/>
        <xsl:sort select="@name"/>
        <div class="cwsheader">
          <table border="0" width="98%" cellspacing="0" cellpadding="0"><tr>
            <td width="25%"><strong><xsl:value-of select="@protocol"/>Cws: </strong>
              <xsl:choose>
                <xsl:when test="@duration"> <!-- duration exists == connector is connected -->
                  <a id="openhref">
                    <xsl:attribute name="href"><xsl:value-of select="@name"/></xsl:attribute>
                    <xsl:value-of select="@name"/>
                  </a>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="@name"/>
                </xsl:otherwise>
              </xsl:choose>
            </td>
            <td width="20%"><strong>Status: </strong><xsl:value-of select="@status"/></td>
            <td width="20%"><strong>Profile: </strong><xsl:value-of select="@profile"/></td>
            <td width="15%">
              <xsl:if test="@service-count">
                <strong>Services: </strong><xsl:value-of select="@service-count"/>
              </xsl:if>
              <xsl:if test="@disconnected">
                <strong>Disconnected: </strong><br /><xsl:value-of select="@disconnected"/>
              </xsl:if>
            </td>
            <td width="10%">
              <xsl:if test="@metric != 1">
                <strong>Metric: </strong><xsl:value-of select="@metric"/>
              </xsl:if>
            </td>
            <td width="20%" align="right">
              <xsl:if test="@utilization">
                <xsl:choose>
                  <xsl:when test="@utilization &gt; 100">
                    <strong><font color="red"><xsl:value-of select="@utilization"/>%</font></strong>
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:value-of select="@utilization"/>%
                  </xsl:otherwise>
                </xsl:choose>
                &#160;(<xsl:value-of select="@ecm-load"/>)
              </xsl:if>
              <xsl:if test="@next-attempt">
                <strong>Retry in: </strong><xsl:value-of select="@next-attempt"/>
              </xsl:if>
            </td>
          </tr>
          </table>
          <div class="cwscontent">
            <xsl:attribute name="style">display: <xsl:value-of select="@display"/>;</xsl:attribute>
            <xsl:attribute name="id"><xsl:value-of select="concat('toggle-', @name)"/></xsl:attribute>
            <xsl:if test="@host"><strong>Host: </strong><xsl:value-of select="@host"/><br /></xsl:if>
            <xsl:if test="@provider-idents"><strong>Provider-idents: </strong><xsl:value-of select="@provider-idents"/><br /></xsl:if>
            <strong>Connected: </strong><xsl:value-of select="@connected"/><br />
            <strong>Uptime: </strong><xsl:value-of select="@duration"/><br />
            <xsl:if test="@card-data1"><strong>Card-data: </strong><xsl:value-of select="@card-data1"/><br /></xsl:if>
            <strong>Processing time: </strong><xsl:value-of select="@cutime"/> ms (avg: <xsl:value-of select="@avgtime"/> ms)<br />
            <strong>Utilization: </strong><xsl:value-of select="@utilization"/>% (total: <xsl:value-of select="@avgutilization"/>%)<br />
            <strong>Estimated capacity: </strong><xsl:value-of select="@capacity"/><br />
            <strong>Queue size: </strong><xsl:value-of select="@sendq"/><br />
            <strong>ECM count: </strong><xsl:value-of select="@ecm-count"/><br />
            <strong>ECM load: </strong><xsl:value-of select="@ecm-load"/> (over the last cw-max-age period)<br />
            <xsl:if test="@emm-count &gt; 0"><strong>EMM count: </strong><xsl:value-of select="@emm-count"/><br /></xsl:if>
            <strong>Timeouts: </strong><xsl:value-of select="@timeout-count"/><br />
            <xsl:if test="@cws-log">
              <strong>Transaction log: </strong>
              <a target="_blank">
                <xsl:attribute name="href">/xmlHandler?command=cws-log&amp;name=<xsl:value-of select="@name"/></xsl:attribute>
                <xsl:value-of select="@cws-log"/>
              </a>
              <br />
            </xsl:if>
            <br />
            <table width="95%" cellspacing="0" cellpadding="0"><tr>
              <td width="55%" valign="top">
                <strong>Services: </strong>&#160;
                <xsl:if test="count(service) > 0">
                  <a target="_blank">
                    <xsl:attribute name="href">/xmlHandler?command=export-services&amp;name=<xsl:value-of select="@name"/></xsl:attribute>
                    show full
                  </a>&#160;
                  <a target="_blank">
                    <xsl:attribute name="href">/xmlHandler?command=export-services&amp;name=<xsl:value-of select="@name"/>&amp;format=hex</xsl:attribute>
                    show hex
                  </a>
                </xsl:if>
                <ul>
                  <xsl:for-each select="service">
                    <xsl:sort select="@name"/>
                    <li>
                    <xsl:choose>
                      <xsl:when test="@hit = 'true'"><font color="blue"><strong><xsl:value-of select="@name"/></strong></font></xsl:when>
                      <xsl:otherwise><xsl:value-of select="@name"/></xsl:otherwise>
                    </xsl:choose>
                    <xsl:choose>
                      <xsl:when test="not(starts-with(@name, 'Unknown '))"> (<xsl:value-of select="@hex-id"/><xsl:if test="@profile">:<xsl:value-of select="@profile"/></xsl:if>)</xsl:when>
                      <xsl:otherwise><xsl:if test="@profile"> (<xsl:value-of select="@profile"/>)</xsl:if></xsl:otherwise>
                    </xsl:choose>
                  </li>
                  </xsl:for-each>
                </ul>
              </td>
              <td valign="top">
                <strong>Remote properties: </strong>
                <ul>
                  <xsl:for-each select="remote-info/cws-param"><li>
                    <strong><xsl:value-of select="@name"/>: </strong>
                    <xsl:choose>
                      <xsl:when test="@name = 'url'">
                        <a target="_blank">
                          <xsl:attribute name="href"><xsl:value-of select="@value"/></xsl:attribute>
                          <xsl:value-of select="@value"/>
                        </a>                        
                      </xsl:when>
                      <xsl:otherwise>
                        <xsl:value-of select="@value"/>
                      </xsl:otherwise>
                    </xsl:choose>
                  </li>
                  </xsl:for-each>
                </ul>
              </td>
            </tr></table>
          </div>
        </div>
      </xsl:for-each>
    </fieldset><br />
  </xsl:template>

  <xsl:template match="watched-services">
    <xsl:call-template name="profile-filter"/>

    <xsl:if test="count(//linked-services/link/service) &gt; 0">
    <fieldset>
      <legend><strong>Channel groups using same dcw (configured, not verified) (<xsl:value-of select="count(//linked-services/link)"/>)</strong></legend><br />
      <xsl:for-each select="//linked-services/link">
        <xsl:if test="count(service) &gt; 0">
        <xsl:value-of select="@id"/>.
        <xsl:for-each select="service">
          <xsl:variable name="srvname" select="@name"/>
          <xsl:variable name="srvprofile" select="@profile"/>
          <xsl:choose>
          <xsl:when test="//watched-services/service[@name=$srvname and @profile=$srvprofile]"> <!-- highlight linked services currently being watched -->
            <font color="blue"><strong><xsl:value-of select="@name"/></strong> (<xsl:value-of select="@profile"/>:<xsl:value-of select="@cdata"/>) </font>
          </xsl:when>
          <xsl:otherwise>
            <strong><xsl:value-of select="@name"/></strong> (<xsl:value-of select="@profile"/>:<xsl:value-of select="@cdata"/>)
          </xsl:otherwise>
          </xsl:choose>
        </xsl:for-each>
        <br />
        </xsl:if>
      </xsl:for-each>
      <br />
    </fieldset><br />
    </xsl:if>

    <fieldset>
      <legend><strong>Currently watched channels (<xsl:value-of select="@count"/>)</strong></legend><br />
      <xsl:for-each select="service">
        <xsl:sort select="@profile"/>
        <strong><xsl:value-of select="@name"/></strong> (<xsl:value-of select="@profile"/>:<xsl:value-of select="@hex-id"/>) - <strong><xsl:value-of select="@watchers"/></strong> viewers<br />
      </xsl:for-each>
      <xsl:if test="count(service) = 0">No channels</xsl:if>
      <br />
    </fieldset><br />

    <xsl:if test="count(//all-services) = 0">
      <br />
      <strong>All mapped channels: </strong><a href="#" id="showAllServices"> show</a>
    </xsl:if>
  </xsl:template>

  <xsl:template match="all-services">
    <!-- xsl:call-template name="profile-filter"/ -->
    <!-- xsl:call-template name="watched-services"/ -->
    <br />
    <fieldset>
      <legend><strong>All mapped channels (<xsl:value-of select="@count"/>)</strong></legend><br />
      <ul id="chanList">
        <xsl:for-each select="service">
          <li class="channel">
            <xsl:attribute name="id"><xsl:value-of select="concat('channel', @id)"/></xsl:attribute>
            <img width="40">
              <xsl:attribute name="src"><xsl:value-of select="concat('picon/', @id, '.', @profile, '.png')"/></xsl:attribute>
            </img>
            <input name="chanId" type="checkbox">
              <xsl:attribute name="name"><xsl:value-of select="@id"/></xsl:attribute>
              <xsl:attribute name="id"><xsl:value-of select="concat('chan', @id)"/></xsl:attribute>
            </input>
            <label>
              <xsl:attribute name="for"><xsl:value-of select="concat('chan', @id)"/></xsl:attribute>
              <strong><xsl:value-of select="@name"/></strong> (<xsl:value-of select="@profile"/>:<xsl:value-of select="@hex-id"/>)
            </label>
          </li>
        </xsl:for-each>
      </ul><br />
      <xsl:if test="count(service) > 0">
        <input name="checkAllCb" id="checkAllCb" type="checkbox"/>
        <label for="checkAllCb">Check all</label>&#160;
        <input name="enigma2Cb" id="enigma2Cb" type="checkbox"/>
        <label for="enigma2Cb">Use enigma2 format</label>&#160;
        <input value="Create bouquet file" type="button" id="createChannelFileBtn"/><br />
      </xsl:if>
      <xsl:if test="count(service) = 0">
        No channels<br />
      </xsl:if>
    </fieldset>
  </xsl:template>

  <xsl:template match="last-seen">
    <strong>Name: </strong><xsl:value-of select="../proxy-status/@name"/><br />
    <strong>State: </strong>up<br />
    <br />
    <strong>Sessions: </strong><a href="javascript:clickSection('sessions');">show current</a><br />
    <br />
    <xsl:for-each select="//last-seen">
      <xsl:if test="count(entry) > 0">
        <fieldset id="disconnectedUsers">
          <legend><strong>Disconnected users (last seen)</strong></legend>
          <div class="cwsheader">
            <table border="0" width="80%"><tbody>
              <tr>
                <td><strong>User</strong></td><td><strong>Profile</strong></td><td><strong>Last login</strong></td><td><strong>Last seen (or logout)</strong></td><td><strong>IP</strong></td><td><strong>Log</strong></td>
              </tr>
              <xsl:for-each select="entry">
                <xsl:sort order="descending" select="@last-seen"/>
                <tr>
                  <xsl:if test="position() mod 2 = 1">
                    <xsl:attribute name="bgcolor">#ffffff</xsl:attribute>
                  </xsl:if>
                  <td><xsl:value-of select="@name"/></td>
                  <td><xsl:value-of select="@profile"/></td>
                  <td><xsl:value-of select="@last-login"/></td>
                  <td><xsl:value-of select="@last-seen"/></td>
                  <td><xsl:value-of select="@host"/></td>
                  <td><a target="_blank">
                    <xsl:attribute name="href">/xmlHandler?command=user-log&amp;name=<xsl:value-of select="@name"/>&amp;profile=<xsl:value-of select="@profile"/></xsl:attribute>
                    <xsl:value-of select="@user-log"/>
                  </a></td>
                </tr>
              </xsl:for-each>
            </tbody></table>
          </div>
        </fieldset>
      </xsl:if>
    </xsl:for-each>
    <br />
  </xsl:template>

  <xsl:template match="login-failures">
    <strong>Name: </strong><xsl:value-of select="../proxy-status/@name"/><br />
    <strong>State: </strong>up<br />
    <br />
    <strong>Sessions: </strong><a href="javascript:clickSection('sessions');">show current</a><br />
    <br />
    <xsl:for-each select="//login-failures">
      <xsl:if test="count(entry) > 0">
        <fieldset id="disconnectedUsers">
          <legend><strong>Login failures/connection attempts</strong></legend>
          <div class="cwsheader">
            <table border="0" width="99%"><tbody>
              <tr>
                <td><strong>User</strong></td><td><strong>Context</strong></td><td><strong>Count</strong></td><td><strong>Last failure</strong></td><td><strong>IP</strong></td><td><strong>Reason</strong></td>
              </tr>
              <xsl:for-each select="entry">
                <xsl:sort order="descending" select="@last-failure"/>
                <tr>
                  <xsl:if test="position() mod 2 = 1">
                    <xsl:attribute name="bgcolor">#ffffff</xsl:attribute>
                  </xsl:if>
                  <td><xsl:value-of select="@name"/></td>
                  <td><xsl:value-of select="@context"/></td>
                  <td><xsl:value-of select="@failure-count"/></td>
                  <td><xsl:value-of select="@last-failure"/></td>
                  <td><xsl:value-of select="@host"/></td>
                  <td><xsl:value-of select="@reason"/></td>
                </tr>
              </xsl:for-each>
            </tbody></table>
          </div>
        </fieldset>
      </xsl:if>
    </xsl:for-each>
    <br />
  </xsl:template>

  <xsl:template match="proxy-users">
    <strong>Name: </strong><xsl:value-of select="../proxy-status/@name"/><br />
    <strong>State: </strong>up<br />
    <strong>Profiles: </strong><xsl:value-of select="count(../ca-profiles/profile)"/><br />
    <xsl:if test="@count > 1"><strong>Users: </strong><xsl:value-of select="@count"/><br /></xsl:if>
    <strong>Sessions: </strong><xsl:value-of select="../proxy-status/@sessions"/> (active: <xsl:value-of select="../proxy-status/@active-sessions"/>)<br />
    <br />
    <strong>Disconnected users/last seen: </strong><a href="javascript:clickSection('seen');">show log</a><br />
    <strong>Login failures/connect attempts: </strong><a href="javascript:clickSection('failures');">show log</a> (<xsl:value-of
      select="@login-failures"/> entries)<br />
    <br />
    <input type="checkbox" name="hideInactiveCb" id="hideInactiveCb">
      <xsl:if test="@hide-inactive">
        <xsl:attribute name="checked">checked</xsl:attribute>
      </xsl:if>
    </input>
    <label for="hideInactiveCb">Hide idle sessions</label><br />
    <br />
    <div class="cwsheader" style="width: 750px;">
      <table id="userSessions" border="0" width="100%"><tbody>
        <tr>
          <td><strong>User</strong></td><td title="Number of connections/maximum"><strong>#</strong></td><td><strong>IP</strong></td>
          <td><strong>
            <xsl:choose>
              <xsl:when test="@toggle-duration"><a href="#" id="toggleDuration" title="Toggle Connected/Zapped Time">Zapped</a></xsl:when>
              <xsl:otherwise><a href="#" id="toggleDuration" title="Toggle Connected/Zapped Time">Connected</a></xsl:otherwise>
            </xsl:choose>
          </strong></td>
          <td title="ECMs sent by client since connect"><strong>ECM</strong></td><td title="EMMs sent by client since connect"><strong>EMM</strong></td>
          <td title="Interval between ECMs (sliding window average)"><strong>Iv</strong></td><td title="Processing time for last ECM transaction"><strong>Time</strong></td>
          <td title="Proxy flags for last ECM transaction"><strong>Flags</strong></td><td><strong>ClientID</strong></td><td><strong>Service</strong></td>
        </tr>
        <xsl:for-each select="../ca-profiles/profile">
          <xsl:sort select="@name"/>
          <tr>
            <td colspan="11" align="left" bgcolor="#dddddd">
              <div><xsl:value-of select="@name"/> (capacity: <xsl:value-of select="@capacity"/>, mapped-services: <xsl:value-of select="@mapped-services"/>)</div>
            </td>
          </tr>
          <xsl:variable name="profile" select="@name"/>
          <xsl:for-each select="../../proxy-users/user/session[@profile=$profile]">
            <tr>
              <xsl:if test="@active = 'false'">
                <xsl:attribute name="style">font-style: italic</xsl:attribute>
                <xsl:if test="@keepalive-count">
                  <xsl:attribute name="style">font-style: italic; color: blue</xsl:attribute>
                </xsl:if>
              </xsl:if>
              <xsl:if test="position() mod 2 = 0">
                <xsl:attribute name="bgcolor">#ffffff</xsl:attribute>
              </xsl:if>
              <td>&#160;&#160;
                <a target="_blank">
                  <xsl:attribute name="id"><xsl:value-of select="../@name"/></xsl:attribute>
                  <xsl:if test="@active = 'false'">
                    <xsl:attribute name="style">font-style: italic</xsl:attribute>
                  </xsl:if>
                  <xsl:attribute name="href">/xmlHandler?command=proxy-users&amp;name=<xsl:value-of select="../@name"/></xsl:attribute>
                  <xsl:attribute name="title">Show session xml for user</xsl:attribute>
                  <xsl:choose>
                    <xsl:when test="../@display-name">
                      <xsl:value-of select="../@display-name"/>
                    </xsl:when>
                    <xsl:otherwise>
                      <xsl:value-of select="../@name"/>
                    </xsl:otherwise>
                  </xsl:choose>
                </a>
              </td>
              <td><xsl:value-of select="@count"/></td>
              <td><xsl:value-of select="@host"/>
                <xsl:variable name="host" select="@host"/>
                <xsl:variable name="count" select="count(//proxy-users/user/session[@host=$host])"/>
                <xsl:if test="$count > 1">&#160;(<xsl:value-of select="$count"/>)</xsl:if> 
              </td>
              <td>
                <xsl:value-of select="@duration"/>
                <xsl:if test="@last-zap">
                  <xsl:value-of select="@last-zap"/>
                </xsl:if>
              </td>
              <td><a target="_blank">
                <xsl:attribute name="href">/xmlHandler?command=user-log&amp;name=<xsl:value-of select="../@name"/>&amp;profile=<xsl:value-of select="@profile"/></xsl:attribute>
                <xsl:attribute name="title">Show ECM transaction log for user</xsl:attribute>
                <xsl:value-of select="@ecm-count"/>
              </a></td>
              <td>
                <xsl:value-of select="@emm-count"/><xsl:if test="@au"> -&gt; <br /><xsl:value-of select="@au"/></xsl:if>
              </td>
              <td>
                <xsl:choose>
                  <xsl:when test="@avg-ecm-interval &lt; 7 and @avg-ecm-interval &gt; 0">
                    <strong><font color="red"><xsl:value-of select="@avg-ecm-interval"/></font></strong>
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:if test="@avg-ecm-interval &gt; -1">
                      <xsl:value-of select="@avg-ecm-interval"/>
                    </xsl:if>
                  </xsl:otherwise>
                </xsl:choose>
                <xsl:if test="@pending-count &gt; 1">
                  (<strong><font color="red"><xsl:value-of select="@pending-count"/></font></strong>)
                </xsl:if>
              </td>
              <td>
                <xsl:choose>
                  <xsl:when test="@last-transaction &gt; 4500">
                    <strong><font color="red"><xsl:value-of select="@last-transaction"/></font></strong>
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:if test="@last-transaction &gt; -1">
                      <xsl:value-of select="@last-transaction"/>
                    </xsl:if>
                  </xsl:otherwise>
                </xsl:choose>
              </td>
              <td><xsl:value-of select="@flags"/></td>
              <td><xsl:value-of select="@client-id"/></td>
              <td>
                <xsl:value-of select="service/@name"/>
                <xsl:if test="service/@profile">:<xsl:value-of select="service/@profile"/></xsl:if>
              </td>
            </tr>
          </xsl:for-each>
          <tr><td colspan="11">&#160;</td></tr>
        </xsl:for-each>
      </tbody></table>
    </div><br />
    <xsl:call-template name="flag-legend"/>
    <!-- xsl:call-template name="last-seen"/ -->
  </xsl:template>

  <xsl:template match="ctrl-commands">
    <xsl:for-each select="command-group">
      <fieldset>
        <legend><strong><xsl:value-of select="@name"/> commands</strong> (<xsl:value-of select="@handler"/>)</legend>
        <xsl:for-each select="command">
          <form action="">
            <xsl:attribute name="name"><xsl:value-of select="concat(@name, '-form')"/></xsl:attribute>
            <xsl:attribute name="id"><xsl:value-of select="@name"/></xsl:attribute>
            <xsl:value-of select="@label"/>
            <xsl:for-each select="command-param">
              <xsl:if test="@label != '' and position() = 1">&#160;-&#160;</xsl:if>
              <xsl:value-of select="@label"/>:
              <xsl:choose>
                <xsl:when test="@allow-arbitrary = 'true'">
                  <xsl:choose>
                    <xsl:when test="@size">
                      <textarea>
                        <xsl:attribute name="style">vertical-align: middle;</xsl:attribute>
                        <xsl:attribute name="name"><xsl:value-of select="@name"/></xsl:attribute>
                        <xsl:attribute name="rows"><xsl:value-of select="@size"/></xsl:attribute>
                        <xsl:attribute name="cols">45</xsl:attribute>
                        <xsl:value-of select="@label"/>
                      </textarea>&#160;
                    </xsl:when>
                    <xsl:otherwise>
                      <input type="text">
                        <xsl:attribute name="name"><xsl:value-of select="@name"/></xsl:attribute>
                        <xsl:attribute name="value"><xsl:value-of select="@value"/></xsl:attribute>
                      </input>&#160;
                    </xsl:otherwise>
                  </xsl:choose>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:choose>
                    <xsl:when test="@boolean">
                      <input type="checkbox">
                        <xsl:attribute name="name"><xsl:value-of select="@name"/></xsl:attribute>
                      </input>&#160;
                    </xsl:when>
                    <xsl:otherwise>
                      <select>
                        <xsl:attribute name="name"><xsl:value-of select="@name"/></xsl:attribute>
                        <xsl:for-each select="option">
                          <xsl:variable name="listname" select="@value"/>
                          <xsl:choose>
                            <xsl:when test="starts-with(@value, '@')"> <!-- @ indicates named list of options -->
                              <xsl:for-each select="//ctrl-commands/option-list[@name=$listname]/option">
                                <option>
                                  <xsl:if test="position() = 1">
                                    <xsl:attribute name="selected">selected</xsl:attribute>
                                  </xsl:if>
                                  <xsl:attribute name="value"><xsl:value-of select="@value"/></xsl:attribute>
                                  <xsl:value-of select="@value"/>
                                </option>
                              </xsl:for-each>
                            </xsl:when>
                            <xsl:otherwise> <!-- no named list of options -->
                              <option>
                                <xsl:if test="position() = 1">
                                  <xsl:attribute name="selected">selected</xsl:attribute>
                                </xsl:if>
                                <xsl:attribute name="value"><xsl:value-of select="@value"/></xsl:attribute>
                                <xsl:value-of select="@value"/>
                              </option>                              
                            </xsl:otherwise>
                          </xsl:choose>

                        </xsl:for-each>
                      </select>&#160;
                    </xsl:otherwise>
                  </xsl:choose>
                </xsl:otherwise>
              </xsl:choose>
            </xsl:for-each>
            <xsl:if test="@confirm = 'true'">
              <input type="hidden" name="confirm" value="true"/>
            </xsl:if>
            &#160; <input value="OK" type="submit"/><br />
            &#8226; <xsl:value-of select="@description"/>
          </form>
        </xsl:for-each>
      </fieldset><br />
      <br />
    </xsl:for-each>
  </xsl:template>

  <!-- common named templates for elements that appear in the root but should only be rendered where explicitly called -->

  <xsl:template name="proxy-status">
    <xsl:for-each select="//proxy-status">
      <strong>Name: </strong><xsl:value-of select="@name"/><br />
      <strong>State: </strong>up<br />
      <strong>Started: </strong><xsl:value-of select="@started"/><br />
      <strong>Uptime: </strong><xsl:value-of select="@duration"/><br />
      <strong>Connectors: </strong><xsl:value-of select="@connectors"/><br />
      <strong>Sessions: </strong><xsl:value-of select="@sessions"/> (active: <xsl:value-of select="@active-sessions"/>)<br />
      <strong>Estimated total capacity: </strong><xsl:value-of select="@capacity"/> (ECM-&gt;CW transactions per CW-validity-period)<br />
      <strong>Estimated total load: </strong><xsl:value-of select="sum(//connector/@ecm-load)"/> (forwards during the last period)<br />
      <strong>ECM total: </strong><xsl:value-of select="@ecm-count"/> (average rate: <xsl:value-of select="@ecm-rate"/>/s)<br />
      <strong>ECM forwards: </strong><xsl:value-of select="@ecm-forwards"/>&#160;
      <xsl:if test="@ecm-count &gt; 0">(<xsl:value-of select="format-number(@ecm-forwards div @ecm-count, '##.0%')"/>)</xsl:if><br />
      <strong>ECM cache hits: </strong><xsl:value-of select="@ecm-cache-hits"/>&#160;
      <xsl:if test="@ecm-count &gt; 0">(<xsl:value-of select="format-number(@ecm-cache-hits div @ecm-count, '##.0%')"/>)</xsl:if><br />
      <strong>ECM denied: </strong><xsl:value-of select="@ecm-denied"/>&#160;
      <xsl:if test="@ecm-denied &gt; 0">(<xsl:value-of select="format-number(@ecm-denied div @ecm-count, '##.0%')"/>)</xsl:if><br />
      <strong>ECM filtered: </strong><xsl:value-of select="@ecm-filtered"/>&#160;
      <xsl:if test="@ecm-filtered &gt; 0">(<xsl:value-of select="format-number(@ecm-filtered div @ecm-count, '##.0%')"/>)</xsl:if><br />
      <strong>ECM failures: </strong><xsl:value-of select="@ecm-failures"/><br />
      <strong>EMM total: </strong><xsl:value-of select="@emm-count"/><br />
    </xsl:for-each>
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
        <div style="position: absolute; top: 30px; left: 120px;"><strong><xsl:value-of select="@time"></xsl:value-of></strong></div>
        <div id="autoPollDiv" style="position: absolute; top: 26px; left: 660px;">
          <input type="checkbox" name="autoPollCb" id="autoPollCb" checked="checked"/>
          <label for="autoPollCb">Auto polling</label>
        </div>
      </xsl:for-each>
    </span>
  </xsl:template>

  <xsl:template name="cache-status">
    <xsl:for-each select="//cache-status">
      <div class="cwsheader"><strong>Cache-Handler: </strong>
        <a id="openhref">
          <xsl:attribute name="href"><xsl:value-of select="@type"/></xsl:attribute>
          <xsl:value-of select="@type"/>
        </a><br />
        <div class="cwscontent">
          <xsl:attribute name="style">display: <xsl:value-of select="@display"/>;</xsl:attribute>
          <xsl:attribute name="id"><xsl:value-of select="concat('toggle-', @type)"/></xsl:attribute>
          <xsl:for-each select="cache-param">
            <strong><xsl:value-of select="@name"/>: </strong> <xsl:value-of select="@value"/><br />
          </xsl:for-each>
        </div>
      </div>
    </xsl:for-each>
  </xsl:template>

  <xsl:template name="ca-profiles">
    <xsl:for-each select="//ca-profiles">
      <fieldset>
        <legend><strong>Profiles (<xsl:value-of select="count(profile)"/>)</strong></legend>
        <xsl:for-each select="profile">
          <xsl:sort select="@name"/>
          <xsl:variable name="profile" select="@name"/>
          <div class="cwsheader">

            <table border="0" width="98%" cellspacing="0" cellpadding="0"><tr>
              <td width="30%"><strong>CA-Profile: </strong>
                <a id="openhref">
                  <xsl:attribute name="href">profile<xsl:value-of select="@name"/></xsl:attribute>
                  <xsl:value-of select="@name"/>
                  <xsl:choose>
                    <xsl:when test="@network-id"> (<xsl:value-of select="@network-id"/>)</xsl:when>
                    <xsl:otherwise> (ALL)</xsl:otherwise>
                  </xsl:choose>
                </a><br /></td>
              <td width="25%">
                <xsl:if test="@ca-id"><strong>CA-Id: </strong><xsl:value-of select="@ca-id"/></xsl:if>
                <xsl:if test="not(@network-id)">
                  <div style="position: relative; left: -70px">(multi-context connectors/sessions)</div>
                </xsl:if>
              </td>
              <td width="15%"><strong>Sessions: </strong><xsl:value-of select="@sessions"/></td>
              <td width="19%"><strong>
                <xsl:choose>
                  <xsl:when test="@network-id">Local services: </xsl:when>
                  <xsl:otherwise>Services: </xsl:otherwise>
                </xsl:choose>
                </strong><xsl:value-of select="@mapped-services"/>
              </td>
              <td width="1%">&#160;</td>
              <td width="20%" align="right">&#160;(<xsl:value-of select="sum(//connector[@profile=$profile]/@ecm-load)"/>)

              </td>
            </tr>
            </table>

            <div class="cwscontent">
              <xsl:attribute name="style">display: <xsl:value-of select="@display"/>;</xsl:attribute>
              <xsl:attribute name="id"><xsl:value-of select="concat('toggle-profile', @name)"/></xsl:attribute>
              <xsl:if test="@network-id"><strong>Network-id: </strong> <xsl:value-of select="@network-id"/><br /></xsl:if>
              <xsl:if test="@ca-id"><strong>Ca-id: </strong> <xsl:value-of select="@ca-id"/><br /></xsl:if>
              <xsl:if test="@provider-idents"><strong>Provider-idents: </strong> <xsl:value-of select="@provider-idents"/><br /></xsl:if>
              <xsl:if test="@provider-match"><strong>Require-provider-match: </strong> <xsl:value-of select="@provider-match"/>
                <xsl:if test="@provider-match='true'"> (provider-ident in requests must exist on connector for forwards to proceed)</xsl:if>
                <xsl:if test="@provider-match='false'"> (provider-ident in requests not considered when forwarding)</xsl:if>
                <br />
              </xsl:if>
              <strong>Debug: </strong> <xsl:value-of select="@debug"/>
              <xsl:if test="@debug='true'"> (backlog of 100 transactions kept for every session - memory intensive)</xsl:if>
              <xsl:if test="@debug='false'"> (no transaction backlogs kept)</xsl:if>
              <br />
              <strong>Estimated capacity: </strong> <xsl:value-of select="@capacity"/><br />
              <strong>Estimated load: </strong><xsl:value-of select="sum(//connector[@profile=$profile]/@ecm-load)"/><br />
              <strong>
                <xsl:choose>
                  <xsl:when test="@network-id">Locally mapped services: </xsl:when>
                  <xsl:otherwise>Services: </xsl:otherwise>
                </xsl:choose>
              </strong><xsl:value-of select="@mapped-services"/>
              <xsl:if test="@parsed-services"> (<xsl:value-of select="@parsed-services"/> parsed from services file, with <xsl:value-of select="@parsed-conflicts"/> conflicts)</xsl:if>
              <br />
              <xsl:if test="@reset-services"><strong>Reset services: </strong><xsl:value-of select="@reset-services"/><br /></xsl:if>
              <xsl:if test="@blocked-services"><strong>Blocked services: </strong><xsl:value-of select="@blocked-services"/><br /></xsl:if>
              <xsl:if test="@allowed-services"><strong>Allowed services: </strong><xsl:value-of select="@allowed-services"/><br /></xsl:if>

              <strong>Max-cw-wait: </strong> <xsl:value-of select="@max-cw-wait"/> ms
              <xsl:if test="@congestion-limit"> (congestion-limit at <xsl:value-of select="@congestion-limit"/> ms)</xsl:if>
              <br />
              <strong>Max-cache-wait: </strong> <xsl:value-of select="@max-cache-wait"/> ms<br />

              <xsl:if test="count(listen-port) > 0">
              <br /><strong>Listen-ports: </strong>
              <ul>
                <xsl:for-each select="listen-port"><li>
                  <span>
                    <xsl:if test="@alive = 'false'">
                      <xsl:attribute name="style">text-decoration: line-through; font-weight: bold</xsl:attribute>
                    </xsl:if>
                    <xsl:value-of select="@name"/>&#160;
                  </span>
                  <xsl:value-of select="@properties"/>

                </li></xsl:for-each>
              </ul>
              </xsl:if>

            </div>
          </div>
        </xsl:for-each>
      </fieldset><br />
    </xsl:for-each>
  </xsl:template>

  <xsl:template name="proxy-plugins">
    <xsl:for-each select="//proxy-plugins">
      <xsl:if test="count(plugin) > 0">
        <br /><fieldset>
        <legend><strong>Plugins (<xsl:value-of select="count(plugin)"/>)</strong></legend>
        <xsl:for-each select="plugin">
          <xsl:sort select="@name"/>
          <div class="cwsheader">

            <table border="0" width="98%" cellspacing="0" cellpadding="0"><tr>
              <td width="25%"><strong>Plugin: </strong>
                <xsl:choose>
                  <xsl:when test="count(plugin-param) > 0">
                    <a id="openhref">
                      <xsl:attribute name="href">plugin<xsl:value-of select="@name"/></xsl:attribute>
                      <xsl:value-of select="@name"/>
                    </a>
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:value-of select="@name"/>
                  </xsl:otherwise>
                </xsl:choose>
                <br />
              </td>
              <td width="75%"><xsl:value-of select="@description"/></td>
            </tr>
            </table>

            <div class="cwscontent">
              <xsl:attribute name="style">display: <xsl:value-of select="@display"/>;</xsl:attribute>
              <xsl:attribute name="id"><xsl:value-of select="concat('toggle-plugin', @name)"/></xsl:attribute>

              <xsl:if test="count(plugin-param) > 0">
                <xsl:for-each select="plugin-param">
                  <strong><xsl:value-of select="@name"/>: </strong> <xsl:value-of select="@value"/><br />
                </xsl:for-each>
              </xsl:if>
              &#160;

            </div>
          </div>
        </xsl:for-each>
        </fieldset><br />
      </xsl:if>
    </xsl:for-each>
  </xsl:template>

  <xsl:template name="flag-legend">
    <fieldset>
      <legend><strong>Transaction Flags</strong></legend>
      <table class="error-log">
        <tbody>
          <tr><td>
            Normal traffic flags: <br />
            <strong>F</strong> = Normal forward to CWS<br />
            <strong>C</strong> = Cache hit (local)<br />
            <strong>R</strong> = Cache hit (received from remote cache)<br />
            <strong>I</strong> = Instant cache hit (no waiting at all in cache, both request and reply already available)<br />
            <strong>L</strong> = Linked cache hit (CW from a different service was used, via sid linking)<br />
            <strong>1</strong> = This was the first transaction performed by a new session.<br />
            <strong>Z</strong> = SID changed (compared to previous transaction = user zap, can also indicate multiple tuners or users in one session if it occurs every time)<br />

            <br />Service mapper flags: <br />
            <strong>N</strong> = Cannot decode (service mapper says service not on any card, or service blocked)<br />
            <strong>P</strong> = Service mapper didn't know status for this SID on one or more cards (may have triggered probing if accepted by connectors)<br />
            <strong>2</strong> = Triggered broadcast to additional cards besides the selected one (broadcast-missing-sid or redundant-forwarding in use)<br />
            <strong>+</strong> = Caused an addition to the service map (found service, after probing/successful decode)<br />
            <strong>-</strong> = Caused a removal from the service map (lost service, after repeated failed decodes)<br />

            <br />Flags indicating possible problems/recovery situations: <br />
            <strong>B</strong> = Blocked by exceeded limits or by filters (plugins)<br />
            <strong>M</strong> = Ca-id mismatch. The ecm reply didn't have the same ca-id as the request, indicates some clients are sending ecms to the wrong ports.<br />
            <strong>E</strong> = Client got an empty reply (cannot-decode received from CWS, or from the proxy itself with situation <strong>N</strong>)<br />
            <strong>Y</strong> = Forward retry performed (first chosen CWS disconnected during the transaction, but alternatives exist)<br />
            <strong>A</strong> = Abort when forwarding (CWS connection was closed before forward, because of other request timeouts or by the network/server).<br />
            <strong>T</strong> = Timeout when forwarding (no response from CWS within time limit, i.e max-cw-wait)<br />
            <strong>O</strong> = Timeout while waiting in cache (waited for max-cache-wait, but no reply was reported to the cache).<br />
            <strong>Q</strong> = Abort while waiting in cache (the forward the cache was waiting for failed, either locally or remotely).<br />
            <strong>G</strong> = Congestion when forwarding (time was > max-cw-wait/2, but forward still performed)<br />
            <strong>X</strong> = Cache hit after failed forward (situation <strong>Y</strong>, but reply became available in cache so no need for new forward)<br />

            <br />Internal/debugging flags: <br />
            <strong>S</strong> = Timeout in send queue (when trying to forward to connector, should normally not occur)<br />
            <strong>W</strong> = Triggered cannot-decode-wait (would have been situation <strong>N</strong>, but waiting for remote cache paid off)<br />
            <strong>H</strong> = Caused an internal failed rechecking of the cache, represents an attempt to recover from an immediately preceeding problem.<br />
            <strong>U</strong> = Interrupt in the no-sid-delay sleep, a request without sid was delayed but disconnected during the delay.<br />
            <strong>D</strong> = The user session disconnected before it could receive the reply (likely reached the client ecm timeout)<br />
          </td></tr>
        </tbody>
      </table>
    </fieldset><br /><br />
  </xsl:template>

  <xsl:template name="profile-filter">
    <xsl:for-each select="//ca-profiles">
      <xsl:if test="count(profile) > 1">
        <strong>Profile filter: </strong>
        <select name="profileFilter" id="profileFilter">
          <option>
            <xsl:attribute name="value">ALL</xsl:attribute>
            ALL
          </option>
          <xsl:for-each select="profile">
            <xsl:if test="@name != '*'">
              <option>
                <xsl:attribute name="value"><xsl:value-of select="@name"/></xsl:attribute>
                <xsl:value-of select="@name"/>
              </option>
            </xsl:if>
          </xsl:for-each>
        </select>
        <br /><br />
      </xsl:if>
    </xsl:for-each>
  </xsl:template>

  <xsl:template name="user-warning-entry">
    <xsl:param name="indent"/>
    <xsl:value-of select="$indent"/><xsl:text> </xsl:text><xsl:value-of select="@timestamp"/> -
    <strong> <xsl:value-of select="@name"/> </strong>
    <xsl:text> </xsl:text><xsl:value-of select="@session-id"/> -
    <xsl:text> </xsl:text><xsl:value-of select="@service-name"/> -
    <strong> <xsl:value-of select="@time"/> ms</strong> -
    <strong> "<xsl:value-of select="@flags"/>" </strong>
    <xsl:if test="@count"> - (<xsl:value-of select="@count"/> occurances)</xsl:if>
    <br />
    <xsl:if test="@time-cache and @time &gt; 5000">
      <xsl:value-of select="$indent"/>&#160; - Time breakdown - Cache wait: <strong><xsl:value-of select="@time-cache"/> ms </strong>
      Send queue: <strong><xsl:value-of select="@time-queue"/> ms </strong>
      Server wait: <strong><xsl:value-of select="@time-cws"/> ms </strong>
      <xsl:if test="@cws-name"> (<xsl:value-of select="@cws-name"/>) </xsl:if>
      Client writeback: <strong><xsl:value-of select="@time-client"/> ms </strong>
      <br />
    </xsl:if>
  </xsl:template>



</xsl:stylesheet>

