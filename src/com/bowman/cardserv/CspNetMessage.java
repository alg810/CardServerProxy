package com.bowman.cardserv;

import com.bowman.cardserv.cws.ServiceMapping;
import com.bowman.cardserv.crypto.DESUtil;

import java.util.*;
import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Jan 6, 2010
 * Time: 7:33:15 AM
 */
public class CspNetMessage implements Serializable {

  public static final int TYPE_NEW = 0, TYPE_ECMREQ = 1, TYPE_DCWRPL = 2;
  public static final int TYPE_FULLSTATE = 3, TYPE_STATEACK = 4;
  public static final int TYPE_INCRSTATE = 5;

  public static final int STATE_DELETE = 0xFF, STATE_SIDS = 1, STATE_PROVIDERS = 2, STATE_CUSTOM = 3;

  private int type, seqNr, originId;

  private List statusUpdates;
  private Map statusLookup;
  private CamdNetMessage camdMessage;

  public static void parseStatusChange(DataInputStream is, CspNetMessage msg) throws IOException {
    msg.originId = is.readInt(); // 4 bytes, id of remote proxy
    int count = is.readUnsignedByte(); // 1 byte, number of status updates in this message

    StatusChange sc;
    msg.statusUpdates = new ArrayList(count); msg.statusLookup = new HashMap();

    int operation, stateType, itemCount;
    int onid, caid; List updates; ProfileKey key;
    for(int i = 0; i < count; i++) {
      operation = is.readUnsignedByte(); // 1 byte, 0 for remove/cannot decode, 1 for add/can decode
      stateType = is.readUnsignedByte(); // 1 byte, type of state update
      onid = is.readUnsignedShort(); // 2 bytes, dvb original network id
      caid = is.readUnsignedShort(); // 2 bytes, ca-id
      key = new ProfileKey(onid, caid);

      updates = (List)msg.statusLookup.get(key);
      if(updates == null) {
        updates = new ArrayList();
        msg.statusLookup.put(key, updates);
      }
      sc = new StatusChange(key, stateType, operation);
      itemCount = is.readUnsignedShort(); // 2 bytes, number of items affected (0 = this is just a notification for the onid, no known state)

      if(itemCount > 0) {
        switch(stateType) {
          case STATE_PROVIDERS:
            Integer[] items = new Integer[itemCount];
            for(int n = 0; n < itemCount; n++) items[n] = new Integer(is.readInt());
            sc.setUpdatedItems(items);
            break;
          case STATE_SIDS:
            Integer[] shortItems = new Integer[itemCount];
            for(int n = 0; n < itemCount; n++) shortItems[n] = new Integer(is.readUnsignedShort());
            sc.setUpdatedItems(shortItems);
            break;
          case STATE_CUSTOM:
            Long[] longItems = new Long[itemCount];
            for(int n = 0; n < itemCount; n++) longItems[n] = new Long(is.readLong());
            sc.setUpdatedItems(longItems);
            break;
          default:
            throw new IOException("unknown state type:" + stateType);
        }
      }

      msg.statusUpdates.add(sc);
      updates.add(sc);
    }
  }

  public static int statusHashCode(List updates) {
    String s = updates.toString();
    // System.out.println(s.hashCode() + " -> " + s);
    return updates.toString().hashCode();
  }

  public static List buildProfileUpdate(CaProfile profile) {

    if(profile.getNetworkId() <= 0 || profile.getCaId() <= 0) return null;

    // todo CaProfile.MULTIPLE

    List updates = new ArrayList();
    ProfileKey key = new ProfileKey(profile);

    // split mappings into sid and cid to avoid sending custom if not present for profile
    ServiceMapping[] sm = profile.getServices(true);
    Integer[] sids = new Integer[sm.length];
    Long[] customs = new Long[sm.length];
    boolean hasCustom = false;
    for(int i = 0; i < sm.length; i++) {
      sids[i] = new Integer(sm[i].serviceId);
      customs[i] = new Long(sm[i].getCustomData());
      if(sm[i].getCustomId() != 0 || sm[i].getProviderIdent() != ServiceMapping.NO_PROVIDER) hasCustom = true;
    }
    StatusChange sc;
    for(int i = 1; i < 4; i++) {
      sc = new StatusChange(key, i, 1);
      switch(i) {
        case STATE_SIDS:
          sc.setUpdatedItems(sids);
          break;
        case STATE_PROVIDERS:
          sc.setUpdatedItems(profile.getProviderIdents());
          break;
        case STATE_CUSTOM:
          if(hasCustom) sc.setUpdatedItems(customs);
          break;
      }
      updates.add(sc);
    }

    // special case for sids already known not to decode
    hasCustom = false;
    sm = profile.getServices(false);
    sids = new Integer[sm.length]; customs = new Long[sm.length];
    for(int i = 0; i < sm.length; i++) {
      sids[i] = new Integer(sm[i].serviceId);
      customs[i] = new Long(sm[i].getCustomData());
      if(sm[i].getCustomId() != 0 || sm[i].getProviderIdent() != ServiceMapping.NO_PROVIDER) hasCustom = true;
    }
    sc = new StatusChange(key, STATE_SIDS, 0);
    sc.setUpdatedItems(sids);
    updates.add(sc);
    if(hasCustom) {
      sc = new StatusChange(key, STATE_CUSTOM, 0);
      sc.setUpdatedItems(customs);
      updates.add(sc);
    }

    return updates;
  }

