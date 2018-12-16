/*
 * Copyright 2009, 2014, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.mysql;

import com.aoindustries.noc.monitor.AlertLevelUtils;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.TableResultNodeImpl;
import com.aoindustries.noc.monitor.common.AlertLevel;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;

/**
 * The node for all MySQLDatabases on one Server.
 *
 * @author  AO Industries, Inc.
 */
public class CheckTablesNode extends TableResultNodeImpl {

	private static final long serialVersionUID = 2L;

	final DatabaseNode mysqlDatabaseNode;

	CheckTablesNode(DatabaseNode mysqlDatabaseNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			mysqlDatabaseNode.mysqlDatabasesNode.mysqlServerNode._mysqlServersNode.serverNode.hostsNode.rootNode,
			mysqlDatabaseNode,
			CheckTablesNodeWorker.getWorker(
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
	protected AlertLevel getMaxAlertLevel() {
		return AlertLevelUtils.getMonitoringAlertLevel(
			mysqlDatabaseNode.mysqlDatabase.getMaxCheckTableAlertLevel()
		);
	}
}
