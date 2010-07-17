MessagingPlugin
---------------

Another example plugin, showing automated messaging in response to ecm transaction or cws events.
Also adds mail-sending commands, but probably not particularly useful ones at this point.

The automated osd messaging (mgcamd clients only) has a dynamic event matching that could be
used for other purposes as well. Message triggers can be defined with these attributes:

match-flags="flags" - match any transaction that contains all of these flags (separated by space, e.g "Z N").
match-warning="true/false" - match any transaction that was considered a warning by the proxy.
match-sids="sids" - match any transaction that has either of these sids (hex, separated by space).
match-profiles="profiles" - match any event for any of these profiles (profile names, separated by space).
match-usernames="names" - match any transaction that occurs for these users (user names, separated by space).
match-events="events" - match any event with these numbers (event numbers, separated by space, e.g "2 4").

Also matching can be restricted to only apply to admin users, or cws owner users (users with the same
name as connector are assumed to be the owner of that connector):
admin-only="true/false"
cws-owner-only="true/false"

There is an implicit boolean AND between each match attribute, for example:
<msg-trigger match-flags="Z N" match-sids="051f" match-profiles="profileY" match-usernames="userA userB">
- triggers when either userA or userB zaps (Z) to sid 051f in profile profileY, and the service is unavailable (N).

See example config below for more ideas...

NOTE: 
- Mail sending uses the javamail api. Fetch: http://repo1.maven.org/maven2/javax/mail/mail/1.4.1/mail-1.4.1.jar
  Rename it to mail.jar and place it in proxy-home/lib (proxy restart required).
- If an mgcamd client has multiple tuners, matching on the Z flag (zap) is probably a bad idea (exclude such users).
- For email triggers, the target attribute can be either a user name (for users with email set) or an address.

TODO/SUGGESTIONS:
- Message store for persistent messages (per user inbox).
- Sticky osd messages (repeated until acknowledged by user via web).
- Separate log files to track who received what.
- Persist the state for what has been sent to avoid repeating messages when the plugin is reloaded/reconfigured.

Example config:
---------------
  <plugin class="com.bowman.cardserv.MessagingPlugin" enabled="true" jar-file="messagingplugin.jar">
    <plugin-config>

      <auto-mgcamd-osd min-interval="20" enabled="true"> <!-- min interval is flood protection, in seconds -->
        <exclude-users>user1 user2</exclude-users> <!-- never send to these even if they do id as mgcamd -->
        <exclude-profiles>profileX profileY</exclude-profiles> <!-- never send to anyone in these profiles -->        

        <!-- if the user is an admin and flagged "warning"-transaction occurs for him, send notification -->
        <msg-trigger match-warnings="true" admin-only="true">
          <msg format="TR warning: {0}"/>
        </msg-trigger>

        <!-- format masks
          {0} full preformatted text (similar to whats shown in the status web for cws events and tr warnings)
          {1} name (user, connector or proxy name depending on the type of event matched)
          {2} profile name
          {3} service name and sid (if an ecm transaction was matched, otherwise blank)
          {4} formatted time of the event/transaction
          {5} just the event text (e.g "disconnected" for cws event or "service name - time - flags" for ecm tr)
        -->

        <!-- if a user zaps to sid 04f2 in profile profileX, send immediate msg with the channel name -->
        <msg-trigger match-flags="Z" match-sids="04f2" match-profiles="profileX">
          <msg format="You zapped to: {3}"/>
        </msg-trigger>

        <!-- if a cws connect failure or invalid card event occurs, send notification to the cws owner user -->
        <msg-trigger match-events="4 8" cws-owner-only="true">
          <msg format="CWS Event: {0}"/>
        </msg-trigger>
        
        <!-- CWS Events
          2 = Successfully connected
          3 = Disconnected
          4 = Connection attempt failed
          5 = Warning (timeout)
          6 = Lost service
          8 = Invalid card data (on connect)
          10 = Proxy node startup notification
        -->

        <!-- if a user zaps to a blocked or unavailable service, tell them so -->
        <msg-trigger match-flags="Z N">
          <msg format="Service '{3}' is not available"/>
        </msg-trigger>

        <!-- if these cws events occur, notify the user 'adminuser' -->
        <msg-trigger match-events="4 6 8">
          <msg format="CWS Event: {0}" target="adminuser"/>
        </msg-trigger>

        <!-- every time 'someuser' zaps in profile profileX, send him the msg -->
        <msg-trigger match-flags="Z" match-profiles="profileX" match-usernames="someuser">
          <msg format="You've got mail!"/>
        </msg-trigger>

        <!-- fetch additional message triggers from an external file -->
        <external-msg-triggers enabled="false">
          <trigger-file-url>http://192.168.0.5/triggers.xml</trigger-file-url>
          <trigger-file-key>asdf22</trigger-file-key> <!-- optional, remove if there is no encryption -->
          <update-interval>5</update-interval> <!-- minutes, 0 for only manual updates -->
        </external-msg-triggers>

      </auto-mgcamd-osd>

      <email enabled="true"> <!-- this requires javamail: mail.jar placed in lib -->
        <smtp-server>localhost</smtp-server>
        <smtp-port>25</smtp-port>
        <sender-address>proxy@host.com</sender-address> <!-- reply to -->
        <mail-footer>--=--------[ user: {0} ] ------=------ [ CSP: {1} ] --------------------------=--</mail-footer>
        <!-- format masks: {0} = target username, {1} = CSP version -->

        <auto-email min-interval="600" enabled="true"> <!-- max one mail per 10 mins and user -->
          <msg-trigger match-events="4 8" cws-owner-only="true">
            <msg format="CWS Event: {0}"/> <!-- mail the cws owner user when the connector wont connect -->
          </msg-trigger>
          <msg-trigger match-events="4 8">
            <msg format="CWS Event: {0}" target="admin@email.com"/> <!-- mail this address when any connector wont connect -->
          </msg-trigger>
          <msg-trigger match-events="4 6 8">
            <msg format="CWS Event: {0}" target="someuser"/> <!-- mail this user when any connector wont connect or a service is lost -->
          </msg-trigger>
        </auto-email>

      </email>
      <!-- javamail download @ http://java.sun.com/products/javamail/downloads/index.html -->

    </plugin-config>
  </plugin>
  
Control commands:
----------------
- mail-user: Send a plain text mail to a single specified user (assuimg the user has an email set).
    required parameters: text (mail text)
                         name (user name)
                         
- mail-profile: Send a plain text mail to all users in a profile (those that have an email set).
    required parameters: text (mail text)
                         name (profile name)
                         


                             
