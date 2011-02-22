package com.bowman.cardserv;

import com.bowman.cardserv.interfaces.*;
import com.bowman.cardserv.util.*;
import com.bowman.cardserv.rmi.*;
import com.bowman.cardserv.web.*;
import com.bowman.cardserv.web.Command;
import com.bowman.xml.XMLConfig;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.rmi.RemoteException;


/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Mar 20, 2009
 * Time: 2:05:17 PM
 */
public class DreamboxPlugin implements ProxyPlugin {

  private static String AGENT_VERSION;
  public static final String PLUGIN_JAR = "plugins/dreamboxplugin.jar";

  protected RemoteHandler proxy;
  protected ProxyLogger logger;
  protected int checkInterval;

  private BoxRegistry registry = new BoxRegistry();
  private File registryFile;
  private Set commands = new HashSet();
  private AgentWeb web;

  private AgentSshd sshd;
  private static final String[] sshdDeps = {
      "http://repo1.maven.org/maven2/org/apache/sshd/sshd-core/0.3.0/sshd-core-0.3.0.jar",
      "http://repo1.maven.org/maven2/org/apache/mina/mina-core/2.0.0-M6/mina-core-2.0.0-M6.jar",
      "http://repo2.maven.org/maven2/org/slf4j/slf4j-api/1.5.2/slf4j-api-1.5.2.jar",
      "http://repo2.maven.org/maven2/org/slf4j/slf4j-jdk14/1.5.2/slf4j-jdk14-1.5.2.jar"
  };

  private Set scripts = new HashSet();

  public DreamboxPlugin() {
    logger = ProxyLogger.getLabeledLogger(getClass().getName());
    registryFile = new File("etc", "registry.dat");
  }

  public void configUpdated(ProxyXmlConfig xml) throws ConfigException {
    ProxyXmlConfig sshdXml = null;
    try {
      sshdXml = xml.getSubConfig("agent-sshd");
    } catch (ConfigException e) {}
    
    if(sshdXml != null && "true".equalsIgnoreCase(sshdXml.getStringValue("enabled", "true"))) {
      if(sshd == null) {
        try {
          PluginClassLoader pcl = (PluginClassLoader)getClass().getClassLoader();
          pcl.resolveDependencies(sshdDeps, logger);
          sshd = new AgentSshd(this);
          sshd.configUpdated(sshdXml);
        } catch(Exception e) {
          logger.severe("Failed to load sshd: " + e, e);
          sshd = null;
        }
      }
    } else {
      if(sshd != null) {
        sshd.stop();
        sshd = null;
      }
    }

    if(web == null) web = new AgentWeb(this);
    web.configUpdated(xml.getSubConfig("agent-web"));    

    checkInterval = xml.getIntValue("check-interval", 5) * 60;

    listScripts();
  }

  private void listScripts() {
    scripts.clear();
    String path = "web/open/scripts/";
    try {
      ZipFile jar = new ZipFile(PLUGIN_JAR);
      ZipEntry entry;
      for(Enumeration e = jar.entries(); e.hasMoreElements();) {
        entry = (ZipEntry)e.nextElement();
        if(entry.getName().startsWith(path) && !entry.getName().endsWith("/")) {
          scripts.add(entry.getName().substring(path.length()));
        }
      }          
    } catch(IOException e) {
      logger.warning("Failed to list scripts in jar: " + e);
      logger.throwing(e);
    }
  }

  private Set listScriptsDir() {
    Set set = new HashSet();
    File dir = new File("dreamboxplugin", "scripts");
    if(dir.exists() && dir.isDirectory()) set.addAll(Arrays.asList(dir.list()));
    return set;
  }

  public byte[] getFile(String path) {
    byte[] buf = getResource("open/" + path, false);
    if(buf == null) {
      String[] tokens = path.split("/");
      if(tokens.length == 2) {
        File dir = new File("dreamboxplugin", tokens[0]);
        if(dir.exists() && dir.isDirectory()) {
          File file = new File(dir, tokens[1]);
          if(file.exists() && file.length() > 0) {
            buf = new byte[(int)file.length()];
            try {
              DataInputStream dis = new DataInputStream(new FileInputStream(file));
              dis.readFully(buf);
              dis.close();
            } catch(IOException e) {
              logger.warning("Failed to read file '" + file.getAbsolutePath() + "': " + e);
              logger.throwing(e);
            }
          }
        }
      }
    }
    return buf;
  }

  public int getTunnelPort() {
    if(sshd == null) return -1;
    else return sshd.findTunnelPort();
  }

  public int getSshdPort() {
    if(sshd == null) return -1;
    else return sshd.getPort();
  }

  public static String getBoxCpuArch(BoxMetaData box) {
    if(box == null) return null;
    else return box.getProperty("machine");
  }

