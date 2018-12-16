/*
 * Copyright 2009-2012, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.mysql;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.TableMultiResultNodeImpl;
import com.aoindustries.noc.monitor.common.MySQLReplicationResult;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * The replication status per MysqlReplication.
 *
 * @author  AO Industries, Inc.
 */
public class SlaveStatusNode extends TableMultiResultNodeImpl<MySQLReplicationResult> {

	private static final long serialVersionUID = 1L;

	SlaveStatusNode(SlaveNode mysqlSlaveNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException, SQLException {
		super(
			mysqlSlaveNode.mysqlSlavesNode.mysqlServerNode._mysqlServersNode.serverNode.hostsNode.rootNode,
			mysqlSlaveNode,
			SlaveStatusNodeWorker.getWorker(
				mysqlSlaveNode.getPersistenceDirectory(),
				mysqlSlaveNode.getFailoverMySQLReplication()
			),
			port,
			csf,
			ssf
		);
	}

	@Override
	public String getLabel() {
		return accessor.getMessage(rootNode.locale, "MySQLSlaveStatusNode.label");
	}

	@Override
	public List<String> getColumnHeaders() {
		return Arrays.asList(
			accessor.getMessage(rootNode.locale, "MySQLSlaveStatusNode.columnHeader.secondsBehindMaster"),
			accessor.getMessage(rootNode.locale, "MySQLSlaveStatusNode.columnHeader.masterLogFile"),
			accessor.getMessage(rootNode.locale, "MySQLSlaveStatusNode.columnHeader.masterLogPosition"),
			accessor.getMessage(rootNode.locale, "MySQLSlaveStatusNode.columnHeader.slaveIOState"),
			accessor.getMessage(rootNode.locale, "MySQLSlaveStatusNode.columnHeader.slaveLogFile"),
			accessor.getMessage(rootNode.locale, "MySQLSlaveStatusNode.columnHeader.slaveLogPosition"),
			accessor.getMessage(rootNode.locale, "MySQLSlaveStatusNode.columnHeader.slaveIORunning"),
			accessor.getMessage(rootNode.locale, "MySQLSlaveStatusNode.columnHeader.slaveSQLRunning"),
			accessor.getMessage(rootNode.locale, "MySQLSlaveStatusNode.columnHeader.lastErrorNumber"),
			accessor.getMessage(rootNode.locale, "MySQLSlaveStatusNode.columnHeader.lastErrorDetails"),
			accessor.getMessage(rootNode.locale, "MySQLSlaveStatusNode.columnHeader.alertThresholds")
		);
	}
}
