/*
 * Copyright 2009-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.MySQLDatabase;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.table.Table;
import com.aoindustries.table.TableListener;
import com.aoindustries.util.WrappedException;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * The node for all MySQLDatabases on one MySQLServer.
 *
 * @author  AO Industries, Inc.
 */
public class MySQLDatabasesNode extends NodeImpl {

    private static final long serialVersionUID = 1L;

    final MySQLServerNode mysqlServerNode;
    final MySQLSlaveNode mysqlSlaveNode;
    private final List<MySQLDatabaseNode> mysqlDatabaseNodes = new ArrayList<MySQLDatabaseNode>();

    MySQLDatabasesNode(MySQLServerNode mysqlServerNode) {
        this.mysqlServerNode = mysqlServerNode;
        this.mysqlSlaveNode = null;
    }

    MySQLDatabasesNode(MySQLSlaveNode mysqlSlaveNode) {
        this.mysqlServerNode = mysqlSlaveNode.mysqlSlavesNode.mysqlServerNode;
        this.mysqlSlaveNode = mysqlSlaveNode;
    }

    @Override
    public NodeImpl getParent() {
        return mysqlSlaveNode!=null ? mysqlSlaveNode : mysqlServerNode;
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
        synchronized(mysqlDatabaseNodes) {
            return Collections.unmodifiableList(new ArrayList<MySQLDatabaseNode>(mysqlDatabaseNodes));
        }
    }

    /**
     * The alert level is equal to the highest alert level of its children.
     */
    @Override
    public AlertLevel getAlertLevel() {
        synchronized(mysqlDatabaseNodes) {
            AlertLevel level = AlertLevel.NONE;
            for(NodeImpl mysqlDatabaseNode : mysqlDatabaseNodes) {
                AlertLevel mysqlDatabaseNodeLevel = mysqlDatabaseNode.getAlertLevel();
                if(mysqlDatabaseNodeLevel.compareTo(level)>0) level = mysqlDatabaseNodeLevel;
            }
            return level;
        }
    }

    /**
     * No alert messages.
     */
    @Override
    public String getAlertMessage() {
        return null;
    }

    @Override
    public String getId() {
        return "mysql_databases";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.locale,*/ "MySQLDatabasesNode.label");
    }
    
    private TableListener tableListener = new TableListener() {
        @Override
        public void tableUpdated(Table<?> table) {
            try {
                verifyMySQLDatabases();
            } catch(IOException err) {
                throw new WrappedException(err);
            } catch(SQLException err) {
                throw new WrappedException(err);
            }
        }
    };

    void start() throws IOException, SQLException {
        synchronized(mysqlDatabaseNodes) {
            mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.conn.getMysqlDatabases().addTableListener(tableListener, 100);
            verifyMySQLDatabases();
        }
    }
    
    void stop() {
        synchronized(mysqlDatabaseNodes) {
            mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.conn.getMysqlDatabases().removeTableListener(tableListener);
            for(MySQLDatabaseNode mysqlDatabaseNode : mysqlDatabaseNodes) {
                mysqlDatabaseNode.stop();
                mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.nodeRemoved();
            }
            mysqlDatabaseNodes.clear();
        }
    }

    private void verifyMySQLDatabases() throws IOException, SQLException {
        List<MySQLDatabase> mysqlDatabases = mysqlServerNode.getMySQLServer().getMySQLDatabases();
        synchronized(mysqlDatabaseNodes) {
            // Remove old ones
            Iterator<MySQLDatabaseNode> mysqlDatabaseNodeIter = mysqlDatabaseNodes.iterator();
            while(mysqlDatabaseNodeIter.hasNext()) {
                MySQLDatabaseNode mysqlDatabaseNode = mysqlDatabaseNodeIter.next();
                MySQLDatabase mysqlDatabase = mysqlDatabaseNode.getMySQLDatabase();
                if(!mysqlDatabases.contains(mysqlDatabase)) {
                    mysqlDatabaseNode.stop();
                    mysqlDatabaseNodeIter.remove();
                    mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.nodeRemoved();
                }
            }
            // Add new ones
            for(int c=0;c<mysqlDatabases.size();c++) {
                MySQLDatabase mysqlDatabase = mysqlDatabases.get(c);
                if(c>=mysqlDatabaseNodes.size() || !mysqlDatabase.equals(mysqlDatabaseNodes.get(c).getMySQLDatabase())) {
                    // Insert into proper index
                    MySQLDatabaseNode mysqlDatabaseNode = new MySQLDatabaseNode(this, mysqlDatabase, mysqlSlaveNode!=null ? mysqlSlaveNode.getFailoverMySQLReplication() : null);
                    mysqlDatabaseNodes.add(c, mysqlDatabaseNode);
                    mysqlDatabaseNode.start();
                    mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.nodeAdded();
                }
            }
        }
    }

    File getPersistenceDirectory() throws IOException {
        File dir = new File((mysqlSlaveNode!=null ? mysqlSlaveNode.getPersistenceDirectory() : mysqlServerNode.getPersistenceDirectory()), "mysql_databases");
        if(!dir.exists()) {
            if(!dir.mkdir()) {
                throw new IOException(
                    accessor.getMessage(
                        //mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.locale,
                        "error.mkdirFailed",
                        dir.getCanonicalPath()
                    )
                );
            }
        }
        return dir;
    }
}
