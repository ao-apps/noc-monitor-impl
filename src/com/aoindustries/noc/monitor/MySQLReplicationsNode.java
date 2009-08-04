/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.FailoverMySQLReplication;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.Node;
import com.aoindustries.table.Table;
import com.aoindustries.table.TableListener;
import com.aoindustries.util.WrappedException;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * The node for all FailoverMySQLReplications on one MySQLServer.
 *
 * @author  AO Industries, Inc.
 */
public class MySQLReplicationsNode extends NodeImpl {

    private static final long serialVersionUID = 1L;

    final MySQLServerNode mysqlServerNode;
    private final List<MySQLReplicationNode> mysqlReplicationNodes = new ArrayList<MySQLReplicationNode>();

    MySQLReplicationsNode(MySQLServerNode mysqlServerNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
        super(port, csf, ssf);
        this.mysqlServerNode = mysqlServerNode;
    }

    @Override
    public Node getParent() {
        return mysqlServerNode;
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
        synchronized(mysqlReplicationNodes) {
            return Collections.unmodifiableList(new ArrayList<MySQLReplicationNode>(mysqlReplicationNodes));
        }
    }

    /**
     * The alert level is equal to the highest alert level of its children.
     */
    @Override
    public AlertLevel getAlertLevel() {
        synchronized(mysqlReplicationNodes) {
            AlertLevel level = AlertLevel.NONE;
            for(NodeImpl mysqlReplicationNode : mysqlReplicationNodes) {
                AlertLevel mysqlReplicationNodeLevel = mysqlReplicationNode.getAlertLevel();
                if(mysqlReplicationNodeLevel.compareTo(level)>0) level = mysqlReplicationNodeLevel;
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
    public String getLabel() {
        return ApplicationResourcesAccessor.getMessage(mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.locale, "MySQLReplicationsNode.label");
    }
    
    private TableListener tableListener = new TableListener() {
        @Override
        public void tableUpdated(Table table) {
            try {
                verifyMySQLReplications();
            } catch(IOException err) {
                throw new WrappedException(err);
            } catch(SQLException err) {
                throw new WrappedException(err);
            }
        }
    };

    void start() throws IOException, SQLException {
        synchronized(mysqlReplicationNodes) {
            mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.conn.getFailoverFileReplications().addTableListener(tableListener, 100);
            verifyMySQLReplications();
        }
    }
    
    void stop() {
        synchronized(mysqlReplicationNodes) {
            mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.conn.getFailoverFileReplications().removeTableListener(tableListener);
            for(MySQLReplicationNode mysqlReplicationNode : mysqlReplicationNodes) {
                mysqlReplicationNode.stop();
                mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.nodeRemoved();
            }
            mysqlReplicationNodes.clear();
        }
    }

    private void verifyMySQLReplications() throws IOException, SQLException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        List<FailoverMySQLReplication> mysqlReplications = mysqlServerNode.getMySQLServer().getFailoverMySQLReplications();
        synchronized(mysqlReplicationNodes) {
            // Remove old ones
            Iterator<MySQLReplicationNode> mysqlReplicationNodeIter = mysqlReplicationNodes.iterator();
            while(mysqlReplicationNodeIter.hasNext()) {
                MySQLReplicationNode mysqlReplicationNode = mysqlReplicationNodeIter.next();
                FailoverMySQLReplication mysqlReplication = mysqlReplicationNode.getFailoverMySQLReplication();
                if(!mysqlReplications.contains(mysqlReplication)) {
                    mysqlReplicationNode.stop();
                    mysqlReplicationNodeIter.remove();
                    mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.nodeRemoved();
                }
            }
            // Add new ones
            for(int c=0;c<mysqlReplications.size();c++) {
                FailoverMySQLReplication mysqlReplication = mysqlReplications.get(c);
                if(c>=mysqlReplicationNodes.size()) {
                    // Just add to the end
                    MySQLReplicationNode mysqlReplicationNode = new MySQLReplicationNode(this, mysqlReplication, port, csf, ssf);
                    mysqlReplicationNodes.add(mysqlReplicationNode);
                    mysqlReplicationNode.start();
                    mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.nodeAdded();
                } else {
                    if(!mysqlReplication.equals(mysqlReplicationNodes.get(c).getFailoverMySQLReplication())) {
                        // Insert into proper index
                        MySQLReplicationNode mysqlReplicationNode = new MySQLReplicationNode(this, mysqlReplication, port, csf, ssf);
                        mysqlReplicationNodes.add(c, mysqlReplicationNode);
                        mysqlReplicationNode.start();
                        mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.nodeAdded();
                    }
                }
            }
        }
    }

    File getPersistenceDirectory() throws IOException {
        File dir = new File(mysqlServerNode.getPersistenceDirectory(), "failover_mysql_replications");
        if(!dir.exists()) {
            if(!dir.mkdir()) {
                throw new IOException(
                    ApplicationResourcesAccessor.getMessage(
                        mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.locale,
                        "error.mkdirFailed",
                        dir.getCanonicalPath()
                    )
                );
            }
        }
        return dir;
    }
}
