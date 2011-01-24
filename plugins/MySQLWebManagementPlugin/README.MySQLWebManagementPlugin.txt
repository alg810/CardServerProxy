MySQLWebManagementPlugin
------------------------

This plugin offers a web-ui to add/edit/delete/show users. Its related to the "MySQLUserManager" and doesn't work with other UserManagers.

NOTE:
-----
- when more than around 90 users exist it will only show the first 90 ones. The maximum size of a status command seems to be limited. Needs to be fixed though

TODO / suggestions:
-------------------
- allow to rename profiles in the profile section
- add groups to categorize users
- add an extra comment field
- add an option to sort users
- add function to export users as xml
- add an option to generate a user password
- change the frontend so that it can also handle more than 90 users (damn i need a bigger test environment :/)
- change (in add/edit users) the behaviour of the checkboxes when you click on "ALL" it should check all checkboxes and also the other way round.

Example config:
---------------
    <plugin class="com.bowman.cardserv.MySQLWebManagementPlugin" enabled="true" jar-file="mysqlwebmanagementplugin.jar">
    </plugin>

