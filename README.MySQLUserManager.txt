MySQLUserManager
----------------

A simple mysql usermanager to get the user informations directly from a mysql database without having to load them over external xml sources.
For the database communication it uses the MySQL Connectors/J Driver (mysql-connector-java-5.1.XX-bin.jar) found on "http://dev.mysql.com/downloads/connector/j/".
Formally it was related to the "CSP MySQL User Manager (c.m.u.m.)" written in php by dukereborn. This is not anymore. For adding/editing/deleting users
please use the Plugin "MySQLWebManagementPlugin". Also the database structure changed to a more runtime friendly one to avoid unnecessary castings in the code and is 
therefor not compatible with the one used in dukereborns cmum. But its possible to import users to the mysql database through xml sources and the plugin "MySQLWebManagementPlugin". 
So you can just pass in the url of your genxml.php and it will import the users to the database.

On csp startup the MySQLUserManager creates the needed tables automatically when they don't exist. The user used for that therefor needs the rights to do that. 
Also when the table gets created it will store a default user "admin" with the password "secret".

TODO/SUGGESTIONS:
----------------- 
- check table correctness on csp startup
- stresstest
- implement edit profile
- store the open-access user in database too

Installation:
---------------
- get the file "mysql-connector-java-5.1.XX-bin.jar" from "http://dev.mysql.com/downloads/connector/j/", rename it it "mysql-connector-java.jar" and put it in your csp folder lib/ .
- compile csp with ant.
- edit your proxy.xml by replacing the Simple/XMLUserManager with MySQLUserManager.

Example config:
---------------
  <user-manager class="com.bowman.cardserv.MySQLUserManager" allow-on-failure="true" log-failures="true">
    <auth-config>
	  <mysql-database>
		<dbhost>172.16.12.72</dbhost> <!-- optional - default: 127.0.0.1 -->
		<dbport>3306</dbport>  <!-- optional - default: 3306 -->
		<dbname>cardserverproxy</dbname>  <!-- optional - default: cardserverproxy -->
		<dbuser>csp_test</dbuser>
		<dbpassword>3PeBO9eLb6AtqDXw3Ve3</dbpassword>
	  </mysql-database>
	  <!-- local defined user -->
	  <user name="test" password="test" display-name="Administrator" admin="true"/> 
	  <!-- xml source -->
	  <user-source name="localusers">
		<user-file-url>file:config\users.example.xml</user-file-url>
      </user-source>
	  <!-- xml source update interval -->
      <update-interval>5</update-interval>
    </auth-config>
  </user-manager>
  
Status commands:
----------------
- No commands

Database structure (little overview):
-------------------
+----------------------------------+
| users                            |
+--------------------+-------------+
| id (primary key)   | INT(10)     |
| username (unique)  | VARCHAR(30) |            +------------------------+            +----------------------------+
| password           | VARCHAR(50) |            | users_has_profiles     |            | profiles                   |
| displayname        | VARCHAR(30) |     n      +--------------+---------+     m      +--------------+-------------+
| ipmask             | VARCHAR(30) |------------| users_id     | INT(10) |------------| id           | INT(10)     |
| maxconnections     | TINYINT     |            | profiles_id  | INT(10) |            | profilename  | VARCHAR(60) |
| enabled            | BOOLEAN     |            +--------------+---------+            +--------------+-------------+
| admin              | BOOLEAN     |            | ON DELETE CASCADE      |
| debug              | BOOLEAN     |            | ON DELETE UPDATE       |
| mapexclude         | BOOLEAN     |            +------------------------+
| email              | VARCHAR(30) |
+--------------------+-------------+

Changelog:
----------------
version 0.7 alpha
- added an extra profile table with a n:m relationship to the user table. That makes it possible to delete or edit profiles without having to
  change it for each user. The edit function is on my to-do-list. The profile column in the users table is therefor not needed anymore.
- added code to handle the profile table
- fixed the delete user function
- changed maxconnections type in database from INT(10) to TINYINT UNSIGNED (who needs more than 255 connections per profile?)
- changed the name of the plugin "MySQLWebManagement" to "MySQLWebManagementPlugin" to avoid confusions

version 0.6 alpha
- automatic table creation
- added code to add/edit/delete users

version 0.5 alpha
- added: extended the XmlUserManager so you can parallel use xml sources and local defined users. 
  priority: (highest) xml - local - mysql (lowest)
- added: introduced a "Read-Ahead-Cache" to cache the currently needed user informations. It works like this:
  When a user data is needed the UserCacheManager loads them from the database with only one query and puts them for the specified user into the cache.
  As long as the data is needed (for example: the user is connected and is watching, or user is logged in into the backend) a background thread 
  asynchronly refreshes the user data by getting them from the database again. When its not needed anymore (for example: the user disconnected) the cache
  entry for the specified user gets removed. 
  The advantages are that in most cases the user data can be get directly out of the cache and is therefore a lot faster than always querying the 
  database, and the local cached data is always synchron with the database. Also the cache only contains data which is actually needed.
  Now the time difference compared to user data defined in the proxy.xml on my Intel Atom D510 is 1 ms.
- changed: set cleaner thread priorities for UserCacheManager and ConnectionPoolManager to low.
- changed: in the config the mysql connection must be defined in <mysql-database></mysql-database> tags. Only one database connection
  is currently allowed.
- fixed: displayname default is now username like it is in the SimpleUserManager

version 0.4 alpha
- "Set user debug" through admin panel now works
- now using preparedStatements for querying the database
- fixed the dynamic connection pool

version 0.3 alpha
- fixed the default values
- changed the simple static ConnectionPool to a dynamic ConnectionPool. Now new connections will be created when needed and closed
  again when they exceed an idletime limit of 60 seconds.
- removed config parameter <connection-pool-size>

version 0.2 alpha
- introduced a simple ConnectionPool with static database connections