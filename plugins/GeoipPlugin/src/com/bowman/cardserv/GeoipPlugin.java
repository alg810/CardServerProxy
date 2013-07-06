package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.rmi.*;
import com.bowman.cardserv.web.*;
import com.bowman.cardserv.session.SeenEntry;
import com.maxmind.geoip.*;

import java.io.*;
import java.util.*;
import java.rmi.RemoteException;
import java.text.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Jul 8, 2008
 * Time: 2:05:17 PM
 */
public class GeoipPlugin implements ProxyPlugin {

  private static final long MONTH = 3600 * 24 * 31;

  private String db;
  private LookupService service;
  private RemoteHandler proxy;
  private ProxyLogger logger;
  private StatusCommand usersCmd;
  private Map lastRequest = new HashMap();

  private String gmapsKey, dbInfo;
  private String startLat, startLong, startZoom;

  public GeoipPlugin() {
    logger = ProxyLogger.getLabeledLogger(getClass().getName());
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {

    db = xml.getFileValue("geoipcity-path", false);
    gmapsKey = xml.getStringValue("googlemaps-key", "");

    startLat = xml.getStringValue("start-lat", "62.35");
    startLong = xml.getStringValue("start-long", "18.066667");
    startZoom = xml.getStringValue("start-zoom", "5");

    try {
      service = new LookupService(db, LookupService.GEOIP_STANDARD);
    } catch(IOException e) {
      throw new ConfigException(xml.getFullName(), "Unable to load geoipcity database: " + e);
    }
  }

  public void start(CardServProxy proxy) {
    this.proxy = proxy.getRemoteHandler();
    try {
      usersCmd = new StatusCommand("proxy-users", "Show user(s)", "List of current user connections with geoip info.", false);
      usersCmd.register(this, true); // overrides the default proxy-users command
    } catch(NoSuchMethodException e) {
      e.printStackTrace();
    }

    Date dbDate = service.getDatabaseInfo().getDate();
    dbInfo = new File(db).getName() + " built on " + new SimpleDateFormat("yyyy-MM-dd").format(dbDate);

    logger.info("Using database: " + dbInfo);
    long dbAge = (System.currentTimeMillis() - dbDate.getTime()) / 1000;
    if(dbAge > MONTH) logger.warning("Database is older than one month: " + XmlHelper.formatDuration(dbAge));
  }

  public void stop() {
    if(usersCmd != null) {
      usersCmd.unregister(); // removes the override, restoring the command
      usersCmd = null;
    }
    if(service != null) {
      service.close();
      service = null;
    }
  }

  public void runStatusCmdProxyUsers(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    // called whenever proxy-users is executed
    String[] profiles = (String[])params.get("profiles");
    UserStatus[] users = null;
    String userName = (String)params.get("name");
    boolean activeOnly = "true".equalsIgnoreCase((String)params.get("hide-inactive"));
    if(!proxy.isAdmin(user)) userName = user;
    if(userName != null) {
      UserStatus temp = proxy.getUserStatus(userName, activeOnly);
      if(temp != null) users = new UserStatus[] {temp};
    } else {
      users = proxy.getUsersStatus(profiles, activeOnly);
    }

    // this is the only difference from the default proxy-users command
    if(users != null) {
      lastRequest.clear();
      for(int i = 0; i < users.length; i++) {
        locateUser(users[i]);
        addToLocation(users[i]); // group users per location
      }
    }
		SeenEntry[] seen = proxy.getSeenUsers(null, userName, true);   

    // since we're only adding properties we can rely on the existing method for writing the xml reply
    XmlHelper.xmlFormatUsers(users, seen.length, xb);
  }

  private void addToLocation(UserStatus user) {
    String key = user.getProperty("geoip-lat") + "," + user.getProperty("geoip-long");
    List users = (List)lastRequest.get(key);
    if(users == null) {
      users = new ArrayList();
      lastRequest.put(key, users);
    }
    users.add(user);
  }

  private void locateUser(UserStatus user) {
    Set hosts = new HashSet(Arrays.asList(user.getRemoteHosts()));
    // just get the first ip for now
    Iterator iter = hosts.iterator();
    if(iter.hasNext()) {
      Location loc = service.getLocation((String)iter.next());
      if(loc != null) {
        user.setProperty("geoip-lat", String.valueOf(loc.latitude));
        user.setProperty("geoip-long", String.valueOf(loc.longitude));
        user.setProperty("geoip-city", loc.city == null ? "Unknown" : String.valueOf(loc.city));
      }
    }
  }

  public String getName() {
    return "GeoipPlugin";
  }

  public String getDescription() {
    return "Adds geoip information to user ip addresses, displayed using google maps.";
  }
  
  public Properties getProperties() {
    Properties p = new Properties();
    p.setProperty("database", dbInfo);
    return p;
  }

  public CamdNetMessage doFilter(ProxySession proxySession, CamdNetMessage msg) {
    return msg;
  }

  public byte[] getResource(String path, boolean admin) {
    if(path.startsWith("/")) path = path.substring(1);
    try {
      DataInputStream dis = new DataInputStream(GeoipPlugin.class.getResourceAsStream("/web/" + path));
      byte[] buf = new byte[dis.available()];
      dis.readFully(buf);
      if("googlemap.html".equals(path)) { // insert key & js, replaces the {0} and {1} identifiers in googlemap.html
        String text = MessageFormat.format(new String(buf, "UTF-8"), new Object[] {gmapsKey, generateJs()});
        buf = text.getBytes("UTF-8");
      } else if("load.js".equals(path)) { // insert database age
        StringBuffer sb = new StringBuffer("var dbInfo = ");
        sb.append("'").append(dbInfo).append("'\n");
        sb.append(new String(buf, "UTF-8"));
        buf = sb.toString().getBytes("UTF-8");
      }
      return buf;
    } catch (IOException e) {
      return null;
    }    
  }

  public byte[] getResource(String path, byte[] inData, boolean admin) {
    return null;
  }

  private String generateJs() {  
    UserStatus user; StringBuffer sb = new StringBuffer();
    // ok this is pretty silly
    sb.append("function load() {\n");
    sb.append("if(GBrowserIsCompatible()) {\n");
    sb.append("var map = new GMap2(document.getElementById('map'));\n");
    sb.append("map.addControl(new GLargeMapControl());\n");
    sb.append("map.addControl(new GMapTypeControl());\n");
    sb.append("map.setCenter(new GLatLng(").append(startLat).append(", ").append(startLong).append("), ");
    sb.append(startZoom).append(");\n");
    int i = 0; String key; List users;
    for(Iterator iter = lastRequest.keySet().iterator(); iter.hasNext(); ) {
      i++;
      key = (String)iter.next();
      users = (List)lastRequest.get(key);
      user = (UserStatus)users.iterator().next();
      sb.append("marker").append(i).append(" = new GMarker(new GLatLng(").append(user.getProperty("geoip-lat")).append(", ");
      sb.append(user.getProperty("geoip-long")).append("));\n");
      sb.append("map.addOverlay(marker").append(i).append(");\n");
      sb.append("GEvent.addListener(marker").append(i).append(", 'click', function() {\n");
      sb.append("marker").append(i).append(".openInfoWindowHtml(\"");
      sb.append("<div style='font-family:Arial;font-size:8pt'>");
      for(Iterator u = users.iterator(); u.hasNext(); ) {
        user = (UserStatus)u.next();
        sb.append("User: <strong>").append(user.getUserName());
        sb.append("</strong> IP: <strong>");
        sb.append(com.bowman.cardserv.util.CustomFormatter.formatAddress(user.getRemoteHosts()[0])); 
        sb.append("</strong><br/>");
      }
      sb.append("<br/>City: <strong>").append(user.getProperty("geoip-city")).append("</strong></div>\");");
      sb.append("});\n");
    }
    sb.append("}\n}\n");
    return sb.toString();
  }

}
