package com.bowman.cardserv;

import java.io.Serializable;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Apr 12, 2009
 * Time: 10:08:37 PM
 */
public class BoxRegistry implements Serializable {

  private Map boxes = new HashMap();

  public void registerBox(BoxMetaData box) {
    boxes.put(box.getBoxId(), box);
  }

  public BoxMetaData[] findBox(String macAddr, String user, boolean activeOnly) {
    if(macAddr == null && user == null && !activeOnly) return (BoxMetaData[])boxes.values().toArray(new BoxMetaData[boxes.size()]);
    List foundBoxes = new ArrayList(); BoxMetaData box;
    for(Iterator iter = boxes.values().iterator(); iter.hasNext(); ) {
      box = (BoxMetaData)iter.next();
      if( (macAddr == null || box.getMacAddr().equals(macAddr)) &&
          (user == null || box.getUser().equals(user)) )
      {
        if(activeOnly) {
          if(box.isActive()) foundBoxes.add(box);
        } else foundBoxes.add(box);
      }
    }
    return (BoxMetaData[])foundBoxes.toArray(new BoxMetaData[foundBoxes.size()]);
  }

  public BoxMetaData[] findBox(String macAddr, String user) {
    return findBox(macAddr, user, false);
  }

  public BoxMetaData[] findBox(String user) {
    return findBox(null, user);
  }

  public BoxMetaData[] findBox(String user, boolean activeOnly) {
    return findBox(null, user, activeOnly);
  }

  public BoxMetaData getBox(String boxId) {
    return (BoxMetaData)boxes.get(boxId);
  }

  public int size() {
    return boxes.size();
  }

  public void removeBox(String boxId) {
    boxes.remove(boxId);    
  }

  public int getActiveCount() {
    return findBox(null, null, true).length;
  }
}
