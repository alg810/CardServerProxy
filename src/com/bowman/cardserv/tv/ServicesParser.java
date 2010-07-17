package com.bowman.cardserv.tv;

import java.io.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 16, 2005
 * Time: 6:51:52 AM
 */
public class ServicesParser {

  private File servicesFile;
  public int conflicts;

  public ServicesParser(String fileName) {
    servicesFile = new File(fileName);
  }

  public Map parse(String profile, int networkId) throws IOException {
    return parse(null, profile, networkId);
  }

  public Map parse(String providers, String profile, int networkId) throws IOException {
    conflicts = 0;

    Set providerSet = null;
    if(providers != null) {
      String[] s = providers.toLowerCase().split(",");
      providerSet = new HashSet();
      for(int i = 0; i < s.length; i++) providerSet.add(s[i].trim().toLowerCase());
    }

    if(providerSet != null && providerSet.isEmpty()) providerSet = null;

    Map services = new HashMap();
    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(servicesFile), "UTF-8"));

    boolean servicesFound = false;
    String[] tokens;
    TvService service;
    String line;
    while((line = br.readLine()) != null) {
      line = line.trim();
      if(servicesFound) {
        if("end".equals(line)) break;
        tokens = line.split(":");
        service = new TvService(tokens, profile);
        service.setName(br.readLine());
        service.setProviderStr(br.readLine());
        if(networkId != 0 && service.getNetworkId() != networkId)
          if(providerSet == null) continue; // skip other networks if no custom provider string filter is set
        if(providerSet != null) service.setNetworkId(networkId); // filter was set, use networkid from profile instead
        if(providerSet == null || providerSet.contains(service.getProvider().toLowerCase())) {
          addService(services, service);
        }
      }
      if("services".equals(line)) servicesFound = true;

    }
    return services;
  }

  public int getConflicts() {
    return conflicts;
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

}
