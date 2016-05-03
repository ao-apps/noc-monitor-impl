/*
 * Copyright 2009-2013, 2014, 2016 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.FailoverMySQLReplication;
import com.aoindustries.aoserv.client.MySQLDatabase;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.List;

/**
 * The node for one MySQLDatabase.
 *
 * @author  AO Industries, Inc.
 */
public class MySQLDatabaseNode extends TableResultNodeImpl {

	private static final long serialVersionUID = 1L;

	final MySQLDatabaseNodeWorker databaseWorker;
	final MySQLDatabasesNode mysqlDatabasesNode;
	final MySQLDatabase mysqlDatabase;
	private final FailoverMySQLReplication mysqlSlave;
	private final String _label;
	volatile private MySQLCheckTablesNode mysqlCheckTablesNode;

	MySQLDatabaseNode(MySQLDatabasesNode mysqlDatabasesNode, MySQLDatabase mysqlDatabase, FailoverMySQLReplication mysqlSlave, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException, SQLException {
		super(
			mysqlDatabasesNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode,
			mysqlDatabasesNode,
			MySQLDatabaseNodeWorker.getWorker(
				new File(mysqlDatabasesNode.getPersistenceDirectory(), mysqlDatabase.getName()+".show_full_tables"),
				mysqlDatabase,
				mysqlSlave
			),
			port,
			csf,
			ssf
		);
		this.databaseWorker = (MySQLDatabaseNodeWorker)worker;
		this.mysqlDatabasesNode = mysqlDatabasesNode;
		this.mysqlDatabase = mysqlDatabase;
		this.mysqlSlave = mysqlSlave;
		this._label = mysqlDatabase.getName();
	}

	MySQLDatabase getMySQLDatabase() {
		return mysqlDatabase;
	}

	FailoverMySQLReplication getMySQLSlave() {
		return mysqlSlave;
	}

	@Override
	public boolean getAllowsChildren() {
		return true;
	}

	@Override
	public List<MySQLCheckTablesNode> getChildren() {
		return getSnapshot(this.mysqlCheckTablesNode);
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	public AlertLevel getAlertLevel() {
		return constrainAlertLevel(
			AlertLevelUtils.getMaxAlertLevel(
				super.getAlertLevel(),
				this.mysqlCheckTablesNode
			)
		);
	}

	@Override
	public String getLabel() {
		return _label;
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File(mysqlDatabasesNode.getPersistenceDirectory(), _label);
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				throw new IOException(
					accessor.getMessage(
						//mysqlDatabasesNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.locale,
						"error.mkdirFailed",
						dir.getCanonicalPath()
					)
				);
			}
		}
		return dir;
	}

	@Override
	synchronized void start() throws IOException {
		if(mysqlCheckTablesNode==null) {
			mysqlCheckTablesNode = new MySQLCheckTablesNode(this, port, csf, ssf);
			mysqlCheckTablesNode.start();
			mysqlDatabasesNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.nodeAdded();
		}
		super.start();
	}

	@Override
	synchronized void stop() {
		super.stop();
		if(mysqlCheckTablesNode!=null) {
			mysqlCheckTablesNode.stop();
			mysqlCheckTablesNode = null;
			mysqlDatabasesNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.nodeRemoved();
		}
	}
}
