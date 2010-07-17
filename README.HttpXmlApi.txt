CSP HTTP/XML API
----------------

The proxy has a simple query api that allows remote monitoring and some limited control/administration features.
To use this, rmi and status-web need to be enabled in proxy.xml (see proxy-reference.html).

There are two forms of commands: status and control. Each can be executed either via HTTP post containing an xml
query or by HTTP get, specifying the command and parameters on the query string. Both methods will produce the
same response xml from the proxy, but only post allows multiple commands to be included in one request.

These features are designed to be VERY simple to modify and extend, and they're to be considered a default example.
One way to make use of this api using only a web browser is included in the form of the status-web (in cs-status.war).
This shows an entirely client-side way of building a simple gui on the fly using xslt, js and css (see end of file).


Available status commands (as of 0.9.0):
----------------------------------------
- proxy-status: general usage stats
    optional parameters: profile (show info for this profile only)*
    
- ca-profiles: list of all defined profiles, and their listen ports
    optional parameters: name (name of the profile to retrieve, omit for all)*
    
- cws-connectors: list of all defined connectors and current usage stats for each
    optional parameters: name (name of the connector to retrieve, omit for all)*
                         profile (list only connectors within specified profile)
    
- proxy-users: list of all active user sessions, and their current status
    optional parameters: name (user name to retrieve, omit for all)** 
                         profile (list only sessions within specified profile)
    
- cache-status: usage stats from the current cache implementation 
    optional parameters: none
    
- error-log: the last 40 (by default) events of general interest
    optional parameters: profile (list only events related to specified profile)

- user-log: the last 100 transactions results for a specific user (requires debug="true" for the profile)
    optional parameters: name (list transactions for named user instead of calling user, admin only)

- user-warning-log: the last 40 transactions with attached warnings (aggregated if the same warning occurs often)**
    optional parameters: profile (list only events related to the specified profile)*
    
- all-services: all services found on all connectors (requires service mapping and a services file)
    optional parameters: profile (list only services for specified profile)
    
- watched-services: currently watched services, based on active user sessions (requires a services file)
    optional parameters: profile (list only services for specified profile)

- export-services: dump of the current internal state for the service mappers (which sids can and cannot decode).
    optional parameters: name (export map only for the specified connector, omit for all)
    
- fetch-cfg: returns a copy of the current proxy.xml (NOTE: response is without the cws-status-resp wrapper element!)
    optional parameters: none
    
- ctrl-commands: lists available control commands, and their options
    optional parameters: name (only show command definitions for the specified group name)

- status-commands: list available status commands, and their options
    optional parameters: none

- last-seen: lists information about disconnected or removed users (seen before by the session manager)
    optional parameters: name (only show seen info for the specified user, omit to show all)**

- login-failures: lists failed logins and connection attempts per user/ip-address.
    optional parameters: name (only show details for specified user/ip)**

- system-properties: shows all JVM system properties (superuser only)

- system-threads: dumps all the JVM threads as strings (superuser only)

- export-services: dumps the internal state of the service maps (admin only)
    optional parameters: format (set to hex or default)

* By default, only profiles (or connectors within profiles) that the calling user has access to will be shown.

** All commands are available to any user, however only admin users will be able to retrieve information about
other users. A non-admin user will receive only their own data, regardless of the name parameter.

As of 0.8.6 status-commands are also dynamic, so any piece of code (plugin or otherwise) may add new ones or override
the function of a command in the above built-in set.


Available control commands:
---------------------------
This list is dynamic (commands can be added by plugins and extensions).
Run the ctrl-commands status command as an admin user to obtain the current list, e.g:
http://proxy-host:8082/xmlHandler?command=ctrl-commands

Built in commands:  

- reset-connector: clear the service map for one connector (delete all cannot-decode entries causing new probes)
    required parameters: name (connector name)
    
- reset-service: clear the service map entry for one service.
    required parameters: id (service id, by default in decimal format, use 0x prefix when specifying hex value)
                         profile (name of profile that contains this service)

- retry-connector: attempt re-connection for one connector (also temporarily enables disabled connectors)
    required parameters: name (connector name)

- disable-connector: Temporarily disables a connector (until config reload or manual retry)
    required parameters: name (connector name)

- set-profile-debug: Temporarily changes debug flags (set to false for profile ALL or omit profile to delete ecm logs)
    required parameters: value (true/false)
    optional parameters: profile (profile name)

