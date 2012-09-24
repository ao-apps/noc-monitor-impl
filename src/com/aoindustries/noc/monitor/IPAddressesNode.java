/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.IPAddress;
import com.aoindustries.aoserv.client.NetDevice;
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
 * The node of all IPAddresses per NetDevice.
 *
 * @author  AO Industries, Inc.
 */
public class IPAddressesNode extends NodeImpl {

    private static final long serialVersionUID = 1L;

    final NetDeviceNode netDeviceNode;
    private final List<IPAddressNode> ipAddressNodes = new ArrayList<IPAddressNode>();

    IPAddressesNode(NetDeviceNode netDeviceNode) {
        this.netDeviceNode = netDeviceNode;
    }

    @Override
    public NetDeviceNode getParent() {
        return netDeviceNode;
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
        synchronized(ipAddressNodes) {
            return Collections.unmodifiableList(new ArrayList<IPAddressNode>(ipAddressNodes));
        }
    }

    /**
     * The alert level is equal to the highest alert level of its children.
     */
    @Override
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

    @Override
    public String getId() {
        return "ip_addresses";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode.locale,*/ "IPAddressesNode.label");
    }
    
    private TableListener tableListener = new TableListener() {
        @Override
        public void tableUpdated(Table<?> table) {
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
            netDeviceNode._netDevicesNode.serverNode.serversNode.rootNode.conn.getIpAddresses().addTableListener(tableListener, 100);
            verifyIPAddresses();
        }
    }
    
    void stop() {
        synchronized(ipAddressNodes) {
            RootNodeImpl rootNode = netDeviceNode._netDevicesNode.serverNode.serversNode.rootNode;
            rootNode.conn.getIpAddresses().removeTableListener(tableListener);
            for(IPAddressNode ipAddressNode : ipAddressNodes) {
                ipAddressNode.stop();
                rootNode.nodeRemoved();
            }
            ipAddressNodes.clear();
        }
    }

    private void verifyIPAddresses() throws IOException, SQLException {
        final RootNodeImpl rootNode = netDeviceNode._netDevicesNode.serverNode.serversNode.rootNode;

        NetDevice netDevice = netDeviceNode.getNetDevice();
        List<IPAddress> ipAddresses = netDevice.getIPAddresses();
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
                assert !ipAddress.isWildcard() : "Wildcard IP address on NetDevice: "+netDevice;
                if(c>=ipAddressNodes.size() || !ipAddress.equals(ipAddressNodes.get(c).getIPAddress())) {
                    // Insert into proper index
                    IPAddressNode ipAddressNode = new IPAddressNode(this, ipAddress);
                    ipAddressNodes.add(c, ipAddressNode);
                    ipAddressNode.start();
                    rootNode.nodeAdded();
                }
            }
        }
    }

    File getPersistenceDirectory() throws IOException {
        File dir = new File(netDeviceNode.getPersistenceDirectory(), "ip_addresses");
        if(!dir.exists()) {
            if(!dir.mkdir()) {
                throw new IOException(
                    accessor.getMessage(
                        //netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode.locale,
                        "error.mkdirFailed",
                        dir.getCanonicalPath()
                    )
                );
            }
        }
        return dir;
    }
}
