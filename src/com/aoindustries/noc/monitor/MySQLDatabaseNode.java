/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.MySQLDatabase;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.Node;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * The node for all MySQLDatabases on one MySQLServer.
 *
 * @author  AO Industries, Inc.
 */
public class MySQLDatabaseNode extends TableResultNodeImpl {

    private static final long serialVersionUID = 1L;

    final MySQLDatabasesNode mysqlDatabasesNode;
    private final MySQLDatabase mysqlDatabase;
    private final String _label;
    // TODO: private final List<MySQLTableNode> mysqlTableNodes = new ArrayList<MySQLTableNode>();

    MySQLDatabaseNode(MySQLDatabasesNode mysqlDatabasesNode, MySQLDatabase mysqlDatabase, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException, SQLException {
        super(
            mysqlDatabasesNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode,
            mysqlDatabasesNode,
            MySQLDatabaseNodeWorker.getWorker(
                new File(mysqlDatabasesNode.getPersistenceDirectory(), mysqlDatabase.getName()+".show_full_tables"),
                mysqlDatabase
            ),
            port,
            csf,
            ssf
        );
        this.mysqlDatabasesNode = mysqlDatabasesNode;
        this.mysqlDatabase = mysqlDatabase;
        this._label = mysqlDatabase.getName();
    }

    MySQLDatabase getMySQLDatabase() {
        return mysqlDatabase;
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    /**
     * For thread safety and encapsulation, returns an unmodifiable copy of the array.
     */
    @Override
    public List<? extends Node> getChildren() {
        /* TODO: synchronized(mysqlTableNodes) {
            return Collections.unmodifiableList(new ArrayList<MySQLTableNode>(mysqlTableNodes));
        }*/
        return Collections.emptyList();
    }

    /**
     * The alert level is equal to the highest alert level of its children.
     */
    @Override
    public AlertLevel getAlertLevel() {
        AlertLevel level = super.getAlertLevel();
        /* TODO
        synchronized(mysqlTableNodes) {
            for(NodeImpl mysqlTableNode : mysqlTableNodes) {
                AlertLevel mysqlTableNodeLevel = mysqlTableNode.getAlertLevel();
                if(mysqlTableNodeLevel.compareTo(level)>0) level = mysqlTableNodeLevel;
            }
            return level;
        }*/
        return level;
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
                    ApplicationResourcesAccessor.getMessage(
                        mysqlDatabasesNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.locale,
                        "error.mkdirFailed",
                        dir.getCanonicalPath()
                    )
                );
            }
        }
        return dir;
    }
}
