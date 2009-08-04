/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.aoserv.client.BackupPartition;
import com.aoindustries.aoserv.client.FailoverMySQLReplication;
import com.aoindustries.aoserv.client.MySQLServer;
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
public class MySQLReplicationNode extends TableMultiResultNodeImpl {

    private static final long serialVersionUID = 1L;

    private final FailoverMySQLReplication _mysqlReplication;
    private final String _label;

    MySQLReplicationNode(MySQLReplicationsNode mysqlReplicationsNode, FailoverMySQLReplication mysqlReplication, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException, SQLException {
        super(
            mysqlReplicationsNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode,
            mysqlReplicationsNode,
            MySQLReplicationNodeWorker.getWorker(
                mysqlReplicationsNode.getPersistenceDirectory(),
                mysqlReplication
            ),
            port,
            csf,
            ssf
        );
        this._mysqlReplication = mysqlReplication;
        AOServer aoServer = mysqlReplicationsNode.mysqlServerNode._mysqlServersNode.getAOServer();
        MySQLServer mysqlServer = mysqlReplicationsNode.mysqlServerNode.getMySQLServer();
        BackupPartition bp = mysqlReplication.getFailoverFileReplication().getBackupPartition();
        this._label = bp.getAOServer().getHostname()+":"+bp.getPath()+"/"+aoServer.getHostname()+"/var/lib/mysql/"+mysqlServer.getName();
    }

    FailoverMySQLReplication getFailoverMySQLReplication() {
        return _mysqlReplication;
    }

    @Override
    public String getLabel() {
        return _label;
    }

    @Override
    public List<?> getColumnHeaders(Locale locale) {
        List<String> headers = new ArrayList<String>(10);
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLReplicationNode.columnHeader.secondsBehindMaster"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLReplicationNode.columnHeader.masterLogFile"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLReplicationNode.columnHeader.masterLogPosition"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLReplicationNode.columnHeader.slaveIOState"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLReplicationNode.columnHeader.slaveLogFile"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLReplicationNode.columnHeader.slaveLogPosition"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLReplicationNode.columnHeader.slaveIORunning"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLReplicationNode.columnHeader.slaveSQLRunning"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLReplicationNode.columnHeader.lastErrorNumber"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLReplicationNode.columnHeader.lastErrorDetails"));
        return Collections.unmodifiableList(headers);
    }
}
