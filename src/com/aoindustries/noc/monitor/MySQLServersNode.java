/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.aoserv.client.MySQLServer;
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
 * The node for all MySQLServers on one AOServer.
 *
 * @author  AO Industries, Inc.
 */
public class MySQLServersNode extends NodeImpl {

    private static final long serialVersionUID = 1L;

    final ServerNode serverNode;
    private final AOServer aoServer;
    private final List<MySQLServerNode> mysqlServerNodes = new ArrayList<MySQLServerNode>();

    MySQLServersNode(ServerNode serverNode, AOServer aoServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
        super(port, csf, ssf);
        this.serverNode = serverNode;
        this.aoServer = aoServer;
    }

    @Override
    public Node getParent() {
        return serverNode;
    }
    
    public AOServer getAOServer() {
        return aoServer;
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
        synchronized(mysqlServerNodes) {
            return Collections.unmodifiableList(new ArrayList<MySQLServerNode>(mysqlServerNodes));
        }
    }

    /**
     * The alert level is equal to the highest alert level of its children.
     */
    @Override
    public AlertLevel getAlertLevel() {
        synchronized(mysqlServerNodes) {
            AlertLevel level = AlertLevel.NONE;
            for(NodeImpl mysqlServerNode : mysqlServerNodes) {
                AlertLevel mysqlServerNodeLevel = mysqlServerNode.getAlertLevel();
                if(mysqlServerNodeLevel.compareTo(level)>0) level = mysqlServerNodeLevel;
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
        return accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "MySQLServersNode.label");
    }
    
    private TableListener tableListener = new TableListener() {
        @Override
        public void tableUpdated(Table table) {
            try {
                verifyMySQLServers();
            } catch(IOException err) {
                throw new WrappedException(err);
            } catch(SQLException err) {
                throw new WrappedException(err);
            }
        }
    };

    void start() throws IOException, SQLException {
        synchronized(mysqlServerNodes) {
            serverNode.serversNode.rootNode.conn.getMysqlServers().addTableListener(tableListener, 100);
            verifyMySQLServers();
        }
    }
    
    void stop() {
        synchronized(mysqlServerNodes) {
            serverNode.serversNode.rootNode.conn.getMysqlServers().removeTableListener(tableListener);
            for(MySQLServerNode mysqlServerNode : mysqlServerNodes) {
                mysqlServerNode.stop();
                serverNode.serversNode.rootNode.nodeRemoved();
            }
            mysqlServerNodes.clear();
        }
    }

    private void verifyMySQLServers() throws IOException, SQLException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        List<MySQLServer> mysqlServers = aoServer.getMySQLServers();
        synchronized(mysqlServerNodes) {
            // Remove old ones
            Iterator<MySQLServerNode> mysqlServerNodeIter = mysqlServerNodes.iterator();
            while(mysqlServerNodeIter.hasNext()) {
                MySQLServerNode mysqlServerNode = mysqlServerNodeIter.next();
                MySQLServer mysqlServer = mysqlServerNode.getMySQLServer();
                if(!mysqlServers.contains(mysqlServer)) {
                    mysqlServerNode.stop();
                    mysqlServerNodeIter.remove();
                    serverNode.serversNode.rootNode.nodeRemoved();
                }
            }
            // Add new ones
            for(int c=0;c<mysqlServers.size();c++) {
                MySQLServer mysqlServer = mysqlServers.get(c);
                if(c>=mysqlServerNodes.size() || !mysqlServer.equals(mysqlServerNodes.get(c).getMySQLServer())) {
                    // Insert into proper index
                    MySQLServerNode mysqlServerNode = new MySQLServerNode(this, mysqlServer, port, csf, ssf);
                    mysqlServerNodes.add(c, mysqlServerNode);
                    mysqlServerNode.start();
                    serverNode.serversNode.rootNode.nodeAdded();
                }
            }
        }
    }

    File getPersistenceDirectory() throws IOException {
        File dir = new File(serverNode.getPersistenceDirectory(), "mysql_servers");
        if(!dir.exists()) {
            if(!dir.mkdir()) {
                throw new IOException(
                    accessor.getMessage(
                        //serverNode.serversNode.rootNode.locale,
                        "error.mkdirFailed",
                        dir.getCanonicalPath()
                    )
                );
            }
        }
        return dir;
    }
}
