/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.IPAddress;
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
import javax.swing.SwingUtilities;

/**
 * The node per IPAddress.
 *
 * @author  AO Industries, Inc.
 */
public class IPAddressNode extends NodeImpl {

    final IPAddressesNode ipAddressesNode;
    private final IPAddress ipAddress;
    private final String label;
    private final boolean isPingable;

    volatile private PingNode pingNode;
    //volatile private NetBindsNode netBindsNode;

    IPAddressNode(IPAddressesNode ipAddressesNode, IPAddress ipAddress, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException, SQLException, IOException {
        super(port, csf, ssf);
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        this.ipAddressesNode = ipAddressesNode;
        this.ipAddress = ipAddress;
        String ip = ipAddress.getIPAddress();
        String externalIp = ipAddress.getExternalIpAddress();
        this.label = externalIp==null ? ip : (ip+'@'+externalIp);
        // Private IPs and loopback IPs are not externally pingable
        this.isPingable =
            ipAddress.isPingMonitorEnabled()
            && (
                (externalIp!=null && !IPAddress.isPrivate(externalIp))
                || !ipAddress.isPrivate()
            ) && !ipAddress.getNetDevice().getNetDeviceID().isLoopback()
        ;
    }

    public Node getParent() {
        return ipAddressesNode;
    }
    
    public IPAddress getIPAddress() {
        return ipAddress;
    }

    public boolean getAllowsChildren() {
        return true;
    }

    /**
     * For thread safety and encapsulation, returns an unmodifiable copy of the array.
     */
    public List<? extends Node> getChildren() {
        List<NodeImpl> children = new ArrayList<NodeImpl>();

        PingNode localPingNode = this.pingNode;
        if(localPingNode!=null) children.add(localPingNode);

        //NetBindsNode localNetBindsNode = this.netBindsNode;
        //if(localNetBindsNode!=null) children.add(localNetBindsNode);

        return Collections.unmodifiableList(children);
    }

    /**
     * The alert level is equal to the highest alert level of its children.
     */
    public AlertLevel getAlertLevel() {
        AlertLevel level = AlertLevel.NONE;

        PingNode localPingNode = this.pingNode;
        if(localPingNode!=null) {
            AlertLevel pingNodeLevel = localPingNode.getAlertLevel();
            if(pingNodeLevel.compareTo(level)>0) level = pingNodeLevel;
        }

        /*NetBindsNode localNetBindsNode = this.netBindsNode;
        if(localNetBindsNode!=null) {
            AlertLevel netBindsNodeLevel = localNetBindsNode.getAlertLevel();
            if(netBindsNodeLevel.compareTo(level)>0) level = netBindsNodeLevel;
        }*/

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
        return label;
    }

    synchronized void start() throws RemoteException, IOException {
        RootNodeImpl rootNode = ipAddressesNode.netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode;
        if(isPingable) {
            if(pingNode==null) {
                pingNode = new PingNode(this, port, csf, ssf);
                pingNode.start();
                rootNode.nodeAdded();
            }
        }
        /*if(netBindsNode==null) {
            netBindsNode = new NetBindsNode(this, port, csf, ssf);
            netBindsNode.start();
            ipAddressesNode.netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode.nodeAdded();
        }*/
    }

    synchronized void stop() {
        RootNodeImpl rootNode = ipAddressesNode.netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode;

        /*if(netBindsNode!=null) {
            netBindsNode.stop();
            netBindsNode = null;
            rootNode.nodeRemoved();
        }*/

        if(isPingable) {
            if(pingNode!=null) {
                pingNode.stop();
                pingNode = null;
                rootNode.nodeRemoved();
            }
        }
    }

    File getPersistenceDirectory() throws IOException {
        File dir = new File(ipAddressesNode.getPersistenceDirectory(), ipAddress.getIPAddress());
        if(!dir.exists()) {
            if(!dir.mkdir()) {
                throw new IOException(
                    ApplicationResourcesAccessor.getMessage(
                        ipAddressesNode.netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode.locale,
                        "error.mkdirFailed",
                        dir.getCanonicalPath()
                    )
                );
            }
        }
        return dir;
    }
}
