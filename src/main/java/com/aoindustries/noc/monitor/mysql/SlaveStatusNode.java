/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2009-2012, 2016, 2018, 2019, 2020  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of noc-monitor-impl.
 *
 * noc-monitor-impl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * noc-monitor-impl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with noc-monitor-impl.  If not, see <http://www.gnu.org/licenses/>.
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
			mysqlSlaveNode.mysqlSlavesNode.mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode,
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
