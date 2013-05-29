/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import java.io.File;
import java.io.IOException;

/**
 * The node for all MySQLDatabases on one MySQLServer.
 *
 * @author  AO Industries, Inc.
 */
public class MySQLCheckTablesNode extends TableResultNodeImpl {

    private static final long serialVersionUID = 1L;

    MySQLCheckTablesNode(MySQLDatabaseNode mysqlDatabaseNode) throws IOException {
        super(
            mysqlDatabaseNode.mysqlDatabasesNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode,
            mysqlDatabaseNode,
            MySQLCheckTablesNodeWorker.getWorker(
                mysqlDatabaseNode.mysqlDatabasesNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.monitoringPoint,
                mysqlDatabaseNode,
                new File(mysqlDatabaseNode.getPersistenceDirectory(), "check_tables")
            )
        );
    }

    @Override
    public String getId() {
        return "check_tables";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*rootNode.locale,*/ "MySQLCheckTablesNode.label");
    }
}