  public static List getStatusItems(int type, boolean available, List updates) {
    if(updates == null) return null;
    StatusChange sc;
    for(Iterator iter = updates.iterator(); iter.hasNext(); ) {
      sc = (StatusChange)iter.next();
      if(sc.type == type && sc.available == available) return sc.list;
    }
    return Collections.EMPTY_LIST;
  }

  public static boolean isDeletion(List updates) {
    if(updates == null || updates.isEmpty()) return false;
    StatusChange sc = (StatusChange)updates.get(0);
    return sc.type == STATE_DELETE;
  }

  public CspNetMessage(int type) {
    this.type = type;
  }

  public CspNetMessage(int type, int seqNr) {
    this(type);
    this.seqNr = seqNr;
  }

  public int getType() {
    return type;
  }

  public int getSeqNr() {
    return seqNr;
  }

  public void setSeqNr(int seqNr) {
    this.seqNr = seqNr;
  }

  public CamdNetMessage getCamdMessage() {
    return camdMessage;
  }

  public void setCamdMessage(CamdNetMessage camdMessage) {
    this.camdMessage = camdMessage;
  }

  public int getOriginId() {
    return originId;
  }

  public void setOriginId(int originId) {
    this.originId = originId;
  }

  public boolean isKeepAlive() {
    return type == TYPE_INCRSTATE && isEmpty();
  }

  public boolean isEmpty() {
    return statusUpdates == null || statusUpdates.isEmpty();
  }

  public int getUpdateCount() {
    if(statusUpdates == null) return 0;
    else return statusUpdates.size();
  }

  public List getStatusUpdates() {
    return statusUpdates;
  }

  public List getStatusUpdatesForKey(ProfileKey key) {
    return (List)statusLookup.get(key);
  }

  public Set getProfileKeys() {
    return statusLookup.keySet();
  }

  public void addStatusUpdate(StatusChange sc) {
    if(statusUpdates == null) statusUpdates = new ArrayList();
    statusUpdates.add(sc);
  }

  public void addSidUpdate(ProfileKey key, Integer sid, boolean available) {
    StatusChange sc = new StatusChange(key, STATE_SIDS, available?1:0);
    sc.setUpdatedItems(new Integer[] {sid});
    addStatusUpdate(sc);
  }

  public void addDeleteUpdate(ProfileKey key) {
    StatusChange sc = new StatusChange(key, STATE_DELETE, 0);
    addStatusUpdate(sc);
  }

  public void addStatusUpdates(ProfileKey key, List updates) {
    if(statusUpdates == null) statusUpdates = new ArrayList();
    if(statusLookup == null) statusLookup = new HashMap();
    statusUpdates.addAll(updates);
    statusLookup.put(key, updates);
  }

  public void setStatusUpdates(List statusUpdates) {
    this.statusUpdates = statusUpdates;
  }

  public static class StatusChange implements Serializable {

    ProfileKey key;
    int type;
    boolean available; // add or remove
    // private Set set = new TreeSet();
    private List list = new ArrayList();

    public StatusChange(ProfileKey key, int type, int available) {
      this.key = key;
      this.type = type;
      this.available = (available == 1);
    }

    public void setUpdatedItems(Integer[] items) {
      list.addAll(Arrays.asList(items));
    }

    public void setUpdatedItems(Long[] items) {
      list.addAll(Arrays.asList(items));
    }

    public Integer[] getUpdatedItemsInt() {
      return (Integer[])list.toArray(new Integer[list.size()]);
    }

    public Long[] getUpdatedItemsLong() {
      return (Long[])list.toArray(new Long[list.size()]);
    }

    public String toString() {
      if(list.isEmpty()) return "";
      else return type + " " + list;
    }

  }

  public static class ProfileKey implements Serializable {
    public int onid, caid;

    public ProfileKey(String s) {
      String[] pair = s.split("-");
      onid = Integer.parseInt(pair[0], 16);
      caid = Integer.parseInt(pair[1], 16);
    }

    public ProfileKey(CaProfile profile) {
      this.onid = profile.getNetworkId();
      this.caid = profile.getCaId();
    }

    public ProfileKey(int onid, int caid) {
      this.onid = onid;
      this.caid = caid;
    }

    public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;
      ProfileKey that = (ProfileKey)o;
      if(caid != that.caid) return false;
      if(onid != that.onid) return false;
      return true;
    }

    public int hashCode() {
      int result = onid;
      result = 31 * result + caid;
      return result;
    }

    public String toString() {
      return DESUtil.intToHexString(onid, 4) + "-" + DESUtil.intToHexString(caid, 4);
    }
  }
}
