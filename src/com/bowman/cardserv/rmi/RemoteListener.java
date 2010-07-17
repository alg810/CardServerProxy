package com.bowman.cardserv.rmi;

import java.rmi.*;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 24, 2005
 * Time: 2:09:24 PM
 */
public interface RemoteListener extends Remote {

  public static final int C_ECMCOUNT = 0, C_ECMFORWARDS = 1, C_ECMCACHEHITS = 2, C_ECMFAILURES = 3;
  public static final int C_EMMCOUNT = 4, C_ECMDENIED = 5, C_ECMFILTERED = 6, C_ECMRATE = 7, C_PROBEQ = 8;

  void eventRaised(RemoteEvent event) throws RemoteException;

}
