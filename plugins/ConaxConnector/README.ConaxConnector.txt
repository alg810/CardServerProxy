ConaxConnector
--------------

Example connector implementation. Uses the java6 smartcardio api to read a local conax card with a pcsc reader.
If anyone is interested in writing/porting connectors to read cards for other ca-system, contact me.

NOTE: This will probably only work with java6+, and it has only been tested with this setup:
- Sun JDK 6 Update 12 from java.sun.com under linux and windows.
- PCSC usb cardreader compatible with the generic ccid driver (libccid on linux). Specifically OmniKey CardMan 3121.
- If you get "Node not found" on linux despite pcscd and pcsc-tools correctly detecting the reader, make sure libpcsclite-dev is installed.

TODO/SUGGESTIONS:
- This is only an example to show how extra connector types can be added to the proxy, and at the same time demonstrate
  how to read a dvb-ca card directly using the javax.smartcardio api (JSR268).
- http://java.sun.com/javase/6/docs/jre/api/security/smartcardio/spec/javax/smartcardio/package-summary.html

Example config:
---------------
  <conax-connector name="conax-card" profile="satellite2" class="com.bowman.cardserv.cws.ConaxCwsConnector" jar-file="conaxconnector.jar">
    <au-users>test3</au-users>
    <node>0</node>
  </conax-connector>
  
