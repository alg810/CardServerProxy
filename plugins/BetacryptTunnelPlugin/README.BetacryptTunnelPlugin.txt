BetacryptTunnelPlugin
---------------------

Converts plain Nagra3 ECMs to Betacrypt tunneled ECMs. Needed to watch Sky Germany HD channels when using an Betacrypt emulating Nagra card.

NOTE:
- Requires CSP 0.9.1 or newer.

TODO/SUGGESTIONS:
- Make it more "generic" to use with other CA-Systems and rename this Plugin.
- Network-Id for the "pseudo" Nagra Profile must be different from the Betacrypt Profile and should not be zero.
- Only change the ECM header when you know what you are doing.

Example config:
---------------
  <plugin class="com.bowman.cardserv.BetacryptTunnelPlugin" enabled="true" jar-file="betacrypttunnelplugin.jar">
    <plugin-config>
      <profiles>pseudo_nagra_profile</profiles>
      <target-network-id>0085</target-network-id>
      <ecm-header>C7 00 00 00 01 10 10 00 87</ecm-header>
    </plugin-config>
  </plugin>
  
Status commands:
----------------
- No commands

Usage example
-------------
- Set Profile to your Nagra3 Profile with CAID 0x1833 or 0x1834. The plugin will determine the target CAID automatically.
- Set Network-ID to the same ID also used in the "real" Betacrypt Profile with CAID 0x1702 or 0x1722.

  <profile name="sky_de_betacrypt" ca-id="1702" network-id="0085" enabled="true" debug="false">
    <newcamd listen-port="12345"/>
  </profile>

  <!-- pseudo profile without connectors used by BetacryptTunnelPlugin -->
  <profile name="sky_de_nagra" ca-id="1833" network-id="0001" enabled="true" debug="false">
    <newcamd listen-port="12346"/>
  </profile>