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

  <xsl:template match="cache-contents">
    <strong>Name: </strong><xsl:value-of select="../proxy-status/@name"/> (CacheCoveragePlugin)<br />
    <strong>Cache contexts: </strong><xsl:value-of select="@contexts"/><br />
      <br />
      <xsl:call-template name="cache-status"/>
      <br />
    <strong>Sources: </strong><xsl:value-of select="@sources"/><br />
    <br />
    <xsl:call-template name="cache-sources"/>
    <br />
    <xsl:if test="count(//service) > 0">
      <strong>Services: </strong><xsl:value-of select="count(//service)"/><br />
      <br />
      <div style="width: 750px;">
      <input type="checkbox" name="hideExpiredCb" id="hideExpiredCb">
        <xsl:if test="@hide-expired">
          <xsl:attribute name="checked">checked</xsl:attribute>
        </xsl:if>
      </input>
      <label for="hideExpiredCb">Hide expired entries</label>
      </div>
      <br />
      <div class="cwsheader" style="width: 750px;">
        <table id="services" border="0" width="100%"><tbody>
        <tr>
          <td>&#160;&#160;<strong>Service name</strong></td><td title="Service id (from cache metadata)"><strong>SID</strong></td>
          <td title="Transport stream id (assumed, from services file)"><strong>TID</strong></td>
          <td title="Multiple (number of detected continuities)"><strong>#</strong></td>
          <td title="Update count"><strong>Uc</strong></td><td title="Current age"><strong>Age</strong></td>
          <td title="Average interval (measured)"><strong>Iv</strong></td><td title="Average variance (from expected interval)"><strong>Var</strong></td>
          <td title="Continuity errors (gaps)"><strong>Ce</strong></td><td title="Total continuity errors"><strong>CeT</strong></td>
          <td title="Overwrites (with different dcw)"><strong>Ow</strong></td><td title="Duplicates"><strong>Du</strong></td>
          <td title="Aborted requests (cancelled locks)"><strong>Ab</strong></td>
          <td title="Time offset (from earliest dcw in window)"><strong>Offs</strong></td>
          <td title="Number of sources (total seen)"><strong>S#</strong></td>
        </tr>
        <xsl:for-each select="cache-context">
          <xsl:sort select="@key"/>
          <tr>
            <td colspan="15" align="left" bgcolor="#dddddd">
              <div><xsl:value-of select="@key"/>
                (<xsl:if test="@local-name">local-name: <strong><xsl:value-of select="@local-name"/></strong>, </xsl:if>
                expected-interval: <xsl:value-of select="@expected-interval"/>,
                total-seen: <xsl:value-of select="@total-seen"/>, current-count: <xsl:value-of select="count(service)"/>)</div>
            </td>
          </tr>
          <xsl:for-each select="service">
            <tr>
              <xsl:if test="@expired = 'true'">
                <xsl:attribute name="style">font-style: italic</xsl:attribute>
              </xsl:if>
              <xsl:if test="position() mod 2 = 0">
                <xsl:attribute name="bgcolor">#ffffff</xsl:attribute>
              </xsl:if>
              <td>&#160;&#160;
                <a target="_blank">
                  <xsl:attribute name="href">/xmlHandler?command=service-backlog&amp;sid=<xsl:value-of select="@id"/>&amp;onid=<xsl:value-of select="../@onid"/>&amp;caid=<xsl:value-of select="../@caid"/></xsl:attribute>
                  <xsl:attribute name="title">Show service transaction backlog</xsl:attribute>
                <xsl:value-of select="@name"/>
                </a>
              </td>
              <td><xsl:value-of select="@id"/></td>
              <td><xsl:if test="@tid"><xsl:value-of select="@tid"/></xsl:if></td>
              <td>
                <xsl:choose>
                  <xsl:when test="@multiple &gt; 0">
                    <strong><font color="blue"><xsl:value-of select="@multiple"/></font></strong>
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:value-of select="@multiple"/>
                  </xsl:otherwise>
                </xsl:choose>
              </td>
              <td><xsl:value-of select="@update-count"/></td>
              <td><xsl:value-of select="@age"/></td>
              <td><xsl:value-of select="@avg-interval"/></td>
              <td><xsl:value-of select="@avg-variance"/></td>
              <td>
                <xsl:choose>
                  <xsl:when test="@continuity-errors &gt; 0">
                    <strong><font color="red"><xsl:value-of select="@continuity-errors"/></font></strong>
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:value-of select="@continuity-errors"/>
                  </xsl:otherwise>
                </xsl:choose>
              </td>
              <td><xsl:value-of select="@total-continuity-errors"/></td>
              <td>
                <xsl:choose>
                  <xsl:when test="@overwrites &gt; 0">
                    <strong><font color="red"><xsl:value-of select="@overwrites"/></font></strong>
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:if test="@overwrites">
                      <xsl:value-of select="@overwrites"/>
                    </xsl:if>
                  </xsl:otherwise>
                </xsl:choose>
              </td>
              <td><xsl:if test="@duplicates"><xsl:value-of select="@duplicates"/></xsl:if></td>
              <td><xsl:if test="@aborts"><xsl:value-of select="@aborts"/></xsl:if></td>
              <td><xsl:value-of select="@offset"/></td>
              <td><xsl:value-of select="@sources"/></td>
            </tr>
          </xsl:for-each>
          <tr><td colspan="15">&#160;</td></tr>
        </xsl:for-each>
      </tbody></table>
      </div><br />
    </xsl:if>
  </xsl:template>

  <xsl:template name="cache-sources">
    <div style="width: 550px;">
      <input type="checkbox" name="hideLocalCb" id="hideLocalCb">
        <xsl:if test="//cache-sources/@hide-local">
          <xsl:attribute name="checked">checked</xsl:attribute>
        </xsl:if>
      </input>
      <label for="hideLocalCb">Hide local sources</label>
    </div>
    <br />
    <div class="cwsheader" style="width: 550px;">
      <table id="sources" border="0" width="100%"><tbody>
        <tr>
          <td>&#160;&#160;<strong>Source name</strong></td><td><strong>Label</strong></td>
          <td title="Update count"><strong>Uc</strong></td>
          <td title="Overwrites (with different dcw)"><strong>Ow</strong></td>
          <td title="Duplicates"><strong>Du</strong></td>
          <td title="Aborted requests (cancelled locks)"><strong>Ab</strong></td>
        </tr>
        <xsl:for-each select="//cache-sources/source">
          <xsl:sort select="@update-count" data-type="number" order="descending"/>
          <tr>
            <xsl:if test="position() mod 2 = 0">
              <xsl:attribute name="bgcolor">#ffffff</xsl:attribute>
            </xsl:if>
            <td>&#160;&#160;<xsl:value-of select="@name"/></td>
            <td><xsl:value-of select="@label"/></td>
            <td><xsl:value-of select="@update-count"/></td>
            <td>
              <xsl:choose>
                <xsl:when test="@overwrites &gt; 0">
                  <strong><font color="red"><xsl:value-of select="@overwrites"/></font></strong>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:if test="@overwrites">
                    <xsl:value-of select="@overwrites"/>
                  </xsl:if>
                </xsl:otherwise>
              </xsl:choose>
            </td>
            <td><xsl:if test="@duplicates"><xsl:value-of select="@duplicates"/></xsl:if></td>
            <td><xsl:if test="@aborts"><xsl:value-of select="@aborts"/></xsl:if></td>
          </tr>
        </xsl:for-each>
      </tbody></table>
    </div>
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

</xsl:stylesheet>

