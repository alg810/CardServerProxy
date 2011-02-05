package com.bowman.cardserv.rmi;

import com.bowman.cardserv.session.SeenEntry;
import com.bowman.cardserv.tv.TvService;

import java.rmi.*;


/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Oct 23, 2005
 * Time: 10:54:31 PM
 */
public interface RemoteProxy extends Remote {

  void addRemoteListener(RemoteListener listener) throws RemoteException;
  void addRemoteListener(RemoteListener listener, String profileName) throws RemoteException;
  void removeRemoteListener(RemoteListener listener) throws RemoteException;

  int getCwsCount(String[] profiles) throws RemoteException;
  int getSessionCount(String[] profiles, boolean activeOnly) throws RemoteException;
  int getCwsCapacity(String[] profiles) throws RemoteException;
  long getProxyStartTime() throws RemoteException;

  CwsStatus getCwsStatus(String cwsName) throws RemoteException;
  CwsStatus[] getMultiCwsStatus(String[] profiles) throws RemoteException;
  UserStatus[] getUsersStatus(String[] profiles, boolean activeOnly) throws RemoteException;
  SeenEntry[] getSeenUsers(String[] profiles, String userName, boolean failures) throws RemoteException;
  UserStatus getUserStatus(String userName, boolean activeOnly) throws RemoteException;
  TvService[] getServices(String cwsName, boolean merge) throws RemoteException;
  TvService[] getCannotDecodeServices(String cwsName) throws RemoteException;
  TvService[] getWatchedServices(String[] profiles) throws RemoteException;
  ProfileStatus[] getProfiles() throws RemoteException;
  CacheStatus getCacheStatus() throws RemoteException;
  PluginStatus[] getPlugins() throws RemoteException;

  String getUserPasswd(String userName) throws RemoteException;
  ProfileStatus[] getUserProfiles(String userName) throws RemoteException;
  boolean authenticateUser(String userName, String pass) throws RemoteException;
  boolean isAdmin(String userName) throws RemoteException;
  String getEmailAddress(String userName) throws RemoteException;
  String[] getLocalUsers() throws RemoteException;
  String[] getLocalUsers(String profileName) throws RemoteException;

  boolean resetStatus(String profileName, int serviceId, long customData) throws RemoteException;
  int resetStatus(String cwsName, boolean full) throws RemoteException;
  int kickUser(String userName) throws RemoteException;
  void shutdown() throws RemoteException;
  int sendOsdMessage(String userName, String message) throws RemoteException;
  void retryConnector(String cwsName) throws RemoteException;
  void disableConnector(String cwsName) throws RemoteException;
  void setConnectorMetric(String name, int metric) throws RemoteException;
  void setProfileDebug(boolean debug, String profileName) throws RemoteException;
  boolean setUserDebug(boolean debug, String userName) throws RemoteException;

  String getName() throws RemoteException;
  int[] getCounters() throws RemoteException;

  int removeSeenUser(String name) throws RemoteException;
  int removeLoginFailure(String mask) throws RemoteException;
}