  public void start(CardServProxy proxy) {
    this.proxy = proxy.getRemoteHandler();
    loadRegistry();
    registerCommands();
    web.start();
    if(sshd != null) try {
      sshd.start();
    } catch(IOException e) {
      logger.warning("Failed to start sshd: " + e);
      logger.throwing(e);
    }
    createDirs();
  }

  protected void createDirs() {
    boolean success = true;
    File dir = new File("dreamboxplugin");
    if(!dir.exists()) success = success && dir.mkdirs();
    File scripts = new File(dir, "scripts");
    if(!scripts.exists()) success = success && scripts.mkdirs();
    File binaries = new File(dir, "binaries");
    if(!binaries.exists()) success = success && binaries.mkdirs();
    if(!success) logger.warning("Failed to initialize dir structure: dreamboxplugin/");
    else {
      try {
        byte[] buf = getResource("fetch_binaries.sh", false);
        File script = new File(dir, "fetch_binaries.sh");
        if(!script.exists()) {
          FileOutputStream fos = new FileOutputStream(script);
          fos.write(buf);
          fos.close();
        }
        buf = getResource("test.sh", false);
        script = new File(scripts, "test.sh");
        if(!script.exists()) {
          FileOutputStream fos = new FileOutputStream(script);
          fos.write(buf);
          fos.close();
        }
        buf = getResource("setup_mgcamd.sh", false);
        script = new File(scripts, "setup_mgcamd.sh");
        if(!script.exists()) {
          FileOutputStream fos = new FileOutputStream(script);
          fos.write(buf);
          fos.close();
        }
        // script.setExecutable(true);
      } catch(IOException e) {
        logger.warning("Exception extracting helper scripts to 'dreamboxplugin/': " + e);
        logger.throwing(e);
      }
    }
  }

  protected void registerCommands() {
    try {
      commands.addAll(XmlHelper.registerControlCommands(DreamboxPlugin.class.getResourceAsStream("ctrl-commands.xml"), this, null));
    } catch (Exception e) {
      logger.severe("Failed to load/parse internal control commands (ctrl-commands.xml).", e);
    }
    try {
      commands.addAll(XmlHelper.registerStatusCommands(DreamboxPlugin.class.getResourceAsStream("status-commands.xml"), this, null));
    } catch (Exception e) {
      logger.severe("Failed to load/parse internal status commands (status-commands.xml).", e);
    }
  }

