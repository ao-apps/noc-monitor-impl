/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.NetDevice;
import com.aoindustries.aoserv.client.Server;
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
import java.util.Iterator;
import java.util.List;

/**
 * The node per server.
 *
 * @author  AO Industries, Inc.
 */
public class NetDevicesNode extends NodeImpl {

    private static final long serialVersionUID = 1L;

    final ServerNode serverNode;
    private final Server server;
    private final List<NetDeviceNode> netDeviceNodes = new ArrayList<NetDeviceNode>();

    NetDevicesNode(ServerNode serverNode, Server server) {
        this.serverNode = serverNode;
        this.server = server;
    }

    @Override
    public ServerNode getParent() {
        return serverNode;
    }
    
    public Server getServer() {
        return server;
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
        synchronized(netDeviceNodes) {
            return Collections.unmodifiableList(new ArrayList<NetDeviceNode>(netDeviceNodes));
        }
    }

    /**
     * The alert level is equal to the highest alert level of its children.
     */
    @Override
    public AlertLevel getAlertLevel() {
        synchronized(netDeviceNodes) {
            AlertLevel level = AlertLevel.NONE;
            for(NodeImpl networkDeviceNode : netDeviceNodes) {
                AlertLevel networkDeviceNodeLevel = networkDeviceNode.getAlertLevel();
                if(networkDeviceNodeLevel.compareTo(level)>0) level = networkDeviceNodeLevel;
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
        return "net_devices";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "NetDevicesNode.label");
    }
    
    private TableListener tableListener = new TableListener() {
        @Override
        public void tableUpdated(Table<?> table) {
            try {
                verifyNetDevices();
            } catch(IOException err) {
                throw new WrappedException(err);
            } catch(SQLException err) {
                throw new WrappedException(err);
            }
        }
    };

    void start() throws IOException, SQLException {
        synchronized(netDeviceNodes) {
            serverNode.serversNode.rootNode.conn.getNetDevices().addTableListener(tableListener, 100);
            verifyNetDevices();
        }
    }
    
    void stop() {
        synchronized(netDeviceNodes) {
            serverNode.serversNode.rootNode.conn.getNetDevices().removeTableListener(tableListener);
            for(NetDeviceNode netDeviceNode : netDeviceNodes) {
                netDeviceNode.stop();
                serverNode.serversNode.rootNode.nodeRemoved();
            }
            netDeviceNodes.clear();
        }
    }

    private void verifyNetDevices() throws IOException, SQLException {
        List<NetDevice> netDevices = server.getNetDevices();
        synchronized(netDeviceNodes) {
            // Remove old ones
            Iterator<NetDeviceNode> netDeviceNodeIter = netDeviceNodes.iterator();
            while(netDeviceNodeIter.hasNext()) {
                NetDeviceNode netDeviceNode = netDeviceNodeIter.next();
                NetDevice netDevice = netDeviceNode.getNetDevice();
                if(!netDevices.contains(netDevice)) {
                    netDeviceNode.stop();
                    netDeviceNodeIter.remove();
                    serverNode.serversNode.rootNode.nodeRemoved();
                }
            }
            // Add new ones
            for(int c=0;c<netDevices.size();c++) {
                NetDevice netDevice = netDevices.get(c);
                if(c>=netDeviceNodes.size() || !netDevice.equals(netDeviceNodes.get(c).getNetDevice())) {
                    // Insert into proper index
                    NetDeviceNode netDeviceNode = new NetDeviceNode(this, netDevice);
                    netDeviceNodes.add(c, netDeviceNode);
                    netDeviceNode.start();
                    serverNode.serversNode.rootNode.nodeAdded();
                }
            }
        }
    }

    File getPersistenceDirectory() throws IOException {
        File dir = new File(serverNode.getPersistenceDirectory(), "net_devices");
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
