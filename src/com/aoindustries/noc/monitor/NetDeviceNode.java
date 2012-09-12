/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.NetDevice;
import com.aoindustries.aoserv.client.NetDeviceID;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.Node;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The node per server.
 *
 * @author  AO Industries, Inc.
 */
public class NetDeviceNode extends NodeImpl {

    private static final long serialVersionUID = 1L;

    final NetDevicesNode _netDevicesNode;
    private final NetDevice _netDevice;
    private final String _label;

    volatile private NetDeviceBitRateNode _netDeviceBitRateNode;
    volatile private NetDeviceBondingNode _netDeviceBondingNode;
    volatile private IPAddressesNode _ipAddressesNode;

    NetDeviceNode(NetDevicesNode netDevicesNode, NetDevice netDevice) throws SQLException, IOException {
        this._netDevicesNode = netDevicesNode;
        this._netDevice = netDevice;
        this._label = netDevice.getNetDeviceID().getName();
    }

    @Override
    public NetDevicesNode getParent() {
        return _netDevicesNode;
    }
    
    public NetDevice getNetDevice() {
        return _netDevice;
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
        List<NodeImpl> children = new ArrayList<NodeImpl>(3);

        NetDeviceBitRateNode netDeviceBitRateNode = this._netDeviceBitRateNode;
        if(netDeviceBitRateNode!=null) children.add(netDeviceBitRateNode);

        NetDeviceBondingNode netDeviceBondingNode = this._netDeviceBondingNode;
        if(netDeviceBondingNode!=null) children.add(netDeviceBondingNode);

        IPAddressesNode ipAddressesNode = this._ipAddressesNode;
        if(ipAddressesNode!=null) children.add(ipAddressesNode);

        return Collections.unmodifiableList(children);
    }

    /**
     * The alert level is equal to the highest alert level of its children.
     */
    @Override
    public AlertLevel getAlertLevel() {
        AlertLevel level = AlertLevel.NONE;

        NetDeviceBitRateNode netDeviceBitRateNode = this._netDeviceBitRateNode;
        if(netDeviceBitRateNode!=null) {
            AlertLevel netDeviceBitRateNodeLevel = netDeviceBitRateNode.getAlertLevel();
            if(netDeviceBitRateNodeLevel.compareTo(level)>0) level = netDeviceBitRateNodeLevel;
        }

        NetDeviceBondingNode netDeviceBondingNode = this._netDeviceBondingNode;
        if(netDeviceBondingNode!=null) {
            AlertLevel netDeviceBondinNodeLevel = netDeviceBondingNode.getAlertLevel();
            if(netDeviceBondinNodeLevel.compareTo(level)>0) level = netDeviceBondinNodeLevel;
        }

        IPAddressesNode ipAddressesNode = this._ipAddressesNode;
        if(ipAddressesNode!=null) {
            AlertLevel ipAddressesNodeLevel = ipAddressesNode.getAlertLevel();
            if(ipAddressesNodeLevel.compareTo(level)>0) level = ipAddressesNodeLevel;
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

    synchronized void start() throws IOException, SQLException {
        final RootNodeImpl rootNode = _netDevicesNode.serverNode.serversNode.rootNode;
        // bit rate and network bonding monitoring only supported for AOServer
        if(_netDevicesNode.getServer().getAOServer()!=null) {
            NetDeviceID netDeviceID = _netDevice.getNetDeviceID();
            if(
                // bit rate for non-loopback devices
                !netDeviceID.isLoopback()
                // and non-BMC
                && !netDeviceID.getName().equals(NetDeviceID.BMC)
            ) {
                if(_netDeviceBitRateNode==null) {
                    _netDeviceBitRateNode = new NetDeviceBitRateNode(this);
                    rootNode.initNode(_netDeviceBitRateNode);
                    _netDeviceBitRateNode.start();
                    rootNode.nodeAdded();
                }
            }
            // bonding
            if(
                _label.equals(NetDeviceID.BOND0)
                || _label.equals(NetDeviceID.BOND1)
                || _label.equals(NetDeviceID.BOND2)
            ) {
                if(_netDeviceBondingNode==null) {
                    _netDeviceBondingNode = new NetDeviceBondingNode(this);
                    rootNode.initNode(_netDeviceBondingNode);
                    _netDeviceBondingNode.start();
                    rootNode.nodeAdded();
                }
            }
        }

        if(_ipAddressesNode==null) {
            _ipAddressesNode = new IPAddressesNode(this);
            rootNode.initNode(_ipAddressesNode);
            _ipAddressesNode.start();
            rootNode.nodeAdded();
        }
    }

    synchronized void stop() {
        final RootNodeImpl rootNode = _netDevicesNode.serverNode.serversNode.rootNode;
        if(_ipAddressesNode!=null) {
            _ipAddressesNode.stop();
            rootNode.nodeRemoved();
            rootNode.destroyNode(_ipAddressesNode);
            _ipAddressesNode = null;
        }

        if(_netDeviceBondingNode!=null) {
            _netDeviceBondingNode.stop();
            rootNode.nodeRemoved();
            rootNode.destroyNode(_netDeviceBondingNode);
            _netDeviceBondingNode = null;
        }

        if(_netDeviceBitRateNode!=null) {
            _netDeviceBitRateNode.stop();
            rootNode.nodeRemoved();
            rootNode.destroyNode(_netDeviceBitRateNode);
            _netDeviceBitRateNode = null;
        }
    }

    File getPersistenceDirectory() throws IOException {
        File dir = new File(_netDevicesNode.getPersistenceDirectory(), _label);
        if(!dir.exists()) {
            if(!dir.mkdir()) {
                throw new IOException(
                    accessor.getMessage(
                        //_networkDevicesNode.serverNode.serversNode.rootNode.locale,
                        "error.mkdirFailed",
                        dir.getCanonicalPath()
                    )
                );
            }
        }
        return dir;
    }
}
