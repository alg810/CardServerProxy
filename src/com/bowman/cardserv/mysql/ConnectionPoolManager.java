package com.bowman.cardserv.mysql;

import com.bowman.cardserv.ConfigException;
import com.bowman.cardserv.util.ProxyLogger;
import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;

import java.io.IOException;
import java.util.*;

/**
 * Because the usermanager gets used in a threaded environment we have to
 * make sure that each request gets his own connection. A simple solution
 * is to generate a "ConnectionPool" for all Database Connections.
 *
 * @author DonCarlo
 * @since 07.12.2010
 */
public class ConnectionPoolManager extends Thread {

  /* this parameter describes the idle time after which the connection should be seen as unused and therefor should be
  closed and removed from the connectionPool */
  private static final long INACTIVE_TIME = 60000;
  private static final long CLEANING_INTERVAL = 15000;

  private ProxyLogger logger = null;
  private MysqlConnectionPoolDataSource dataSource = null;
  private final List connectionPool = Collections.synchronizedList(new ArrayList());

  public ConnectionPoolManager(String databaseHost, int databasePort, String databaseName, String databaseUser,
                               String databasePassword) throws ConfigException
  {
    this.logger = ProxyLogger.getLabeledLogger(getClass().getName());

    this.dataSource = new MysqlConnectionPoolDataSource();
    this.dataSource.setServerName(databaseHost);
    this.dataSource.setPortNumber(databasePort);
    this.dataSource.setDatabaseName(databaseName);
    this.dataSource.setUser(databaseUser);
    this.dataSource.setPassword(databasePassword);
    this.dataSource.setTcpNoDelay(true);
    this.dataSource.setRequireSSL(true);

    // check the mysql database connection
    if(getMySQLConnection() == null)
      throw new ConfigException("Could not establish MySQL database connection! Please check your configuration and see the log file for more information.");

    this.setName("ConnectionPoolManagerThread");
    this.setPriority(MIN_PRIORITY);
    this.start();
  }

  public MySQLConnection getMySQLConnection() {
    synchronized(connectionPool) {
      if(connectionPool.size() > 0)
        if(((MySQLConnection)connectionPool.get(0)).isHealthy()) {
          return (MySQLConnection)connectionPool.remove(0);
        } else {
          connectionPool.remove(0);
          return getMySQLConnection();
        }
    }
    try {
      return new MySQLConnection(dataSource);
    } catch(IOException e) {
      return null;
    }
  }

  public synchronized void returnMySQLConnection(MySQLConnection mySQLConnection) {
    synchronized(connectionPool) {
      connectionPool.add(mySQLConnection);
    }
  }

  private void cleanInactiveMySQLConnections() {
    synchronized(connectionPool) {
      Iterator iterator = connectionPool.iterator();
      while(iterator.hasNext()) {
        MySQLConnection mySQLConnection = (MySQLConnection)iterator.next();
        if(mySQLConnection.isInactive(INACTIVE_TIME)) {
          mySQLConnection.closeConnection();
          iterator.remove();
        }
      }
    }
  }

  public MysqlConnectionPoolDataSource getDataSource() {
    return this.dataSource;
  }

  private synchronized void closeMySQLConnections() {
    synchronized(connectionPool) {
      Iterator iterator = connectionPool.iterator();
      while(iterator.hasNext()) {
        ((MySQLConnection)iterator.next()).closeConnection();
      }
      connectionPool.clear();
    }
  }

  public void run() {
    try {
      while(!interrupted()) {
        sleep(CLEANING_INTERVAL);
        cleanInactiveMySQLConnections();
      }
    } catch(InterruptedException e) {
      /* close all open database connection when threads gets interrupted */
      closeMySQLConnections();
      logger.info("ConnectionPoolManager interrupted!");
    }
  }

}