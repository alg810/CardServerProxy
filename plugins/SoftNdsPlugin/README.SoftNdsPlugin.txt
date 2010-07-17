SoftNdsPlugin
--------------

Example plugin implementation. Example NDS softcrypt implementation. Thanks to colibri (http://colibri.net63.net/) for explaining the algorithm. 

The SoftcamNDSConnector rewritten as a plugin instead. The code gets much cleaner. :)

TODO/SUGGESTIONS:
- 

Example config:
---------------
  <plugin class="com.bowman.cardserv.SoftNdsPlugin" jar-file="softndsplugin.jar" enabled="true">
    <plugin-config>
      <profiles>profile1 profile2</profiles> <!-- optional, restrict plugin to traffic in these profiles only -->
      <P3>15 85 C5 E4 B8 52 EC F7 C3 D9 08 BA 22 4A 66 F2 82 15 4F B2 18 48 63 97 DC 19 D8 51 9A 39 FC CA 1C 24 D0 65 A9 66 2D D6 53 3B 86 BA 40 EA 4C 6D D9 1E 41 14 FE 15 AF C3 18 C5 F8 A7 A8 01 00 01</P3>
      <P4>0F 1E 2D 3C 4B 5A 69 78 87 96 A5 B4 C3 D2 E1 F0</P4>
    </plugin-config>
  </plugin>
