/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.aoserv.client.BackupPartition;
import com.aoindustries.aoserv.client.FailoverFileReplication;
import com.aoindustries.aoserv.client.FailoverMySQLReplication;
import com.aoindustries.aoserv.client.MySQLServer;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.Node;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The node for all FailoverMySQLReplications on one MySQLServer.
 *
 * @author  AO Industries, Inc.
 */
public class MySQLSlaveNode extends NodeImpl {

    private static final long serialVersionUID = 1L;

    final MySQLSlavesNode mysqlSlavesNode;
    private final FailoverMySQLReplication _mysqlReplication;
    private final String _label;

    volatile private MySQLSlaveStatusNode _mysqlSlaveStatusNode;
    volatile private MySQLDatabasesNode _mysqlDatabasesNode;

    MySQLSlaveNode(MySQLSlavesNode mysqlSlavesNode, FailoverMySQLReplication mysqlReplication, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException, IOException, SQLException {
        super(port, csf, ssf);
        this.mysqlSlavesNode = mysqlSlavesNode;
        this._mysqlReplication = mysqlReplication;
        FailoverFileReplication replication = mysqlReplication.getFailoverFileReplication();
        if(replication!=null) {
            // replication-based
            AOServer aoServer = mysqlSlavesNode.mysqlServerNode._mysqlServersNode.getAOServer();
            MySQLServer mysqlServer = mysqlSlavesNode.mysqlServerNode.getMySQLServer();
            BackupPartition bp = mysqlReplication.getFailoverFileReplication().getBackupPartition();
            this._label = bp.getAOServer().getHostname()+":"+bp.getPath()+"/"+aoServer.getHostname()+"/var/lib/mysql/"+mysqlServer.getName();
        } else {
            // ao_server-based
            MySQLServer mysqlServer = mysqlSlavesNode.mysqlServerNode.getMySQLServer();
            this._label = mysqlReplication.getAOServer().getHostname()+":/var/lib/mysql/"+mysqlServer.getName();
        }
    }

    FailoverMySQLReplication getFailoverMySQLReplication() {
        return _mysqlReplication;
    }

    @Override
    public Node getParent() {
        return mysqlSlavesNode;
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
        List<NodeImpl> children = new ArrayList<NodeImpl>(2);

        MySQLSlaveStatusNode mysqlSlaveStatusNode = this._mysqlSlaveStatusNode;
        if(mysqlSlaveStatusNode!=null) children.add(mysqlSlaveStatusNode);

        MySQLDatabasesNode mysqlDatabasesNode = this._mysqlDatabasesNode;
        if(mysqlDatabasesNode!=null) children.add(mysqlDatabasesNode);
        return Collections.unmodifiableList(children);
    }

    /**
     * The alert level is equal to the highest alert level of its children.
     */
    @Override
    public AlertLevel getAlertLevel() {
        AlertLevel level = AlertLevel.NONE;

        MySQLSlaveStatusNode mysqlSlaveStatusNode = this._mysqlSlaveStatusNode;
        if(mysqlSlaveStatusNode!=null) {
            AlertLevel mysqlSlaveStatusNodeLevel = mysqlSlaveStatusNode.getAlertLevel();
            if(mysqlSlaveStatusNodeLevel.compareTo(level)>0) level = mysqlSlaveStatusNodeLevel;
        }

        MySQLDatabasesNode mysqlDatabasesNode = this._mysqlDatabasesNode;
        if(mysqlDatabasesNode!=null) {
            AlertLevel mysqlDatabasesNodeLevel = mysqlDatabasesNode.getAlertLevel();
            if(mysqlDatabasesNodeLevel.compareTo(level)>0) level = mysqlDatabasesNodeLevel;
        }
        return level;
    }

    /**
     * No alert messages.
     */
    @Override
    public String getAlertMessage() {
        return null;
    }

    @Override
    public String getLabel() {
        return _label;
    }
    
    synchronized void start() throws IOException, SQLException {
        RootNodeImpl rootNode = mysqlSlavesNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode;
        if(_mysqlSlaveStatusNode==null) {
            _mysqlSlaveStatusNode = new MySQLSlaveStatusNode(this, port, csf, ssf);
            _mysqlSlaveStatusNode.start();
            rootNode.nodeAdded();
        }
        if(_mysqlDatabasesNode==null) {
            _mysqlDatabasesNode = new MySQLDatabasesNode(this, port, csf, ssf);
            _mysqlDatabasesNode.start();
            rootNode.nodeAdded();
        }
    }
    
    synchronized void stop() {
        RootNodeImpl rootNode = mysqlSlavesNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode;
        if(_mysqlSlaveStatusNode!=null) {
            _mysqlSlaveStatusNode.stop();
            _mysqlSlaveStatusNode = null;
            rootNode.nodeRemoved();
        }

        if(_mysqlDatabasesNode!=null) {
            _mysqlDatabasesNode.stop();
            _mysqlDatabasesNode = null;
            rootNode.nodeRemoved();
        }
    }

    File getPersistenceDirectory() throws IOException {
        File dir = new File(mysqlSlavesNode.getPersistenceDirectory(), Integer.toString(_mysqlReplication.getPkey()));
        if(!dir.exists()) {
            if(!dir.mkdir()) {
                throw new IOException(
                    ApplicationResourcesAccessor.getMessage(
                        mysqlSlavesNode.mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.locale,
                        "error.mkdirFailed",
                        dir.getCanonicalPath()
                    )
                );
            }
        }
        return dir;
    }
}