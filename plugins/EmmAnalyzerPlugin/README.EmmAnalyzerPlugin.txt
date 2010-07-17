EmmAnalyzerPlugin
-----------------

Records emm's received from client sessions, and stores them in memory for inspection.

NOTE: For this to be useful, observe the following:
- The client(s) that is to be monitored must be set to forward all emms, and not perform any local caching.
- The client(s) should probably stay tuned to the same mux/transponder/sid for the duration of the recording.
- Emm's in memory are kept indefinately, until the plugin is restarted or disabled (could grow large).
- Limit the recording to a particular user and profile, if there is a lot of traffic.

TODO/SUGGESTIONS:
- This could be extended to actually forward the emms to a chosen card, once the most recent unique sequence
has been identified. This way the proxy could make an educated guess and detect cards that have missed updates,
and perform the updates on its own (by sending the entire changed emm sequence in one go).
- Programmable block rules.
- Change tracking (e.g save changed emm's in an identified loop to timestamped files).

Example config:
---------------
  <plugin class="com.bowman.cardserv.EmmAnalyzerPlugin" enabled="true" jar-file="emmanalyzerplugin.jar">
    <plugin-config>
      <profiles>profile1 profile2</profiles> <!-- optional profile filter --->
      <!-- if this is present, only emms sent to matching profiles will be kept -->      
      <users>user1 user2 user3</users> <!-- optional user filter -->      
    </plugin-config>
  </plugin>
  
Status commands:
----------------
- emm-log: Show gathered emm statistics for the specified user and profile (admin user required).
    required parameters: name (name of the user)
                         profile (name of the profile)
    optional parameters: data (true/false, include the full emm data in the xml reply).

Usage example
-------------
http://proxy.host.com/xmlHandler?command=emm-log&name=username&profile=profilename&data=false
  
                             
Example output (excerpt):
-------------------------
<cws-status-resp ver="1.0">
<emm-info user-name="username" profile="profilename" total-count="327" unique-count="126" last-interval="10474">
  <all-emms>
    <emm-record hash="E0C58D33" count="3" size="130">
      <seen-log>
        <sighting timestamp="Fri, 11 Jul 2008 15:33:12 +0200" client-sid="46f" index="1" />
        <sighting timestamp="Fri, 11 Jul 2008 15:58:21 +0200" client-sid="46f" index="127" repeat-interval="1509557" />
        <sighting timestamp="Fri, 11 Jul 2008 16:23:31 +0200" client-sid="46f" index="253" repeat-interval="1509662" />
      </seen-log>
    </emm-record>
    <emm-record hash="4B7081AF" count="3" size="130">
      <seen-log>
        <sighting timestamp="Fri, 11 Jul 2008 15:33:23 +0200" client-sid="46f" index="2" />
        <sighting timestamp="Fri, 11 Jul 2008 15:58:32 +0200" client-sid="46f" index="128" repeat-interval="1508995" />
        <sighting timestamp="Fri, 11 Jul 2008 16:23:42 +0200" client-sid="46f" index="254" repeat-interval="1509768" />
      </seen-log>
    </emm-record>
    <emm-record hash="1B5E2988" count="3" size="130">
      <seen-log>
        <sighting timestamp="Fri, 11 Jul 2008 15:33:34 +0200" client-sid="46f" index="3" />
        <sighting timestamp="Fri, 11 Jul 2008 15:58:43 +0200" client-sid="46f" index="129" repeat-interval="1509710" />
        <sighting timestamp="Fri, 11 Jul 2008 16:23:54 +0200" client-sid="46f" index="255" repeat-interval="1510178" />
      </seen-log>
    </emm-record>
...
  </all-emms>
</emm-info>

This output tells you the following:
(emm-info)
- 327 emms have been received since monitoring started, from user username in profile profilename.
- Only 126 of these are unique, so some have repeated.
- The last 2 emms received were roughly 10 seconds apart.
(all-emms, 3 emms shown here)
- The hash uniquely identifies each emm, and size refers to how many bytes it was. 
- If data=true was specified the actual bytes would appear in a data attribute for each emm-record.
- Each of the 3 visible emms have been seen on 3 occasions since monitoring started.
- Each sighting lists when it occured, and how long it took before it was seen again.
- Client-sid refers to what the client was most likely tuned to, when the emm was seen (based on the last seen zap).
- The index refers to the order in which the emms were received (1 = the first one the client sent after monitoring started).

A reasonable conclusion here would be that the emm sequence is 126 messages long, and takes about 25 minutes to repeat.
At 130 bytes per message this makes it 16380 bytes in total (16k - 4 bytes).
