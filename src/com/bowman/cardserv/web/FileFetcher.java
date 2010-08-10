package com.bowman.cardserv.web;

import com.bowman.cardserv.crypto.*;
import com.bowman.util.BasicHttpAuth;

import javax.net.ssl.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.security.cert.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2007-jan-28
 * Time: 00:45:39
 */
public class FileFetcher {

  public static final int URLCONN_TIMEOUT = 30000;

  private static Map urlConnections = new HashMap();
  private static Properties props;
  public static SSLSocketFactory socketFactory;

  static {
    try {
      SSLContext sc = SSLContext.getInstance("SSL");      
      sc.init(null, new TrustManager[] {new NaiveTrustManager()}, new java.security.SecureRandom());
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
      HttpsURLConnection.setDefaultHostnameVerifier(new NaiveHostnameVerifier());
      socketFactory = sc.getSocketFactory();      
    } catch(Exception e) {
      e.printStackTrace();
    }
    props = new Properties();
    try {
      props.load(FileFetcher.class.getResourceAsStream("filefetcher.properties"));
    } catch(Exception e) {}
  }

  static class NaiveTrustManager implements X509TrustManager {
    public X509Certificate[] getAcceptedIssuers() { return null; }
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {}
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {}
    public boolean isClientTrusted(X509Certificate[] x509Certificates) { return true; }
    public boolean isServerTrusted(X509Certificate[] x509Certificates) { return true; }
  }
  
  static class NaiveHostnameVerifier implements HostnameVerifier {
    public boolean verify(String s, SSLSession sslSession) { return true; }
  }
  
  public static String[] fetchList(URL url, String key) throws IOException {
    BufferedReader reader =  new BufferedReader(new StringReader(fetchFile(url, key, -1)));
    String line; List lines = new ArrayList();
    while((line = reader.readLine()) != null) {
      if(!line.startsWith("#")) lines.add(line);
    }

    return (String[])lines.toArray(new String[lines.size()]);
  }

  public static void fetchBinary(URL url, File targetFile) throws IOException {
    URLConnection conn = url.openConnection();
    int len = conn.getContentLength();
    if(len > 0) {
      byte[] buf = new byte[len];
      DataInputStream dis = new DataInputStream(conn.getInputStream());
      dis.readFully(buf);
      dis.close();
      if(!targetFile.exists())
        if(!targetFile.createNewFile()) throw new IOException("Failed to create file: " + targetFile);
      FileOutputStream fos = new FileOutputStream(targetFile);
      fos.write(buf);
      fos.flush();
      fos.close();
    } else throw new IOException("Content length missing.");
  }

  public static String fetchFile(URL url, String key, long lastModified) throws IOException {
    killConnections();
    URLConnection conn = url.openConnection();
    if(conn instanceof HttpURLConnection) {
      if(url.getUserInfo() != null) conn.setRequestProperty("Authorization", BasicHttpAuth.encode(url.getUserInfo()));
    }
    addConnection(conn);
    if(lastModified > 0 && conn.getLastModified() > 0) {
      if(conn.getLastModified() <= lastModified) return null;
    }
    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), getCharset(conn.getContentType())));
    StringBuffer sb = new StringBuffer();
    String line;
    while((line = reader.readLine()) != null) {
      sb.append(line);
      if(key == null) sb.append('\n');
    }
    if(key != null) {
      if("*".equals(key)) key = props.getProperty("key");
      return FishUtil.decryptString(key, sb.toString());
    } else return sb.toString();
  }

  public static void encryptFile(String inFile, String outFile, String key) throws IOException {
    File in = new File(inFile);
    DataInputStream dis = new DataInputStream(new FileInputStream(in));
    byte[] data = new byte[(int)in.length()];
    dis.readFully(data);
    dis.close();

    String encStr = FishUtil.encryptString(key, new String(data, "ISO-8859-1"));
    PrintWriter pw = new PrintWriter(new FileWriter(outFile), true);
    pw.println(encStr);
    pw.close();
  }

  private static void addConnection(URLConnection conn) {
    if(conn instanceof HttpURLConnection) urlConnections.put(new Long(System.currentTimeMillis()), conn);
  }

  private static void killConnections() {
    Long key; HttpURLConnection conn;
    for(Iterator iter = new ArrayList(urlConnections.keySet()).iterator(); iter.hasNext(); ) {
      key = (Long)iter.next();
      if(System.currentTimeMillis() - key.longValue() > URLCONN_TIMEOUT) {
        conn = (HttpURLConnection)urlConnections.remove(key);
        if(conn != null) conn.disconnect();
      }
    }
  }

  private static String getCharset(String contentType) {
    if(contentType == null) return "ISO-8859-1";
    contentType = contentType.toLowerCase();
    int indx = contentType.indexOf("charset=");
    if(indx == -1) return "ISO-8859-1";
    else return contentType.substring(indx + "charset=".length()).trim();
  }

  public static void main(String[] args) throws IOException {
    if(args.length != 3) System.err.println("Usage: java -jar fishenc.jar <inputfile> <outputfile> <key>");
    else encryptFile(args[0], args[1], args[2]);
  }




}
