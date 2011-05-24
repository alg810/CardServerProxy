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
- store the open-access user in database too

Installation:
---------------
- get the file "mysql-connector-java-5.1.XX-bin.jar" from "http://dev.mysql.com/downloads/connector/j/" and put it in your csp folder lib/ .
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
