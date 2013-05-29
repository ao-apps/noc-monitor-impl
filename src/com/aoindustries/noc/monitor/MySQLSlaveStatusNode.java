/*
 * Copyright 2009-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.MySQLReplicationResult;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The replication status per FailoverMySQLReplication.
 *
 * @author  AO Industries, Inc.
 */
public class MySQLSlaveStatusNode extends TableMultiResultNodeImpl<MySQLReplicationResult> {

    private static final long serialVersionUID = 1L;

    MySQLSlaveStatusNode(MySQLSlaveNode mysqlSlaveNode) throws IOException, SQLException {
        super(
            mysqlSlaveNode.mysqlSlavesNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode,
            mysqlSlaveNode,
            MySQLSlaveStatusNodeWorker.getWorker(
                mysqlSlaveNode.mysqlSlavesNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.monitoringPoint,
                mysqlSlaveNode.getPersistenceDirectory(),
                mysqlSlaveNode.getFailoverMySQLReplication()
            )
        );
    }

    @Override
    public String getId() {
        return "status";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*rootNode.locale,*/ "MySQLSlaveStatusNode.label");
    }

    @Override
    public List<?> getColumnHeaders(/*Locale locale*/) {
        List<String> headers = new ArrayList<String>(11);
        headers.add(accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.secondsBehindMaster"));
        headers.add(accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.masterLogFile"));
        headers.add(accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.masterLogPosition"));
        headers.add(accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.slaveIOState"));
        headers.add(accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.slaveLogFile"));
        headers.add(accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.slaveLogPosition"));
        headers.add(accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.slaveIORunning"));
        headers.add(accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.slaveSQLRunning"));
        headers.add(accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.lastErrorNumber"));
        headers.add(accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.lastErrorDetails"));
        headers.add(accessor.getMessage(/*locale,*/ "MySQLSlaveStatusNode.columnHeader.alertThresholds"));
        return Collections.unmodifiableList(headers);
    }
}
