/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.IPAddress;
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
 * The node of all IPAddresses per NetDevice.
 *
 * @author  AO Industries, Inc.
 */
public class IPAddressesNode extends NodeImpl {

    final NetDeviceNode netDeviceNode;
    private final List<IPAddressNode> ipAddressNodes = new ArrayList<IPAddressNode>();

    IPAddressesNode(NetDeviceNode netDeviceNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
        super(port, csf, ssf);

        this.netDeviceNode = netDeviceNode;
    }

    public Node getParent() {
        return netDeviceNode;
    }
    
    public boolean getAllowsChildren() {
        return true;
    }

    /**
     * For thread safety and encapsulation, returns an unmodifiable copy of the array.
     */
    public List<? extends Node> getChildren() {
        synchronized(ipAddressNodes) {
            return Collections.unmodifiableList(new ArrayList<IPAddressNode>(ipAddressNodes));
        }
    }

    /**
     * The alert level is equal to the highest alert level of its children.
     */
    public AlertLevel getAlertLevel() {
        AlertLevel level = AlertLevel.NONE;
        synchronized(ipAddressNodes) {
            for(NodeImpl ipAddressNode : ipAddressNodes) {
                AlertLevel ipAddressNodeLevel = ipAddressNode.getAlertLevel();
                if(ipAddressNodeLevel.compareTo(level)>0) level = ipAddressNodeLevel;
            }
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

    public String getLabel() {
        return ApplicationResourcesAccessor.getMessage(netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode.locale, "IPAddressesNode.label");
    }
    
    private TableListener tableListener = new TableListener() {
        public void tableUpdated(Table table) {
            try {
                verifyIPAddresses();
            } catch(IOException err) {
                throw new WrappedException(err);
            } catch(SQLException err) {
                throw new WrappedException(err);
            }
        }
    };

    void start() throws IOException, SQLException {
        synchronized(ipAddressNodes) {
            netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode.conn.ipAddresses.addTableListener(tableListener, 100);
            verifyIPAddresses();
        }
    }
    
    void stop() {
        synchronized(ipAddressNodes) {
            RootNodeImpl rootNode = netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode;
            rootNode.conn.ipAddresses.removeTableListener(tableListener);
            for(IPAddressNode ipAddressNode : ipAddressNodes) {
                ipAddressNode.stop();
                rootNode.nodeRemoved();
            }
            ipAddressNodes.clear();
        }
    }

    private void verifyIPAddresses() throws RemoteException, IOException, SQLException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        final RootNodeImpl rootNode = netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode;

        List<IPAddress> ipAddresses = netDeviceNode.getNetDevice().getIPAddresses();
        synchronized(ipAddressNodes) {
            // Remove old ones
            Iterator<IPAddressNode> ipAddressNodeIter = ipAddressNodes.iterator();
            while(ipAddressNodeIter.hasNext()) {
                IPAddressNode ipAddressNode = ipAddressNodeIter.next();
                IPAddress ipAddress = ipAddressNode.getIPAddress();
                if(!ipAddresses.contains(ipAddress)) {
                    ipAddressNode.stop();
                    ipAddressNodeIter.remove();
                    rootNode.nodeRemoved();
                }
            }
            // Add new ones
            for(int c=0;c<ipAddresses.size();c++) {
                IPAddress ipAddress = ipAddresses.get(c);
                if(c>=ipAddressNodes.size()) {
                    // Just add to the end
                    IPAddressNode ipAddressNode = new IPAddressNode(this, ipAddress, port, csf, ssf);
                    ipAddressNodes.add(ipAddressNode);
                    ipAddressNode.start();
                    rootNode.nodeAdded();
                } else {
                    if(!ipAddress.equals(ipAddressNodes.get(c).getIPAddress())) {
                        // Insert into proper index
                        IPAddressNode ipAddressNode = new IPAddressNode(this, ipAddress, port, csf, ssf);
                        ipAddressNodes.add(c, ipAddressNode);
                        ipAddressNode.start();
                        rootNode.nodeAdded();
                    }
                }
            }
        }
    }

    File getPersistenceDirectory() throws IOException {
        File dir = new File(netDeviceNode.getPersistenceDirectory(), "ip_addresses");
        if(!dir.exists()) {
            if(!dir.mkdir()) {
                throw new IOException(
                    ApplicationResourcesAccessor.getMessage(
                        netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode.locale,
                        "error.mkdirFailed",
                        dir.getCanonicalPath()
                    )
                );
            }
        }
        return dir;
    }
}