- set-user-debug: Temporarily changes debug for a user (enabling/disabling log-ecm, log-emm, log-zap etc)
    required parameters: value (true/false)
    optional parameters: name (user name)
    
- kick-user: close all sessions for the specified user
    required parameters: name (user name)
            
- osd-message: send newcamd osd message to all sessions for specified user (or for all users), where clientid is mgcamd or acamd
    required parameters: text (message text, avoid special characters)
    optional parameters: name (user name)

- clear-warnings: delete all user transaction warnings

- clear-events: delete all CWS events

- remove-seen: clear the last-seen log.
    optional parameters: name (user name, remove only entries for this user)

- remove-failed: clear the login-failures log.
    optional parameters: mask (glob wildcard mask, remove only entries with name matching this mask)

- gen-keystore: auto-create a java keystore containing a self-signed certificate for the specified hostname
    required parameters: password (keystore + key password)
                         host (hostname to use for cn)
                         validity (number of days)
    
- shutdown: stop this proxy node


Remote config updates:
----------------------
It is possible to deploy an updated proxy.xml via HTTP post, using the target-url: /cfgHandler

To fetch the current config xml, use the status command fetch-cfg (admin user required).
When posting an updated config to /cfgHandler, use the same approach as for Method 1 below, with two exception:
- The new config xml is to be posted as is, no cws-status-req or cws-command-req wrapper elements. 
- As a consequence, only HTTP basic auth can be used for the login (no way to specify session id in the xml).

The response xml will be a single element:
(proxy -> client)
  <cfg-result message="Preformatted result message indicating success or describing the error"/>


Command method 1 - HTTP post
--------------------
The default target url for both status and control commands is: /xmlHandler (e.g http:/proxy-host:8082/xmlHandler).
Xml queries need to be sent with content-type: text/xml (if no charset is specified UTF-8 is always assumed).
No url encoding or base64 encoding should be used. 

Login can use standard HTTP basic auth or, in situations where that isn't practical, the following xml-based login
can be used instead:

(login request, client -> proxy)
  <cws-status-req ver="1.0">
    <cws-login>
      <user name="user" password="password"/>
    </cws-login>
  </cws-status-req>

(successful login reply for an admin user, proxy -> client)
  <cws-status-resp ver="1.0">
    <status state="loggedIn" user="user" admin="true" super-user="true" session-id="fcjrij9z" />
  </cws-status-resp>

The user identity is any user defined by the current usermanager. 
A session id is by default valid until the proxy is restarted (no timeout).
Incorrect user/pass results in:

(failed login reply, proxy -> client)
  <cws-status-resp ver="1.0">
    <status state="failed" />
  </cws-status-resp>

Once a session id has been obtained, the syntax for status commands looks like this:
(proxy-status command, client -> proxy) 
  <cws-status-req ver="1.0">
    <session session-id="fcjrij9z"/>
    <proxy-status include="true"/>
  </cws-status-req>

Multiple status commands can be sent in one request, like this:
(multiple commands, client -> proxy)  
  <cws-status-req ver="1.0">
    <session session-id="fcjrij9z"/>
    <proxy-status include="true"/>
    <cache-status include="true"/>
    <ca-profiles include="true"/>
    <cws-connectors include="true"/>
  </cws-status-req>
  
To specify parameters for the status commands, just add attributes:
(cws-connector command with name parameter, client -> proxy)
  <cws-status-req ver="1.0">    
    <cws-connectors name="testconn" include="true"/>
  </cws-status-req> 

A control command uses a slightly different syntax, and there can be only one command per request.
(reset-connector control command, client -> proxy)
  <cws-command-req ver="1.0">
    <session session-id="fckivamh"/>
    <command command="reset-connector" name="test"/>
  </cws-command-req>


Command method 2 - HTTP get
-------------------
Uses the same url endpoint as post: /xmlHandler
Login is standard HTTP basic auth only (a session cookie called JSESSIONID will be set if supported).

The same commands are available, but everything is specified on the query string as follows:
http://proxy-host:8082/xmlHandler?command=commandName&paramName=paramValue&otherParam=otherValue...

Examples:
/xmlHandler?command=proxy-users&profile=cable (retrieve all user sessions in profile cable)
/xmlHandler?command=cws-connectors&name=testconn (retrieve connector named testconn)

Control commands work the same way:
/xmlHandler?comand=osd-message&text=hello&name=userx (send osd text hello to all sessions for userx)


