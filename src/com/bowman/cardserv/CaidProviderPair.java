package com.bowman.cardserv;

import com.bowman.cardserv.session.ExtNewcamdSession;
import com.bowman.cardserv.crypto.DESUtil;

/**
 * Created by IntelliJ IDEA.
* User: johan
* Date: Jan 18, 2010
* Time: 11:12:48 AM
*/
public class CaidProviderPair {

  public int caId, providerIdent;

  public CaidProviderPair(int caId, int providerIdent) {
    this.caId = caId;
    this.providerIdent = providerIdent;
  }

  public boolean equals(Object o) {
    if(this == o) return true;
    if(o == null || getClass() != o.getClass()) return false;
    CaidProviderPair that = (CaidProviderPair)o;
    if(caId != that.caId) return false;
    if(providerIdent != that.providerIdent) return false;
    return true;
  }

  public int hashCode() {
    int result = caId;
    result = 31 * result + providerIdent;
    return result;
  }

  public String toString() {
    return DESUtil.intToHexString(caId, 4) + ":" + DESUtil.intToByteString(providerIdent, 3);
  }
}
