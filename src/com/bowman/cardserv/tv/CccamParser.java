package com.bowman.cardserv.tv;

import java.io.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Apr 21, 2009
 * Time: 6:57:40 PM
 */
public class CccamParser {
  
  private File chanInfoFile;
  public int conflicts;

  public CccamParser(String fileName) {
    chanInfoFile = new File(fileName);
  }

  public Map parse(String caId, String providers, String profile, String networkIdStr) throws IOException {
    conflicts = 0;
    if("".equals(providers)) providers = null;
    Set providerSet = providers==null?Collections.EMPTY_SET:new HashSet(Arrays.asList(providers.toLowerCase().split(" ")));

    Map services = new HashMap();
    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(chanInfoFile), "ISO-8859-1"));

    String[] tokens;
    TvService service;
    String line;
    while((line = br.readLine()) != null) {
      line = line.trim();
      if(line.startsWith("#") || line.length() == 0) continue;

      tokens = line.split("\"");
      if(tokens.length != 2) continue;
      String name = tokens[1].trim();
      name = name.split("\\[")[0].trim();
      int idx = name.indexOf('-');
      if(idx != -1 && !name.endsWith("-")) name = name.split("-")[1].trim();
      tokens = tokens[0].trim().split(":");

      if(!tokens[0].equalsIgnoreCase(caId)) continue;
      if(!providerSet.isEmpty() && !providerSet.contains(tokens[1].toLowerCase())) continue;

      service = new TvService(new String[] {tokens[2], "00000000", "0000", networkIdStr, "1", "0"}, profile);
      service.setName(name);

      addService(services, service);

    }
    return services;
  }

  public int getConflicts() {
    return conflicts;
  }

  private void addService(Map services, TvService service) {
    Integer i = new Integer(service.getId());
    if(services.containsKey(i)) {
      TvService existing = (TvService)services.get(i);
      if(!service.getName().equalsIgnoreCase(existing.getName())) {
        existing.addConflicting(service);
        conflicts++;
      }

    } else services.put(i, service);
  }

}
