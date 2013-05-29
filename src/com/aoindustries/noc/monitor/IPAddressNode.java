/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.IPAddress;
import com.aoindustries.aoserv.client.validator.InetAddress;
import com.aoindustries.noc.monitor.common.AlertLevel;
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

    private static final long serialVersionUID = 1L;

    final IPAddressesNode ipAddressesNode;
    private final IPAddress ipAddress;
    private final String label;
    private final boolean isPingable;

    volatile private PingNode pingNode;
    volatile private NetBindsNode netBindsNode;
    volatile private ReverseDnsNode reverseDnsNode;
    volatile private BlacklistsNode blacklistsNode;

    IPAddressNode(IPAddressesNode ipAddressesNode, IPAddress ipAddress, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException, SQLException, IOException {
        super(port, csf, ssf);
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        this.ipAddressesNode = ipAddressesNode;
        this.ipAddress = ipAddress;
        InetAddress ip = ipAddress.getInetAddress();
        InetAddress externalIp = ipAddress.getExternalIpAddress();
        this.label =
            (externalIp==null ? ip.toString() : (ip.toString()+"@"+externalIp.toString()))
            + "/" + ipAddress.getHostname()
        ;
        // Private IPs and loopback IPs are not externally pingable
        this.isPingable =
            ipAddress.isPingMonitorEnabled()
            && (
                (externalIp!=null && !(externalIp.isUniqueLocal() || externalIp.isLooback()))
                || !(ip.isUniqueLocal() || ip.isLooback())
            ) && !ipAddress.getNetDevice().getNetDeviceID().isLoopback()
        ;
    }

    @Override
    public IPAddressesNode getParent() {
        return ipAddressesNode;
    }
    
    public IPAddress getIPAddress() {
        return ipAddress;
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    /**
     * For thread safety and encapsulation, returns an unmodifiable copy of the array.
     */
    @Override
    public List<NodeImpl> getChildren() {
        List<NodeImpl> children = new ArrayList<NodeImpl>(3);

        PingNode localPingNode = this.pingNode;
        if(localPingNode!=null) children.add(localPingNode);

        NetBindsNode localNetBindsNode = this.netBindsNode;
        if(localNetBindsNode!=null) children.add(localNetBindsNode);

        ReverseDnsNode localReverseDnsNode = this.reverseDnsNode;
        if(localReverseDnsNode!=null) children.add(localReverseDnsNode);

        BlacklistsNode localBlacklistsNode = this.blacklistsNode;
        if(localBlacklistsNode!=null) children.add(localBlacklistsNode);

        return Collections.unmodifiableList(children);
    }

    /**
     * The alert level is equal to the highest alert level of its children.
     */
    @Override
    public AlertLevel getAlertLevel() {
        AlertLevel level = AlertLevel.NONE;

        PingNode localPingNode = this.pingNode;
        if(localPingNode!=null) {
            AlertLevel pingNodeLevel = localPingNode.getAlertLevel();
            if(pingNodeLevel.compareTo(level)>0) level = pingNodeLevel;
        }

        NetBindsNode localNetBindsNode = this.netBindsNode;
        if(localNetBindsNode!=null) {
            AlertLevel netBindsNodeLevel = localNetBindsNode.getAlertLevel();
            if(netBindsNodeLevel.compareTo(level)>0) level = netBindsNodeLevel;
        }

        ReverseDnsNode localReverseDnsNode = this.reverseDnsNode;
        if(localReverseDnsNode!=null) {
            AlertLevel reverseDnsNodeLevel = localReverseDnsNode.getAlertLevel();
            if(reverseDnsNodeLevel.compareTo(level)>0) level = reverseDnsNodeLevel;
        }

        BlacklistsNode localBlacklistsNode = this.blacklistsNode;
        if(localBlacklistsNode!=null) {
            AlertLevel blacklistsNodeLevel = localBlacklistsNode.getAlertLevel();
            if(blacklistsNodeLevel.compareTo(level)>0) level = blacklistsNodeLevel;
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
        return label;
    }

    synchronized void start() throws RemoteException, IOException, SQLException {
        RootNodeImpl rootNode = ipAddressesNode.netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode;
        if(isPingable) {
            if(pingNode==null) {
                pingNode = new PingNode(this, port, csf, ssf);
                pingNode.start();
                rootNode.nodeAdded();
            }
        }
        if(netBindsNode==null) {
            netBindsNode = new NetBindsNode(this, port, csf, ssf);
            netBindsNode.start();
            rootNode.nodeAdded();
        }
        // Skip loopback device
        if(reverseDnsNode==null && !ipAddressesNode.netDeviceNode.getNetDevice().getNetDeviceID().isLoopback()) {
            InetAddress ip = ipAddress.getExternalIpAddress();
            if(ip==null) ip = ipAddress.getInetAddress();
            // Skip private IP addresses
            if(!(ip.isUniqueLocal() || ip.isLooback())) {
                reverseDnsNode = new ReverseDnsNode(this, port, csf, ssf);
                reverseDnsNode.start();
                rootNode.nodeAdded();
            }
        }
        // Skip loopback device
        if(blacklistsNode==null && !ipAddressesNode.netDeviceNode.getNetDevice().getNetDeviceID().isLoopback()) {
            InetAddress ip = ipAddress.getExternalIpAddress();
            if(ip==null) ip = ipAddress.getInetAddress();
            // Skip private IP addresses
            if(!(ip.isUniqueLocal() || ip.isLooback())) {
                blacklistsNode = new BlacklistsNode(this, port, csf, ssf);
                blacklistsNode.start();
                rootNode.nodeAdded();
            }
        }
    }

    synchronized void stop() {
        RootNodeImpl rootNode = ipAddressesNode.netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode;

        if(blacklistsNode!=null) {
            blacklistsNode.stop();
            blacklistsNode = null;
            rootNode.nodeRemoved();
        }

        if(reverseDnsNode!=null) {
            reverseDnsNode.stop();
            reverseDnsNode = null;
            rootNode.nodeRemoved();
        }

        if(netBindsNode!=null) {
            netBindsNode.stop();
            netBindsNode = null;
            rootNode.nodeRemoved();
        }

        if(pingNode!=null) {
            pingNode.stop();
            pingNode = null;
            rootNode.nodeRemoved();
        }
    }

    File getPersistenceDirectory() throws IOException {
        File dir = new File(ipAddressesNode.getPersistenceDirectory(), ipAddress.getInetAddress().toString());
        if(!dir.exists()) {
            if(!dir.mkdir()) {
                throw new IOException(
                    accessor.getMessage(
                        //ipAddressesNode.netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode.locale,
                        "error.mkdirFailed",
                        dir.getCanonicalPath()
                    )
                );
            }
        }
        return dir;
    }
}