  protected void loadRegistry() {
    if(registryFile.exists()) {
      try {
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(registryFile)));
        registry = (BoxRegistry)ois.readObject();
        logger.fine("Loaded registry, " + registry.size() + " entries.");
        ois.close();
      } catch (Exception e) {
        logger.throwing(e);
        logger.warning("Failed to load registry ('" + registryFile.getPath() + "'): " + e);
      }
    }
  }

  protected void saveRegistry() {
    try {
      ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(registryFile)));
      oos.writeObject(registry);
      logger.fine("Saved box registry, " + registry.size() + " entries.");
      oos.close();
    } catch (IOException e) {
      logger.throwing(e);
      logger.warning("Failed to save registry ('" + registryFile.getPath() + "'): " + e);
    }
  }

  public void stop() {
    saveRegistry(); // todo periodic saving
    for(Iterator iter = commands.iterator(); iter.hasNext(); ) ((Command)iter.next()).unregister();
    commands.clear();
    if(web != null) {
      web.stop();
      web = null;
    }
    if(sshd != null) {
      sshd.stop();
      sshd = null;
    }
  }

  protected void registerBox(BoxMetaData box) {
    registry.registerBox(box);
  }

  protected BoxMetaData[] findBox(String macAddr, String user) {
    return registry.findBox(macAddr, user);
  }

  protected BoxMetaData getBox(String boxId) {
    return registry.getBox(boxId);
  }

  protected String getAgentVersion() {
    if(AGENT_VERSION == null) {
      try {
        String[] scriptLines = new String(getResource("open/cspagent.sh", false), "ISO-8859-1").split("\n");
        for(int i = 0; i < scriptLines.length; i++) {
          if(scriptLines[i].startsWith("AGENTV=")) {
            AGENT_VERSION = scriptLines[i].substring("AGENTV=".length());
            break;
          }
        }
      } catch(Exception e) {
        e.printStackTrace();
      }
    }
    return AGENT_VERSION == null ? "0.0" : AGENT_VERSION;
  }

  public void runStatusCmdListBoxes(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    String userName = (String)params.get("user");
    boolean activeOnly = "true".equalsIgnoreCase((String)params.get("hide-inactive"));
    boolean admin = proxy.isAdmin(user);
    if(!admin) userName = user;
    BoxMetaData[] boxes = registry.findBox(userName, activeOnly);

    xb.appendElement("boxes", "count", boxes.length).appendAttr("active", registry.getActiveCount());
    xb.appendAttr("httpd-port", web.getFrontendPort());
    if(sshd != null) xb.appendAttr("sshd-port", sshd.getPort());
    xb.appendAttr("admin", admin).endElement(false);
    for(int i = 0; i < boxes.length; i++) {
      if(sshd != null) boxes[i].setTunnelPort(sshd.getBoxPort(boxes[i].getBoxId()));
      xmlFormatBox(xb, boxes[i], false, admin);
    }
    xb.closeElement("boxes");
    if(admin) {
      Map map = new HashMap();
      Set set = new TreeSet(listScriptsDir());
      set.addAll(scripts);
      if(sshd == null) set.remove("sshtunnel.sh");
      map.put("@scripts", set);
      XmlHelper.xmlFormatOptionLists(xb, map);
    }
  }

  public void runStatusCmdBoxDetails(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    String boxId = (String)params.get("id");
    BoxMetaData box = registry.getBox(boxId);
    boolean admin = proxy.isAdmin(user);
    if(box == null) return;
    if(!admin && !box.getUser().equals(user)) return;
    if(sshd != null) box.setTunnelPort(sshd.getBoxPort(boxId));

    xmlFormatBox(xb, box, true, admin);
  }

  public void runStatusCmdInstallerDetails(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    xb.appendElement("installer", "user", user).appendAttr("path", "/installer.sh");
    xb.appendAttr("host", web.getFrontendHost()).appendAttr("port", web.getFrontendPort());
    xb.endElement(true);
  }

  public void runStatusCmdSetOperations(XmlStringBuffer xb, Map params, String user) throws RemoteException {
    if(!proxy.isAdmin(user)) xb.appendElement("error", "description", "Admin user required.", true);
    else {
      String op = (String)params.get("operation");
      String p = (String)params.get("params");
      XMLConfig xml = (XMLConfig)params.get("xml");
      BoxMetaData box;
      for(Enumeration e = xml.getMultipleSubConfigs("box"); e.hasMoreElements(); ) {
        box = registry.getBox(((XMLConfig)e.nextElement()).getString("id"));
        if(box != null) {
          if("".equals(op) || op == null) box.setPendingOperation(null);
          else box.setPendingOperation(new BoxOperation(op, p));
        }
      }
    }
  }

  public CtrlCommandResult runCtrlCmdDeleteBox(Map params) throws RemoteException {
    boolean result = false;
    String resultMsg;
    String boxId = (String)params.get("id");
    if(boxId == null) {
      resultMsg = "Missing parameter: id";
    } else {
      BoxMetaData box = registry.getBox(boxId);
      if(box == null) resultMsg = "Invalid/unknown id: " + boxId;
      else {
        registry.removeBox(boxId);
        resultMsg = "Box deleted";
        result = true;
      }
    }
    return new CtrlCommandResult(result, resultMsg, null);
  }

  public CtrlCommandResult runCtrlCmdCloseTunnel(Map params) throws RemoteException {
    boolean result = false;
    String resultMsg;
    String boxId = (String)params.get("id");
    if(boxId == null) {
      resultMsg = "Missing parameter: id";
    } else {
      if(sshd != null) {
        if(sshd.closeTunnelSession(boxId)) {
          resultMsg = "Tunnel closed";
          result = true;
        } else resultMsg = "No open tunnel found for box: " + boxId;

      } else resultMsg = "Sshd not enabled";
    }
    return new CtrlCommandResult(result, resultMsg, null);
  }

  public CtrlCommandResult runCtrlCmdAbortOperation(Map params) throws RemoteException {
    boolean result = false;
    String resultMsg;
    String boxId = (String)params.get("id");
    if(boxId == null) resultMsg = "Missing parameter: id";
    else {
      BoxMetaData box = registry.getBox(boxId);
      if(box == null) resultMsg = "Invalid/unknown id: " + boxId;
      else {
        int opId = -1 ;
        try { opId = Integer.parseInt((String)params.get("op")); } catch (Exception e) {}

        if(opId == -1) {
          if(box.getPendingOperation() != null) {
            BoxOperation op = box.getPendingOperation();
            resultMsg = "Pending operation '" + (op.isScript()?op.getScriptName():op.getCmdLine()) + "' aborted";
            box.setPendingOperation(null);
            result = true;
          } else resultMsg = "Missing parameter: op";
        } else {
          BoxOperation bo = box.getOperation(opId);
          if(bo == null) resultMsg = "No such operation: " + opId;
          else {
            bo.abort();
            resultMsg = "Operation aborted";
            result = true;
          }
        }
      }
    }
    return new CtrlCommandResult(result, resultMsg, null);
  }

  public CtrlCommandResult runCtrlCmdClearHistory(Map params) throws RemoteException {
    BoxMetaData[] boxes = registry.findBox(null);
    int count = 0;
    for(int i = 0; i < boxes.length; i++) {
      count += boxes[i].clearOperations();
    }
    return new CtrlCommandResult(true, "Deleted " + count + " operation logs", new Integer(count));
  }

  private static void xmlFormatBox(XmlStringBuffer xb, BoxMetaData box, boolean details, boolean admin) {
    if(details) xb.appendElement("box-details", "id", box.getBoxId());
    else xb.appendElement("box", "id", box.getBoxId());
    xb.appendAttr("user", box.getUser());
    xb.appendAttr("active", box.isActive());
    if(box.getTunnelPort() > 0) xb.appendAttr("tunnel-port", box.getTunnelPort());
    if(box.getPendingOperation() != null) xb.appendAttr("pending-operation", box.getPendingOperation().toString());
    if(box.getOperationCount() > 0) {
      xb.appendAttr("operation-count", box.getOperationCount());
      boolean running = false; BoxOperation op;
      for(Iterator iter = box.getOperations().iterator(); iter.hasNext(); ) {
        op = (BoxOperation)iter.next();
        if(op.isRunning()) {
          running = true;
          break;
        }
      }
      if(running) xb.appendAttr("running-operations", true);
    }
    xb.appendAttr("mac", box.getMacAddr());
    xb.appendAttr("created", XmlHelper.formatTimeStamp(box.getCreateTimeStamp()));
    xb.appendAttr("last-checkin", XmlHelper.formatTimeStamp(box.getLastCheckinTimeStamp()));
    long next = (box.getInterval() * 1000) - (System.currentTimeMillis() - box.getLastCheckinTimeStamp());
    if(next > 0) xb.appendAttr("next-checkin", XmlHelper.formatDuration(next / 1000));
    else xb.appendAttr("next-checkin", "?");
    xb.appendAttr("interval", box.getInterval());

    if(details) { // add all properties
      xb.endElement(false);
      xb.appendElement("properties"); String name;
      for(Enumeration e = box.getProperties().propertyNames(); e.hasMoreElements(); ) {
        name = (String)e.nextElement();
        xb.appendElement("property", "name", name);
        xb.appendAttr("value", box.getProperty(name)).endElement(true);
      }
      xb.closeElement("properties");
      if(admin && box.getOperationCount() > 0) xmlFormatOperations(xb, box);
      xb.closeElement(details?"box-details":"box");
    } else { // selected only
      String[] props = {"type", "agent-version", "external-ip", "local-ip", "image-guess", "sid", "onid"};
      for(int i = 0; i < props.length; i++) xb.appendAttr(props[i], box.getProperty(props[i]));
      xb.endElement(true);
    }
  }

  private static void xmlFormatOperations(XmlStringBuffer xb, BoxMetaData box) {
    xb.appendElement("operations", "count", box.getOperationCount());
    BoxOperation op;
    for(Iterator iter = box.getOperations().iterator(); iter.hasNext(); ) {
      op = (BoxOperation)iter.next();
      xb.appendElement("op", "id", op.getId());
      xb.appendAttr("script", op.isScript());
      xb.appendAttr("text", op.isScript()?op.getScriptName() + " " + op.getParams():op.getCmdLine());
      if(op.isStarted()) {
        xb.appendAttr("start", XmlHelper.formatTimeStamp(op.getStartTime()));
        if(!op.isRunning()) xb.appendAttr("stop", XmlHelper.formatTimeStamp(op.getStopTime()));
      }
      String output = op.getOutput();
      if(output != null) {
        xb.endElement(false);
        xb.appendElement("output");
        xb.appendCdata(output);
        xb.closeElement("output");
        xb.closeElement("op");
      } else xb.endElement(true);
    }
    xb.closeElement("operations");
  }
    
  public String getName() {
    return "DreamboxPlugin";
  }

  public String getDescription() {
    return "Remote monitoring and maintenance of dreamboxes or other linux STBs.";
  }
  
  public Properties getProperties() {
    return null;
  }

  public CamdNetMessage doFilter(ProxySession proxySession, CamdNetMessage msg) {
    return msg;
  }

  public byte[] getResource(String path, boolean admin) {
    if(path.startsWith("/")) path = path.substring(1);
    try {
      InputStream is = DreamboxPlugin.class.getResourceAsStream("/web/" + path);
      if(is == null) return null;
      DataInputStream dis = new DataInputStream(is);
      byte[] buf = new byte[dis.available()];
      dis.readFully(buf);
      return buf;
    } catch (IOException e) {
      return null;
    }    
  }

  public byte[] getResource(String path, byte[] inData, boolean admin) {
    return null;
  }

  public String getUserPasswd(String user) {
    if(proxy != null) {
      return proxy.getUserPasswd(user);
    } else return null;
  }

}
