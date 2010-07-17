GeoipPlugin
-----------

An example plugin to illustrate most of the plugin framework. Adds geoip information to client ip addresses, 
displayed using google maps. This plugin will hook itself into the status web and add information to the
"sessions" section (adds a map link and the estimated city for each user).

NOTE: 
- This requires the maxmind geoip city database, a free light-version can be downloaded from:
  http://www.maxmind.com/download/geoip/database/GeoLiteCity.dat.gz
- Google maps requires a key for each url (really just the hostname) that the maps will be accessed via:
  http://code.google.com/apis/maps/signup.html
- By default the map view starts at zoom level 5 centered on scandinavia.

TODO/SUGGESTIONS:
- Adding the cardservers, any other proxy peers, disconnected users, custom icons, poly-lines etc... 
  is left as an exercise for the reader. :)
- Other sources could be added to get better accuracy (scraping commercially available data).
- Manually overriding incorrect positions via config.


Example config:
---------------
  <plugin class="com.bowman.cardserv.GeoipPlugin" enabled="false" jar-file="geoipplugin.jar">
    <plugin-config>
      <geoipcity-path>etc/GeoLiteCity.dat</geoipcity-path> <!-- Path to maxmind.com city database -->
      <!-- Free but inaccurate: http://www.maxmind.com/download/geoip/database/GeoLiteCity.dat.gz -->
      
      <googlemaps-key>ABQIAAAAN0_es7sJ14IruZiSrpu34RT3vEHBx8wTCVRAsHfWL98on4riixQrwIFAacB8Zvuxk6Vy4CJShXdLfw</googlemaps-key>
      <!-- Key must match your proxy status web access hostname, free signup @ http://code.google.com/apis/maps/signup.html -->
      
      <start-lat>62.35</start-lat>
      <start-long>18.066667</start-long>
      <start-zoom>5</start-zoom>
      <!-- Starting center point and zoom level for the map, default roughly = scandinavia -->
    </plugin-config>
  </plugin>
  
Status commands:
----------------
- proxy-users: Overrides the default proxy-users command with one that returns the same output but with
  the extra attributes: geoip-lat, geoip-long, geoip-city.

Usage example
-------------
http://proxy.host.com/xmlHandler?command=proxy-users&hide-inactive=false

The last proxy-users result is kept, and can be shown on a map using the url:
http://proxy.host.com/plugin/geoipplugin/googlemap.html

NOTE: This is an intentional huge hack and security hole, it breaks if multiple users access the web.
I.e if the last proxy-users command was excuted by admin and a non-admin reloads the googlemap.html,
they'll see the admin results. If you want something more sane you'll just have to fix and recompile.
  
                             
