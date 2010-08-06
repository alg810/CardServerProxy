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

  <xsl:template match="boxes">
    <strong>Name: </strong><xsl:value-of select="../proxy-status/@name"/> (DreamboxPlugin)<br />
    <strong>Httpd-port: </strong><xsl:value-of select="@httpd-port"/><br />
    <xsl:if test="@sshd-port"><strong>Sshd-port: </strong><xsl:value-of select="@sshd-port"/><br /></xsl:if>
    <xsl:if test="@count > 0">
      <strong>Boxes: </strong><xsl:value-of select="@count"/> (active: <xsl:value-of select="@active"/>)<br />
      <br />
      <div style="width: 750px;">
      <input type="checkbox" name="hideInactiveCb" id="hideInactiveCb">
        <xsl:if test="@hide-inactive">
          <xsl:attribute name="checked">checked</xsl:attribute>
        </xsl:if>
      </input>
      <label for="hideInactiveCb">Hide idle/old</label>
      <span style="float: right">
        Invert box selection: <input type="button" id="invertSelection" value="OK"/>
      </span>
      </div>
      <br />
      <div class="cwsheader" style="width: 750px;">
        <table id="boxes" border="0" width="100%"><tbody>
          <tr>
            <td><strong>User</strong></td><td><strong>Type</strong></td><td><strong>Img</strong></td><td><strong>Ext. IP</strong></td><td><strong>Int. IP</strong></td><td><strong>HWAddr</strong></td><td><strong>Iv</strong></td><td><strong>Next</strong></td><td><strong>SID</strong></td><td><strong>ONID</strong></td><td><strong>Status</strong></td><td><strong>C</strong></td><xsl:if test="../@admin = 'true'"><td><strong>&#160;</strong></td></xsl:if>
          </tr>
          <xsl:for-each select="box">
            <tr>
              <xsl:if test="@active = 'false'">
                <xsl:attribute name="style">font-style: italic</xsl:attribute>
              </xsl:if>
              <xsl:if test="position() mod 2 = 1">
                <xsl:attribute name="bgcolor">#ffffff</xsl:attribute>
              </xsl:if>
              <td>
                <a target="_blank">
                  <xsl:attribute name="id"><xsl:value-of select="@user"/></xsl:attribute>
                  <xsl:if test="@active = 'false'">
                    <xsl:attribute name="style">font-style: italic</xsl:attribute>
                  </xsl:if>
                  <xsl:attribute name="href">/xmlHandler?command=proxy-users&amp;name=<xsl:value-of select="@user"/></xsl:attribute>
                  <xsl:value-of select="@user"/>
                </a>
              </td>
              <td>
                <a>
                  <xsl:attribute name="href">javascript:selectBox('<xsl:value-of select="@id"/>');</xsl:attribute>
                  <xsl:value-of select="@type"/>
                </a>
              </td>
              <td><xsl:value-of select="@image-guess"/></td>
              <td><xsl:value-of select="@external-ip"/></td>
              <td><xsl:value-of select="@local-ip"/></td>
              <td><xsl:value-of select="@mac"/></td>
              <td><xsl:value-of select="@interval"/></td>
              <td><xsl:value-of select="@next-checkin"/></td>
              <td><xsl:value-of select="@sid"/></td>
              <td><xsl:value-of select="@onid"/></td>
              <td>
                <xsl:choose>
                  <xsl:when test="@pending-operation"><xsl:value-of select="@pending-operation"/></xsl:when>
                  <xsl:otherwise>
                    <xsl:if test="@agent-version">v<xsl:value-of select="@agent-version"/></xsl:if>
                    <xsl:if test="@tunnel-port">&#160;(<xsl:value-of select="@tunnel-port"/>)</xsl:if>                    
                  </xsl:otherwise>
                </xsl:choose>
              </td>
              <td>
                <xsl:if test="@operation-count">
                  <a>
                    <xsl:attribute name="href">javascript:selectBox('<xsl:value-of select="@id"/>');</xsl:attribute>
                    <xsl:value-of select="@operation-count"/>
                    <xsl:if test="@running-operations">
                      &#160;(!)
                    </xsl:if>
                  </a>
                </xsl:if>
              </td>
              <xsl:if test="../@admin = 'true'">
                <td>
                  <input type="checkbox">
                    <xsl:attribute name="id"><xsl:value-of select="@id"/></xsl:attribute>
                    <xsl:if test="@checked"><xsl:attribute name="checked">checked</xsl:attribute></xsl:if>
                  </input>
                </td>
              </xsl:if>
            </tr>
          </xsl:for-each>
          <tr><td colspan="9">&#160;</td></tr>
        </tbody></table>
      </div><br />

      <xsl:if test="@admin = 'true'">
        <xsl:call-template name="task-form"/>
      </xsl:if>

    </xsl:if>
    <xsl:call-template name="agent-instructions"/>
  </xsl:template>

  <xsl:template match="box-details">
    <strong>Box details: </strong>
    <a target="_blank">
      <xsl:attribute name="href">/xmlHandler?command=box-details&amp;id=<xsl:value-of select="@id"/></xsl:attribute>
      show xml
    </a>&#160;
    <a href="javascript:clickSection('maintenance');">show all</a>
    <br /><br />
    <strong>User: </strong><xsl:value-of select="@user"/><br />
    <strong>HWAddr: </strong><xsl:value-of select="@mac"/><br />
    <strong>Created: </strong><xsl:value-of select="@created"/><br />
    <strong>Last checkin: </strong><xsl:value-of select="@last-checkin"/><br />
    <xsl:if test="@tunnel-port"><form action="" id="close-tunnel">
      <strong>Ssh tunnel-port: </strong><xsl:value-of select="@tunnel-port"/>&#160;
      <input name="id" type="hidden"><xsl:attribute name="value"><xsl:value-of select="@id"/></xsl:attribute></input>
      <input name="confirm" type="hidden" value="true"/>
      <input type="submit" value="Close"/></form>
      <br /></xsl:if>
    <br />

    <div class="cwsheader"><strong>Box-Properties: </strong>
      <a id="openhref">
        <xsl:attribute name="href"><xsl:value-of select="@id"/></xsl:attribute>
        <xsl:value-of select="@id"/>
      </a><br />
      <div class="cwscontent">
        <xsl:attribute name="style">display: <xsl:value-of select="@display"/>;</xsl:attribute>
        <xsl:attribute name="id"><xsl:value-of select="concat('toggle-', @id)"/></xsl:attribute>
        <xsl:for-each select="properties/property">
          <strong><xsl:value-of select="@name"/>: </strong> <xsl:value-of select="@value"/><br />
        </xsl:for-each>
      </div>
    </div>
    <br />

    <xsl:if test="count(operations/op) > 0">
      <fieldset>
        <legend><strong>Operations (<xsl:value-of select="operations/@count"/>)</strong></legend>
        <xsl:for-each select="operations/op">
          <div class="cwsheader"><form action="" id="abort-operation" style="margin: 0px; padding: 0px; display: inline;"><strong>Command:</strong>&#160;
            <a id="openhref">
              <xsl:attribute name="href"><xsl:value-of select="@id"/></xsl:attribute>
              <xsl:value-of select="@text"/>
            </a>&#160;
            <strong>Started: </strong>&#160;<xsl:value-of select="@start"/>&#160;
            <xsl:choose>
              <xsl:when test="@stop">
                <strong>Ended: </strong>&#160;<xsl:value-of select="@stop"/>&#160;
              </xsl:when>
              <xsl:otherwise>
                <xsl:if test="@start">
                  <input name="id" type="hidden"><xsl:attribute name="value"><xsl:value-of select="//@id"/></xsl:attribute></input>
                  <input name="op" type="hidden"><xsl:attribute name="value"><xsl:value-of select="@id"/></xsl:attribute></input>
                  <input name="confirm" type="hidden" value="true"/>
                  <input type="submit" value="Abort"/>
                </xsl:if>
              </xsl:otherwise>
            </xsl:choose>
            </form>
            <br />
            <div class="cwscontent">
              <xsl:attribute name="style">display: <xsl:value-of select="@display"/>;</xsl:attribute>
              <xsl:attribute name="id"><xsl:value-of select="concat('toggle-', @id)"/></xsl:attribute>
              <strong>Output:</strong>
              <pre><xsl:value-of select="output" disable-output-escaping="yes"/></pre>
            </div>
          </div>
        </xsl:for-each>
      </fieldset>
    </xsl:if>

  </xsl:template>

  <xsl:template name="task-form">
    <table border="0">
      <tr><td>
        Run script on selected boxes:
      </td><td>
        <select name="script" id="scriptSelector">
          <xsl:for-each select="//option-list[@name='@scripts']/option">
            <option>
              <xsl:if test="position() = 1">
                <xsl:attribute name="selected">selected</xsl:attribute>
              </xsl:if>
              <xsl:attribute name="value"><xsl:value-of select="@value"/></xsl:attribute>
              <xsl:value-of select="@value"/>
            </option>
          </xsl:for-each>
        </select>
      </td><td>
        Optional parameters:
      </td><td>
        <input name="params" id="paramsInput" type="text"/>
      </td><td>
        <input value="OK" id="scriptBtn" type="button"/>
      </td></tr>
      <tr><td>
        Run command on selected boxes:
      </td><td>
        <input name="cmdline" id="cmdlineInput" type="text"/>
      </td><td>
        <input value="OK" id="cmdlineBtn" type="button"/>
      </td><td colspan="2">
        &#160;
      </td></tr>
      <tr><td>
        Abort all pending operations:
      </td><td>
        <input value="OK" id="abortBtn" type="button"/>
      </td><td colspan="3">
        &#160;
      </td></tr>
      <tr><td>
        Clear all operation history:
      </td><td>
        <input value="OK" id="clearBtn" type="button"/>
      </td><td colspan="3">
        &#160;
      </td></tr>
    </table>
    <br />

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
          <option>
            <xsl:attribute name="value"><xsl:value-of select="@name"/></xsl:attribute>
            <xsl:value-of select="@name"/>
          </option>
        </xsl:for-each>
      </select>
      <br /><br />
    </xsl:if>
    </xsl:for-each>
  </xsl:template>

  <xsl:template name="agent-instructions">
    <br />
    <fieldset>
      <legend><strong>CSP Agent install instructions (Dreambox)</strong></legend>
      <table class="error-log">
      <tbody>
        <tr><td>
          The csp agent is an ash-script that runs in the bakgrund and allows remote monitoring and management from the proxy. To obtain a preconfigured installer, follow these steps: <br /><br />            
          <strong>1. </strong>Use ssh or telnet to login to your box(es), dreambox default credentials are: <strong>root/dreambox</strong>. If this doesn't work, try an empty password (telnet only). <br /><br />
          <strong>2. </strong>To download the installer, do the following:<br />
          <strong>cd /tmp</strong><br />
          <strong>wget http://<xsl:value-of select="//installer/@user"/>:YOURPASSWORD@<xsl:value-of select="//installer/@host"/>:<xsl:value-of select="//installer/@port"/><xsl:value-of select="//installer/@path"/></strong><br /><br />
          <strong>3. </strong>The installer will be downloaded (it is customized for your user). To run the installer, do:<br />
          <strong>chmod +x installer.sh</strong><br />
          <strong>./installer.sh</strong><br /><br />
          <strong>4.</strong> Answer y to continue installation. The csp-agent script will be downloaded and installed in /var/bin (setup to auto start on boot).<br />
        </td></tr>
      </tbody>
      </table>
    </fieldset><br /><br />
  </xsl:template>

</xsl:stylesheet>

