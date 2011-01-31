package com.bowman.cardserv.mysql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.sql.SQLException;

import com.bowman.cardserv.ConfigException;
import com.bowman.cardserv.mysql.PoolConnection;
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
	 * @param maxPoolSize - maximum established connections
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
		if (getPoolConnection() == null) {
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
	public PoolConnection getPoolConnection() {

		synchronized (connectionPool) {
			if (connectionPool.size() > 0)
				try {
					if (((PoolConnection) connectionPool.get(0)).isHealthy()) {
						return (PoolConnection) connectionPool.remove(0);
					} else {
						connectionPool.remove(0);
						return getPoolConnection();
					}
				} catch (SQLException e) {
					logger.severe("(getPoolConnection) failed to get warnings or connection status!");
				}
		}
		
		try {
			return new PoolConnection(dataSource);
		} catch (ClassNotFoundException e) {
			logger.severe("(getPoolConnection) class 'com.mysql.jdbc.Driver' not found: " + e);
		} catch (SQLException e) {
			logger.severe("(getPoolConnection) Failed to setup DB connection: " + e);
		}
		
		return null;
	}
	
	/**
	 * turns back an unused PoolConnection
	 * @param PoolConnection 
	 */
	public synchronized void returnPoolConnection(PoolConnection poolConnection) {
		synchronized (connectionPool) {
			connectionPool.add(poolConnection);
		}
	}

	/**
	 * checks the inactivity time stamp of each connection in the pool and removes the
	 * connection if inactive time limit was reached.
	 */
	private void cleanInactivePoolConnections() {
		synchronized (connectionPool) {
			Iterator iterator = connectionPool.iterator();
			while (iterator.hasNext()) {
				PoolConnection poolConnection = (PoolConnection) iterator.next();
				if (poolConnection.isInactive(inactiveTime)) {
					try {
						poolConnection.closeConnection();
					} catch (SQLException e) {
						logger.severe("(cleanInactivePoolConnections) failed to close mysql connection: " + e);
					}
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
	private synchronized void closePoolConnections() {
		synchronized (connectionPool) {
			Iterator iterator = connectionPool.iterator();
			while (iterator.hasNext()) {
				PoolConnection poolConnection = (PoolConnection) iterator.next();
				try {
					poolConnection.closeConnection();
				} catch (SQLException e) {
					logger.severe("(closeConnections) failed to close mysql connection: " + e);
				}
			}
			connectionPool.clear();
		}
	}

	public void run() {
		try {
			while(!interrupted()) {
				sleep(cleaningInterval);
				cleanInactivePoolConnections();
			}
		} catch (InterruptedException e) {
			/* close all open database connection when threads gets interrupted */
			closePoolConnections();
			logger.info("ConnectionPoolManager interrupted!");
		}
	}

}