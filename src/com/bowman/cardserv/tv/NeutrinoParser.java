package com.bowman.cardserv.tv;

import com.bowman.xml.XMLConfig;

import java.util.*;
import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Aug 8, 2010
 * Time: 9:42:17 PM
 */
public class NeutrinoParser {

  private String fileName;
  private int conflicts;

  public NeutrinoParser(String fileName) {
    this.fileName = fileName;
  }

  public Map parse(String filter, String profile, int networkId) throws IOException {

    Set filterSet = null;
    if(filter != null) {
      String[] s = filter.toLowerCase().split(",");
      filterSet = new HashSet();
      for(int i = 0; i < s.length; i++) filterSet.add(s[i].trim().toLowerCase());
    }

    if(filterSet != null && filterSet.isEmpty()) filterSet = null;

    XMLConfig xml = new XMLConfig(new FixingFileInputStream(fileName), false, "UTF-8");
    Map services = new HashMap();

    XMLConfig sat, tp, chan; TvService service; int[] tokens = new int[6]; int onid, tid, i = 1;

    String api = xml.getString("api");

    String transponderStr, idStr, onidStr, channelStr, serviceIdStr, serviceTypeStr, serviceNameStr;

    if(api != null && Integer.parseInt(api) == 3) {
      // zapit api version 3 used by Neutrino HD
      transponderStr = "TS";
      idStr = "id";
      onidStr = "on";
      channelStr = "S";
      serviceIdStr = "i";
      serviceTypeStr = "t";
      serviceNameStr = "n";
    }
    else {
      transponderStr = "transponder";
      idStr = "id";
      onidStr = "onid";
      channelStr = "channel";
      serviceIdStr = "service_id";
      serviceTypeStr = "service_type";
      serviceNameStr = "name";
    }

    sat = xml.getSubConfig("sat");

    Enumeration e;

    // check if satelite or cable config
    if(sat != null) {
      e = xml.getMultipleSubConfigs("sat");
    } else {
      e = xml.getMultipleSubConfigs("cable");
    }

    while(e.hasMoreElements()) {
      sat = (XMLConfig)e.nextElement();

      if(filterSet == null || matchesFilter(sat.getString("name"), filterSet)) {

        for(Enumeration n = sat.getMultipleSubConfigs(transponderStr); n.hasMoreElements(); ) {
          tp = (XMLConfig)n.nextElement();
          tid = Integer.parseInt(tp.getString(idStr), 16);
          onid = Integer.parseInt(tp.getString(onidStr), 16);

          if(filterSet != null || networkId == onid) {
            // manual filter match or matching onid -> proceed with services

            for(Enumeration m = tp.getMultipleSubConfigs(channelStr); m.hasMoreElements(); ) {
              chan = (XMLConfig)m.nextElement();
              tokens[0] = Integer.parseInt(chan.getString(serviceIdStr), 16);
              tokens[1] = 0;
              tokens[2] = tid;
              tokens[3] = filterSet==null?onid:networkId; // if a filter was set the intent was to override, so use id from profile
              tokens[4] = Integer.parseInt(chan.getString(serviceTypeStr), 16);
              tokens[5] = i++;

              service = new TvService(tokens, profile);
              service.setName(chan.getString(serviceNameStr));

              addService(services, service);
            }
          }
        }
      }
    }

    return services;
  }

  private static boolean matchesFilter(String s, Set filters) {
    s = s.toLowerCase();
    for(Iterator iter = filters.iterator(); iter.hasNext(); ) {
      if(s.startsWith((String)iter.next())) return true;
    }
    return false;
  }

  private void addService(Map services, TvService service) {
    if(!service.isTv()) return; // ignore data, other
    Integer i = new Integer(service.getId());
    if(services.containsKey(i)) {
      TvService existing = (TvService)services.get(i);
      if(!service.getName().equalsIgnoreCase(existing.getName())) {
        existing.addConflicting(service);
        conflicts++;
      }
    } else services.put(i, service);
  }

  public int getConflicts() {
    return conflicts;
  }

  // seems neutrino wasn't too picky about xml validation - wrapper to remove any occurances of illegal chars from the data read
  private static class FixingFileInputStream extends FileInputStream {

    public FixingFileInputStream(String name) throws FileNotFoundException {
      super(name);
    }

    public int read(byte[] b, int off, int len) throws IOException {
      int count = super.read(b, off, len);
      int max = off + count;
      for(int i = off; i < max; i++)
        if(b[i] >= 0x00 && b[i] <= 0x06) b[i] = 0x20; // replace with space
      return count;
    }
  }

}
