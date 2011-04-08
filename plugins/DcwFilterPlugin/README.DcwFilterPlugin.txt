DcwFilterPlugin
---------------

Example plugin illustrating how to intercept and alter cw's returned from connectors, before normal proxy processing.

NOTE:
- If enabled, the plugin will change all replies of length 16 that consist entirely of zeroes into empty replies.
- Requires CSP 0.9.0 or newer.
- As of 0.9.1 the plugin also verifies checksum and length of replies.

The detect-links feature (disabled by default) will check for occurances of identical dcw sequences in use for other
currently watched services. Since the cache eliminates duplicates where the ecm requests were identical, this can only
occur when multiple different ecms result in the same dcw. Sid-cache-linking can then be used to take advantage of this
and reduce the load on the cards (i.e by telling the cache that if a dcw for service x already exists or is being
processed, then this is valid for service y as well causing a hit even though the ecms were different).

Verify-replies (disabled by default) is untested and probably needs additional work, but the idea is to require independent 
corroboration of each dcw received from a server, by blocking it until the same dcw is received again from a different server.
This could be used to trap bogus ecms used in certain ca-systems to leak unique card details into (fake) dcw replies.
It requires at least 2 connectors and enough capacity to always use redundant-forwarding in the proxy, or every single
reply will end up blocked.

TODO/SUGGESTIONS:
- Optional DCW checksum zeroing.

Example config:
---------------
  <plugin class="com.bowman.cardserv.DcwFilterPlugin" enabled="true" jar-file="dcwfilterplugin.jar">
    <plugin-config>
      <bad-dcw>00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01</bad-dcw> <!-- add more 16 byte sequences that are to be considered bad, optional -->
      <bad-dcw>01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16</bad-dcw>

      <detect-links>true</detect-links> <!-- keep a 20 sec backlog of all received replies and check for services that share the same dcw sequence -->
      <verify-replies>false</verify-replies> <!-- block and hold all replies until they've been received from at least 2 different connectors (experimental)
    </plugin-config>
  </plugin>
  
Status commands:
----------------
- No commands

Usage example
-------------
- No commands
                             
