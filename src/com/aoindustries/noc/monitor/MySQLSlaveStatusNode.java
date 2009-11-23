/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.noc.common.MySQLReplicationResult;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The replication status per FailoverMySQLReplication.
 *
 * @author  AO Industries, Inc.
 */
public class MySQLSlaveStatusNode extends TableMultiResultNodeImpl<String,MySQLReplicationResult> {

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
        return ApplicationResourcesAccessor.getMessage(rootNode.locale, "MySQLSlaveStatusNode.label");
    }

    @Override
    public List<?> getColumnHeaders(Locale locale) {
        List<String> headers = new ArrayList<String>(11);
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLSlaveStatusNode.columnHeader.secondsBehindMaster"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLSlaveStatusNode.columnHeader.masterLogFile"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLSlaveStatusNode.columnHeader.masterLogPosition"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLSlaveStatusNode.columnHeader.slaveIOState"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLSlaveStatusNode.columnHeader.slaveLogFile"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLSlaveStatusNode.columnHeader.slaveLogPosition"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLSlaveStatusNode.columnHeader.slaveIORunning"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLSlaveStatusNode.columnHeader.slaveSQLRunning"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLSlaveStatusNode.columnHeader.lastErrorNumber"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLSlaveStatusNode.columnHeader.lastErrorDetails"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLSlaveStatusNode.columnHeader.alertThresholds"));
        return Collections.unmodifiableList(headers);
    }
}