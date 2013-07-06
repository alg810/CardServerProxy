package com.bowman.cardserv.tv;

import com.bowman.util.IniFile;

import java.util.*;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Feb 15, 2009
 * Time: 7:55:21 PM
 */
public class DvbviewerParser {

  private String fileName;

  public DvbviewerParser(String fileName) {
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

    IniFile iniFile = new IniFile(fileName);
    Map services = new HashMap();

    String section; TvService service; int[] tokens = new int[6]; Integer sid; String name;
    for(Iterator iter = iniFile.getSections().iterator(); iter.hasNext(); ) {
      section = (String)iter.next();

      if(filterSet == null || filterSet.contains(iniFile.getProperty(section, "Root").toLowerCase())) {

        tokens[0] = Integer.parseInt(iniFile.getProperty(section, "SID"));
        tokens[1] = 0;
        tokens[2] = Integer.parseInt(iniFile.getProperty(section, "StreamID"));
        tokens[3] = Integer.parseInt(iniFile.getProperty(section, "NetworkID"));
        tokens[4] = TvService.TYPE_TV;
        tokens[5] = Integer.parseInt(section.substring(section.lastIndexOf('l') + 1));

        service = new TvService(tokens, profile);
        name = iniFile.getProperty(section, "Name");
        if(name.indexOf('(') != -1) name = name.substring(0, name.indexOf('('));
        service.setName(name);

        if(networkId <= 0 || networkId == tokens[3]) {
          sid = new Integer(tokens[0]);
          if(filterSet != null) service.setNetworkId(networkId); // filter was set, use networkid from profile instead
          if(!services.containsKey(sid)) services.put(sid, service);
        }

      }
    }
    return services;
  }

  public int getConflicts() {
    return 0;
  }

}
