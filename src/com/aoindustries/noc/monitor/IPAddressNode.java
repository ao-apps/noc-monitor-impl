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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The node per IPAddress.
 *
 * @author  AO Industries, Inc.
 */
public class IPAddressNode extends NodeImpl {

    private static final long serialVersionUID = 1L;

    final IPAddressesNode ipAddressesNode;
    private final IPAddress ipAddress;
    private final String id;
    private final String label;
    private final boolean isPingable;

    volatile private PingNode pingNode;
    volatile private NetBindsNode netBindsNode;
    volatile private ReverseDnsNode reverseDnsNode;
    volatile private BlacklistsNode blacklistsNode;

    IPAddressNode(IPAddressesNode ipAddressesNode, IPAddress ipAddress) throws SQLException, IOException {
        this.ipAddressesNode = ipAddressesNode;
        this.ipAddress = ipAddress;
        InetAddress ip = ipAddress.getInetAddress();
        InetAddress externalIp = ipAddress.getExternalIpAddress();
        this.id = ip.toString();
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
    public String getId() {
        return id;
    }

    @Override
    public String getLabel() {
        return label;
    }

    synchronized void start() throws IOException, SQLException {
        RootNodeImpl rootNode = ipAddressesNode.netDeviceNode._netDevicesNode.serverNode.serversNode.rootNode;
        if(isPingable) {
            if(pingNode==null) {
                pingNode = new PingNode(this);
                pingNode.start();
                rootNode.nodeAdded();
            }
        }
        if(netBindsNode==null) {
            netBindsNode = new NetBindsNode(this);
            netBindsNode.start();
            rootNode.nodeAdded();
        }
        // Skip loopback device
        if(reverseDnsNode==null && !ipAddressesNode.netDeviceNode.getNetDevice().getNetDeviceID().isLoopback()) {
            InetAddress ip = ipAddress.getExternalIpAddress();
            if(ip==null) ip = ipAddress.getInetAddress();
            // Skip private IP addresses
            if(!(ip.isUniqueLocal() || ip.isLooback())) {
                reverseDnsNode = new ReverseDnsNode(this);
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
                blacklistsNode = new BlacklistsNode(this);
                blacklistsNode.start();
                rootNode.nodeAdded();
            }
        }
    }

    synchronized void stop() {
        RootNodeImpl rootNode = ipAddressesNode.netDeviceNode._netDevicesNode.serverNode.serversNode.rootNode;

        if(blacklistsNode!=null) {
            blacklistsNode.stop();
            rootNode.nodeRemoved();
            blacklistsNode = null;
        }

        if(reverseDnsNode!=null) {
            reverseDnsNode.stop();
            rootNode.nodeRemoved();
            reverseDnsNode = null;
        }

        if(netBindsNode!=null) {
            netBindsNode.stop();
            rootNode.nodeRemoved();
            netBindsNode = null;
        }

        if(pingNode!=null) {
            pingNode.stop();
            rootNode.nodeRemoved();
            pingNode = null;
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
