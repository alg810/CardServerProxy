MySQLWebManagementPlugin
------------------------

This plugin offers a web-ui to add/edit/delete/show users. Its related to the "MySQLUserManager" and doesn't work with other UserManagers.

TODO / suggestions:
-------------------
- add groups to categorize users
- add an extra comment field
- add an option to sort users
- add function to export users as xml
- add an option to generate a user password
- allow to set the number of showed user entries per page, currently its 25
- replace the user-info xml with an extra user info page 

Example config:
---------------
    <plugin class="com.bowman.cardserv.MySQLWebManagementPlugin" enabled="true" jar-file="mysqlwebmanagementplugin.jar">
    </plugin>

