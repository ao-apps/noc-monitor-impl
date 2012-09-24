/*
 * Copyright 2009-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.FailoverMySQLReplication;
import com.aoindustries.aoserv.client.MySQLDatabase;
import com.aoindustries.noc.monitor.common.AlertLevel;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
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
    private final MySQLDatabase mysqlDatabase;
    private final FailoverMySQLReplication mysqlSlave;
    private final String _label;
    volatile private MySQLCheckTablesNode mysqlCheckTablesNode;

    MySQLDatabaseNode(MySQLDatabasesNode mysqlDatabasesNode, MySQLDatabase mysqlDatabase, FailoverMySQLReplication mysqlSlave) throws IOException, SQLException {
        super(
            mysqlDatabasesNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode,
            mysqlDatabasesNode,
            MySQLDatabaseNodeWorker.getWorker(
                mysqlDatabasesNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.monitoringPoint,
                new File(mysqlDatabasesNode.getPersistenceDirectory(), mysqlDatabase.getName()+".show_full_tables"),
                mysqlDatabase,
                mysqlSlave
            )
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

    /**
     * For thread safety and encapsulation, returns an unmodifiable copy of the array.
     */
    @Override
    public List<? extends NodeImpl> getChildren() {
        MySQLCheckTablesNode myMysqlCheckTablesNode = this.mysqlCheckTablesNode;
        if(myMysqlCheckTablesNode!=null) {
            return Collections.singletonList(myMysqlCheckTablesNode);
        }
        return Collections.emptyList();
    }

    /**
     * The alert level is equal to the highest alert level of its children.
     */
    @Override
    public AlertLevel getAlertLevel() {
        AlertLevel level = super.getAlertLevel();
        MySQLCheckTablesNode myMysqlCheckTablesNode = this.mysqlCheckTablesNode;
        if(myMysqlCheckTablesNode!=null) {
            AlertLevel mysqlCheckTablesNodeLevel = myMysqlCheckTablesNode.getAlertLevel();
            if(mysqlCheckTablesNodeLevel.compareTo(level)>0) level = mysqlCheckTablesNodeLevel;
        }
        return level;
    }

    @Override
    public String getId() {
        return _label;
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
            mysqlCheckTablesNode = new MySQLCheckTablesNode(this);
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
            mysqlDatabasesNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.nodeRemoved();
            mysqlCheckTablesNode = null;
        }
    }
}
