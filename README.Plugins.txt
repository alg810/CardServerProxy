CSP Plugins
-----------

As of 0.8.6 the proxy has a plugin framework. This is another one-hour hack so don't read too much into it.

NOTE: With 0.8.10 it is also possible to add connector implementations using these same methods, although connectors
wouldn't be loaded under the plugins section (see README.ConaxConnector.txt).

Some ideas for plugins:
- CA emulation, have the proxy read/fetch/use the static keys and the clients wont have to.
- Your own simplified CS protocol.
- BISS.
- Log aggregation, have the proxy receive udp/syslog events from servers and clients to aid troubleshooting.
- Fault management, triggering nagios or zabbix alarms on critical errors.

To create a new plugin, first read README.Compiling.txt and ensure you can successfully build the proxy itself.
The source code itself is the ultimate documentation and to find a place to start, you can use the following param when
starting java: -Dcom.bowman.cardserv.util.tracexmlcfg=true
That will keep track of where all proxy.xml accesses are made from in the code, and dump it to file when
you request it via the admin section of the status web.

Then use the following procedure:
  1. Copy one of the existing plugin directory trees and rename it (e.g MyTestPlugin).
  2. Edit build.xml in the plugin dir and search/replace the old name to match yours.
  3. Place any extra dependencies your plugin will need in the lib dir. Remove any jars that are not needed.
  NOTE: As of 0.9.0, the plugin class loader can fetch needed jars for you, to avoid having to distribute them with
  the plugin. See below for details.
  4. Start editing the source, renaming the main plugin class and file to match your new name.
  5. Run ant in the plugin dir to compile and build the jar (it ends up in dist).

Place the jar in proxy-home/plugins, then add the config elements for the plugin to proxy.xml, e.g:
<proxy-plugins>
  ...
  <plugin class="com.bowman.cardserv.MyTestPlugin" enabled="true" jar-file="mytestplugin.jar">
    <plugin-config>
      // plugin specific config here
    </plugin-config>
  </plugin>
  ...
</proxy-plugins>

The plugin lifecycle is as follows:
  1. The main plugin class (that implements ProxyPlugin) is instantiated.
  2. configUpdated() is called with the settings specified for this plugin in proxy.xml.
  3. Assuming configUpdated() didn't throw any exceptions, start() is called, with a reference to the proxy istself.
  4. Next time proxy.xml is touched or changed, stop() will be called allowing the plugin to cleanup before unload.

For most plugins it should be possible to replace the jar file and update/touch proxy.xml to have the new version loaded
without restarting.
NOTE: As of 0.8.11, plugin jars are watched for changes, and automatically reloaded when replaced.

The plugin api is fairly primitive, here's a quick guide for a single class example:

package com.bowman.cardserv;
// You can use any package, but there could be some protected methods only accessible from this one in the main classes.

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.rmi.*;
import com.bowman.cardserv.web.*;
// Depending on what you intend to do, different parts of the proxy source needs to be imported.

import java.io.*;
import java.util.*;


public class MyTestPlugin implements ProxyPlugin {
// The main class of the plugin must implement this interface: com.bowman.cardserv.interfaces.ProxyPlugin
// If you want the plugin to have a say in connector selection, also implement: com.bowman.cardserv.interfaces.CwsSelector
// If you want the plugin to filter replies from connectors (dcw's) implement: com.bowman.cardserv.interfaces.ReplyFilter

// If your plugin makes use of 3rd party libraries, create an array with the direct urls to each dependency:
  public static final String[] dependencyUrls = new String[] {
    "http://www.host.com/path/jarfile.jar",
    "http://www.host2.org/jarfile2.jar"
  }

// Methods outlined below...

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    // Whenever proxy.xml is changed the plugin will be discarded and reloaded. Settings from proxy.xml available here.
    // The plugin should verify that they make sense and throw a ConfigException if they dont.

    // If you have 3rd party dependencies, configure the plugin classloader to fetch them first:
    PluginClassLoader pcl = (PluginClassLoader)getClass().getClassLoader();
    pcl.resolveDependencies(dependencyUrls, logger);
    // Now you can make use of any class from the listed jars.    
  }

  public void start(CardServProxy proxy) {
    // Called after configUpdated(). Tells the plugin to initialize everything and start any background jobs etc.
    // If it needs access to the proxy it should store the reference passed to this method.
    // This reference can be used to get access to most of the functionality, see MessagingPlugin for one example.
    // Any control or status commands should be registered here.
  }

  public void stop() {
    // Called before unload is attempted (every time proxy.xml changes, or when the plugin jar is replaced).
    // The plugin should stop all threads and remove any references to itself that it might have registered elsewhere.
    // Any control or status commands should be unregistered here.
  }

  public String getName() {
    return "MyTestPlugin"; // Displayname for the plugin
  }

  public String getDescription() {
    return "A test plugin."; // Description
  }

  public Properties getProperties() { // arbitrary parameters shown in the proxy-plugins status command output (admin only, 0.9.0+)
    Properties p = new Properties();
    p.setProperty("relevant-param", "value");
    return p;
  }
  
  public CamdNetMessage doFilter(ProxySession proxySession, CamdNetMessage msg) {
    // Called with every single message sent to the proxy from a client session, or from the proxy back to the sessions.
    // The plugin can modify the message, return something else entirely, or block it (by doing msg.setFilteredBy("Reason text")).
    // See the LoggingPlugin or EmmAnalyzerPlugin for examples.

    return msg; // = do nothing
  }

  public byte[] getResource(String path, boolean admin) {
    // This allows the plugin to export content to the httpd
    // The following code ensure that anything placed in /web in the plugin jar file is available via the proxy web.
    // Files are accessed using http://proxy.host.com/plugin/PluginName/path/filename.ext
    // The admin flag indicates if the user logged into the web as an admin user.

    if(path.startsWith("/")) path = path.substring(1);
    try {
      DataInputStream dis = new DataInputStream(getClass().getResourceAsStream("/web/" + path));
      byte[] buf = new byte[dis.available()];
      dis.readFully(buf);
      return buf;
    } catch (IOException e) {
      return null;
    }

    // Note that user login will be required to access anything this way, with one exception:
    // The plugin will be queried for a file called "load.js" whenever anyone accesses the default status web (cs-status).
    // This file allows the plugin to hook itself into the javascript in cs-status.war. See GeoipPlugin for an example.    
  }

  public byte[] getResource(String path, byte[] inData, boolean admin) {
    // Same as the above method, except for http POST instead of GET.
    // Can be used to allow file uploads from the web to a plugin (or any custom data from the client side scripting).

    return null;
  }


  // If your plugin implements the CwsSelector interface, the proxy will call this method for every incoming ecm request.
  // The call includes the session where the message originated, and the connectors (set of name Strings) that the proxy
  // thinks are valid choices to handle this request. Your plugin can remove names from this list based on the contents
  // of the CamdNetMessage data, or based on the properties of the user associated with the session.
  /*
  public Set doSelection(ProxySession session, CamdNetMessage msg, Set connectors) {
    return connectors;   
  }
  */

  // If your plugin implements the ReplyFilter interface, this method will get called for every connector reply (dcw).
  // This happens before the proxy does anything with the reply, and allows the plugin to look for and remove bad dcw's.
  // To change a suspicious dcw reply into a cannot decode, do msg.setCustomData(new byte[0]).
  // To silently block a reply entirely (probably a bad idea) return null instead of the msg.
  /*
  public CamdNetMessage doReplyFilter(CwsConnector connector, CamdNetMessage msg) {
    return msg; // = do nothing
  }
  */  

}