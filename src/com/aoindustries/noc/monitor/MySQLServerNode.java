/*
 * Copyright 2009-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.FailoverMySQLReplication;
import com.aoindustries.aoserv.client.MySQLServer;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.Node;
import com.aoindustries.table.Table;
import com.aoindustries.table.TableListener;
import com.aoindustries.util.WrappedException;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The node per MySQL server.
 *
 * @author  AO Industries, Inc.
 */
public class MySQLServerNode extends NodeImpl {

    private static final long serialVersionUID = 1L;

    final MySQLServersNode _mysqlServersNode;
    private final MySQLServer _mysqlServer;
    private final String _label;

    volatile private MySQLSlavesNode _mysqlSlavesNode;
    volatile private MySQLDatabasesNode _mysqlDatabasesNode;

    MySQLServerNode(MySQLServersNode mysqlServersNode, MySQLServer mysqlServer) {
        this._mysqlServersNode = mysqlServersNode;
        this._mysqlServer = mysqlServer;
        this._label = mysqlServer.getName();
    }

    @Override
    public MySQLServersNode getParent() {
        return _mysqlServersNode;
    }
    
    public MySQLServer getMySQLServer() {
        return _mysqlServer;
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

        MySQLSlavesNode mysqlSlavesNode = this._mysqlSlavesNode;
        if(mysqlSlavesNode!=null) children.add(mysqlSlavesNode);

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

        MySQLSlavesNode mysqlSlavesNode = this._mysqlSlavesNode;
        if(mysqlSlavesNode!=null) {
            AlertLevel mysqlSlavesNodeLevel = mysqlSlavesNode.getAlertLevel();
            if(mysqlSlavesNodeLevel.compareTo(level)>0) level = mysqlSlavesNodeLevel;
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
    public String getId() {
        return _label;
    }

    @Override
    public String getLabel() {
        return _label;
    }

    private TableListener tableListener = new TableListener() {
        @Override
        public void tableUpdated(Table<?> table) {
            try {
                verifyFailoverMySQLReplications();
            } catch(IOException err) {
                throw new WrappedException(err);
            } catch(SQLException err) {
                throw new WrappedException(err);
            }
        }
    };

    synchronized void start() throws IOException, SQLException {
        RootNodeImpl rootNode = _mysqlServersNode.serverNode.serversNode.rootNode;
        rootNode.conn.getFailoverMySQLReplications().addTableListener(tableListener, 100);
        verifyFailoverMySQLReplications();
        if(_mysqlDatabasesNode==null) {
            _mysqlDatabasesNode = new MySQLDatabasesNode(this);
            rootNode.initNode(_mysqlDatabasesNode);
            _mysqlDatabasesNode.start();
            rootNode.nodeAdded();
        }
    }

    synchronized void stop() {
        RootNodeImpl rootNode = _mysqlServersNode.serverNode.serversNode.rootNode;
        if(_mysqlSlavesNode!=null) {
            _mysqlSlavesNode.stop();
            rootNode.nodeRemoved();
            rootNode.destroyNode(_mysqlSlavesNode);
            _mysqlSlavesNode = null;
        }

        if(_mysqlDatabasesNode!=null) {
            _mysqlDatabasesNode.stop();
            rootNode.nodeRemoved();
            rootNode.destroyNode(_mysqlDatabasesNode);
            _mysqlDatabasesNode = null;
        }
    }

    synchronized private void verifyFailoverMySQLReplications() throws IOException, SQLException {
        List<FailoverMySQLReplication> failoverMySQLReplications = _mysqlServer.getFailoverMySQLReplications();
        if(!failoverMySQLReplications.isEmpty()) {
            if(_mysqlSlavesNode==null) {
                _mysqlSlavesNode = new MySQLSlavesNode(this);
                _mysqlServersNode.serverNode.serversNode.rootNode.initNode(_mysqlSlavesNode);
                _mysqlSlavesNode.start();
                _mysqlServersNode.serverNode.serversNode.rootNode.nodeAdded();
            }
        } else {
            if(_mysqlSlavesNode!=null) {
                _mysqlSlavesNode.stop();
                _mysqlServersNode.serverNode.serversNode.rootNode.nodeRemoved();
                _mysqlServersNode.serverNode.serversNode.rootNode.destroyNode(_mysqlSlavesNode);
                _mysqlSlavesNode = null;
            }
        }
    }

    File getPersistenceDirectory() throws IOException {
        File dir = new File(_mysqlServersNode.getPersistenceDirectory(), _label);
        if(!dir.exists()) {
            if(!dir.mkdir()) {
                throw new IOException(
                    accessor.getMessage(
                        //_mysqlServersNode.serverNode.serversNode.rootNode.locale,
                        "error.mkdirFailed",
                        dir.getCanonicalPath()
                    )
                );
            }
        }
        return dir;
    }
}
