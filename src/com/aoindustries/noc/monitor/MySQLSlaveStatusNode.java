/*
 * Copyright 2009-2012, 2016 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.MySQLReplicationResult;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * The replication status per FailoverMySQLReplication.
 *
 * @author  AO Industries, Inc.
 */
public class MySQLSlaveStatusNode extends TableMultiResultNodeImpl<MySQLReplicationResult> {

	private static final long serialVersionUID = 1L;

	MySQLSlaveStatusNode(MySQLSlaveNode mysqlSlaveNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException, SQLException {
		super(
			mysqlSlaveNode.mysqlSlavesNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode,
			mysqlSlaveNode,
			MySQLSlaveStatusNodeWorker.getWorker(
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
		return accessor.getMessage(/*rootNode.locale,*/ "MySQLSlaveStatusNode.label");
	}

	@Override
	public List<String> getColumnHeaders(/*Locale locale*/) {
		return Arrays.asList(
			accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.secondsBehindMaster"),
			accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.masterLogFile"),
			accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.masterLogPosition"),
			accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.slaveIOState"),
			accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.slaveLogFile"),
			accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.slaveLogPosition"),
			accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.slaveIORunning"),
			accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.slaveSQLRunning"),
			accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.lastErrorNumber"),
			accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.lastErrorDetails"),
			accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.alertThresholds")
		);
	}
}
