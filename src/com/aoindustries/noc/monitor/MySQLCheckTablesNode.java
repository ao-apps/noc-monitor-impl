/*
 * Copyright 2009, 2014, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;

/**
 * The node for all MySQLDatabases on one MySQLServer.
 *
 * @author  AO Industries, Inc.
 */
public class MySQLCheckTablesNode extends TableResultNodeImpl {

	private static final long serialVersionUID = 2L;

	final MySQLDatabaseNode mysqlDatabaseNode;

	MySQLCheckTablesNode(MySQLDatabaseNode mysqlDatabaseNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			mysqlDatabaseNode.mysqlDatabasesNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode,
			mysqlDatabaseNode,
			MySQLCheckTablesNodeWorker.getWorker(
				mysqlDatabaseNode,
				new File(mysqlDatabaseNode.getPersistenceDirectory(), "check_tables")
			),
			port,
			csf,
			ssf
		);
		this.mysqlDatabaseNode = mysqlDatabaseNode;
	}

	@Override
	public String getLabel() {
		return accessor.getMessage(rootNode.locale, "MySQLCheckTablesNode.label");
	}

	/**
	 * The maximum alert level is constrained by the mysql_databases table.
	 */
	@Override
	AlertLevel getMaxAlertLevel() {
		return AlertLevelUtils.getMonitoringAlertLevel(
			mysqlDatabaseNode.mysqlDatabase.getMaxCheckTableAlertLevel()
		);
	}
}
