XmlUserManager (com.bowman.cardserv.XmlUserManager)
---------------------------------------------------

XmlUserManager is a small extension of the built in example SimpleUserManager. Instead of reading user definitions 
only from proxy.xml it can fetch an external xml file from any url. This can be a simpler alternative to a full
database usermanager, when maintaining the same set of users in a cluster of multiple proxies.

- XmlUserManager uses the exact same xml format as SimpleUserManager (see proxy-reference.html).
- It will accept user definitions included in proxy.xml, just like SimpleUserManager. These are parsed before any
  attempts are made to get external definitions, making it possible to have some local users in addition to the main
  user db.
- If the same username exists both in proxy.xml and in the fetched user file, the local definition will be overwritten.

------------------------------------------------------

The following are settings are available for XmlUserManager:

<user-manager class="com.bowman.cardserv.XmlUserManager" log-failures="true">

- To load the XmlUserManager, use the following user-manager class name: com.bowman.cardserv.XmlUserManager
Changing from one user-manager to another requires a proxy restart.

<auth-config>     
  <user-file-url>http://192.168.0.5/users.xml</user-file-url>
  
- The url of the xml file with user definitions. The file should match the auth-config for SimpleUserManager, but
the top level element is ignored. See config/users.example.xml for an example. 
Any url can be used, including https/ftp with user:passwd@hostname type auth info.
File urls are also accepted. NOTE: Relative file urls are written with no initial slashes, e.g: file:etc/users.xml

NOTE: When using a http/https url, it doesn't have to point to an actual static xml file. A php/jsp/asp page that
renders the xml dynamically from an underlying database is a more flexible solution.
  
  <user-file-key>asdf22</user-file-key>

- Optionally, the user file can be blowfish encrypted using the included fishenc.jar tool (found in lib). If the
file is not encrypted, omit the user-file-key element entirely.

  <update-interval>5</update-interval> <!-- minutes -->
  
- How often to check for changes in the user file. If no changes have occured, the file will not be fetched/parsed.

  <user name="local1" password="test" ip-mask="192.168.0.*" profiles="cable" admin="true"/>
  <user name="local2" password="test" debug="true"/>

</auth-config>
</user-manager>

------------------------------------------------------

NOTE: Errors in the user file will not prevent the proxy from starting, it will only log warnings if unable to
fetch/parse the file. Local users in proxy.xml will still be available. 
Should the user file become temporarily unavailable or broken, the proxy will keep using the last known working one.
To see exactly what XmlUserManager is doing, use log-level FINE.

As of 0.8.8 it is possible to specify multiple source urls, using the following format:

<auth-config>

  <user-source name="someusers">
    <user-file-url>http://192.168.0.5/users.xml</user-file-url>
    <user-file-key>asdf22</user-file-key>
  </user-source>
  <user-source name="otherusers">
    <user-file-url>https://admin:secret@some.host.com/users.php</user-file-url>
  </user-source>
  <user-source name="localusers">
    <user-file-url>file:///tmp/users.xml</user-file-url>
  </user-source>

  <update-interval>5</update-interval> <!-- minutes -- >
  
</auth-config>
