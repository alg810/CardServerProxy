package com.bowman.cardserv.mysql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.bowman.cardserv.ConfigException;
import com.bowman.cardserv.util.ProxyLogger;
import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;

/**
 * Because the usermanager gets used in a threaded environment we have to
 * make sure that each request gets his own connection. A simple solution
 * is to generate a "ConnectionPool" for all Database Connections.
 * 
 * @author DonCarlo
 * @since 07.12.2010
 */
public class ConnectionPoolManager extends Thread {

	private ProxyLogger logger = null;
	/* this parameter describes the idle time after which the connection
	 * should be seen as unused and therefor should be closed and removed
	 * from the connectionPool */
	private long inactiveTime = 60000;
	private long cleaningInterval = 15000;
	private MysqlConnectionPoolDataSource dataSource = null;
	private List connectionPool = Collections.synchronizedList(new ArrayList());
	
	/**
	 * main constructor to initialize the connection pool manager
	 * 
	 * @param  - maximum established connections
	 * @param databaseHost - MySQL hostname
	 * @param databasePort - MySQL tcp destination port
	 * @param databaseName - MySQL database name to use
	 * @param databaseUser - login credential
	 * @param databasePassword - login credential
	 * @throws ConfigException 
	 */
	public ConnectionPoolManager(String databaseHost, int databasePort, String databaseName,
			String databaseUser, String databasePassword) throws ConfigException {
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
		if (getMySQLConnection() == null) {
			throw new ConfigException("Could not establish MySQL database connection! Please check your configuration and see the log file for more information.");
		}
		
		this.setName("ConnectionPoolManagerThread");
		this.setPriority(MIN_PRIORITY);
		this.start();
	}

	/**
	 * returns a connection out of the connection-pool.
	 * @return PoolConnection
	 */
	public MySQLConnection getMySQLConnection() {

		synchronized (connectionPool) {
			if (connectionPool.size() > 0)
				if (((MySQLConnection) connectionPool.get(0)).isHealthy()) {
					return (MySQLConnection) connectionPool.remove(0);
				} else {
					connectionPool.remove(0);
					return getMySQLConnection();
				}
		}
		
		return new MySQLConnection(dataSource);
	}
	
	/**
	 * turns back an unused PoolConnection
	 * @param
	 */
	public synchronized void returnMySQLConnection(MySQLConnection mySQLConnection) {
		synchronized (connectionPool) {
			connectionPool.add(mySQLConnection);
		}
	}

	/**
	 * checks the inactivity time stamp of each connection in the pool and removes the
	 * connection if inactive time limit was reached.
	 */
	private void cleanInactiveMySQLConnections() {
		synchronized (connectionPool) {
			Iterator iterator = connectionPool.iterator();
			while (iterator.hasNext()) {
				MySQLConnection mySQLConnection = (MySQLConnection) iterator.next();
				if (mySQLConnection.isInactive(inactiveTime)) {
					mySQLConnection.closeConnection();
					iterator.remove();
				}
			}
		}
	}
	
	/**
	 * returns the database connection details as a dataSource object.
	 * 
	 * @return dataSource
	 */
	public MysqlConnectionPoolDataSource getDataSource() {
		return this.dataSource;
	}
	
	/**
	 * little helper function to close all database connections and release
	 * all PoolConnection Objects in the connectionPool vector.
	 */
	private synchronized void closeMySQLConnections() {
		synchronized (connectionPool) {
			Iterator iterator = connectionPool.iterator();
			while (iterator.hasNext()) {
				((MySQLConnection) iterator.next()).closeConnection();
			}
			connectionPool.clear();
		}
	}

	public void run() {
		try {
			while(!interrupted()) {
				sleep(cleaningInterval);
				cleanInactiveMySQLConnections();
			}
		} catch (InterruptedException e) {
			/* close all open database connection when threads gets interrupted */
			closeMySQLConnections();
			logger.info("ConnectionPoolManager interrupted!");
		}
	}

}