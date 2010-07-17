DcwFilterPlugin
---------------

Example plugin illustrating how to intercept and alter cw's returned from connectors, before normal proxy processing.

NOTE:
- If enabled, the plugin will change all replies of length 16 that consist entirely of zeroes into empty replies.
- Requires CSP 0.9.0 or newer.

TODO/SUGGESTIONS:
- DCW checksum validation/zeroing.

Example config:
---------------
  <plugin class="com.bowman.cardserv.DcwFilterPlugin" enabled="true" jar-file="dcwfilterplugin.jar">
    <plugin-config>
      <profiles>profile1 profile2</profiles> <!-- apply filtering to connectors in these profiles only, optional -->

      <bad-dcw>00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01</bad-dcw> <!-- add more 16 byte sequences that are to be considered bad, optional -->
      <bad-dcw>01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16</bad-dcw>
    </plugin-config>
  </plugin>
  
Status commands:
----------------
- No commands

Usage example
-------------
- No commands
                             