Example status web
------------------
The proxy comes with a simple web site (cs-status.war) made up entirely of client side javascript and xslt. This is
not a conventional web application, but rather a standalone script that will fetch xml from the proxy (ajax-style) and
format it using xslt directly in the browser. It has been tested in firefox 2/3, ie 6/7/8 and safari 3/4 (and probably 
won't work in anything else). To get an idea of how the status web works with the xml api, check out the /api-test.html
test page.

To modify the visual appearance (or even structure) of the status web, look at the following files:

  /xslt/cws-status-resp.xsl - This is the xslt xml that transforms all status command responses into html (or xhtml).
    It generates markup that will reference the css styles in: /css/style.css
    For more information on xslt: http://en.wikipedia.org/wiki/XSL_Transformations

  /js/cs-status.js - The javascript that defines the different sections, and handles pre-processing of the xml (before
    the xslt transform) and post-processing of the html. The post-processing involves adding script handlers to
    specific id's in the markup created by the xslt transform, making the status web somewhat interactive.
    Adding javascript handlers to the browser dom tree (rather than hard coding them into the markup) is sometimes
    referred to as 'unobtrusive' javascript.

The cs-status.war file (a zip containing the directory tree served by the built in httpd) can be replaced at runtime
and changes will take effect immediately, no restart required.


Shell script/agent access
-------------------------
Xslt can also be used to extract live proxy information from the command line, for scripts or monitoring agents like
those used by NMS solutions such as nagios or zabbix. This is one example using wget and xsltproc from libxslt:

#!/bin/sh
echo $(wget --no-check-certificate -q -O - https://admin:passwd@proxy.host.com:8082/xmlHandler?command=proxy-status | xsltproc test.xsl -)

Where test.xsl is a snippet from the cws-status-resp.xsl file that handles formatting of the proxy-status + jvm output.
In a real use case you would likely extract only specific values, but this shows that any advanced formatting can be used:

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="text"/>
<xsl:template match="cws-status-resp">
  <xsl:for-each select="//proxy-status/jvm">
    CSP <xsl:value-of select="../@version"/>
    OS: [<xsl:value-of select="@os"/>]
    JVM: [<xsl:value-of select="@name"/> <xsl:value-of select="@version"/>]
    Heap: [<xsl:value-of select="@heap-total - @heap-free"/>k/<xsl:value-of select="@heap-total"/>k]
    TC: [<xsl:value-of select="@threads"/>]
    <xsl:if test="@filedesc-open"> FD: [<xsl:value-of select="@filedesc-open"/>/<xsl:value-of select="@filedesc-max"/>]</xsl:if>
  </xsl:for-each>
  <xsl:for-each select="//proxy-status">
    Name: <xsl:value-of select="@name"/>
    Started: <xsl:value-of select="@started"/>
    Uptime: <xsl:value-of select="@duration"/>
    Cards: <xsl:value-of select="@connectors"/>
    Sessions: <xsl:value-of select="@sessions"/> (active: <xsl:value-of select="@active-sessions"/>)
    Estimated total capacity: <xsl:value-of select="@capacity"/> (ECM-&gt;CW transactions per CW-validity-period)
    Estimated total load: <xsl:value-of select="sum(//connector/@ecm-load)"/> (unique transactions during the last period)
    ECM total: <xsl:value-of select="@ecm-count"/> (average rate: <xsl:value-of select="@ecm-rate"/>/s)
    ECM forwards: <xsl:value-of select="@ecm-forwards"/>
    <xsl:if test="@ecm-count &gt; 0"> (<xsl:value-of select="format-number(@ecm-forwards div @ecm-count, '##.0%')"/>)</xsl:if>
    ECM cache hits: <xsl:value-of select="@ecm-cache-hits"/>
    <xsl:if test="@ecm-count &gt; 0"> (<xsl:value-of select="format-number(@ecm-cache-hits div @ecm-count, '##.0%')"/>)</xsl:if>
    ECM denied: <xsl:value-of select="@ecm-denied"/>
    <xsl:if test="@ecm-denied &gt; 0"> (<xsl:value-of select="format-number(@ecm-denied div @ecm-count, '##.0%')"/>)</xsl:if>
    ECM failures: <xsl:value-of select="@ecm-failures"/>
    EMM total: <xsl:value-of select="@emm-count"/>
  </xsl:for-each>
</xsl:template>
</xsl:stylesheet>