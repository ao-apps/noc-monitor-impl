/*
 * Copyright 2008 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.NetDevice;
import com.aoindustries.aoserv.client.NetDeviceID;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.Node;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * The node per server.
 *
 * @author  AO Industries, Inc.
 */
public class NetDeviceNode extends NodeImpl {

    final NetDevicesNode _networkDevicesNode;
    private final NetDevice _netDevice;
    private final String _label;

    volatile private NetDeviceBitRateNode _netDeviceBitRateNode;
    volatile private NetDeviceBondingNode _netDeviceBondingNode;
    volatile private IPAddressesNode _ipAddressesNode;

    NetDeviceNode(NetDevicesNode networkDevicesNode, NetDevice netDevice, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
        super(port, csf, ssf);
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        this._networkDevicesNode = networkDevicesNode;
        this._netDevice = netDevice;
        this._label = netDevice.getNetDeviceID().getName();
    }

    @Override
    public Node getParent() {
        return _networkDevicesNode;
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

    @Override
    public String getLabel() {
        return _label;
    }

    synchronized void start() throws IOException {
        // bit rate and network bonding monitoring only supported for AOServer
        if(_networkDevicesNode.getServer().getAOServer()!=null) {
            // bit rate for non-loopback devices
            if(!_netDevice.getNetDeviceID().isLoopback()) {
                _netDeviceBitRateNode = new NetDeviceBitRateNode(this, port, csf, ssf);
                _netDeviceBitRateNode.start();
                _networkDevicesNode.serverNode.serversNode.rootNode.nodeAdded();
            }
            // bonding
            if(_label.equals(NetDeviceID.BOND0)) {
                _netDeviceBondingNode = new NetDeviceBondingNode(this, port, csf, ssf);
                _netDeviceBondingNode.start();
                _networkDevicesNode.serverNode.serversNode.rootNode.nodeAdded();
            }
        }

        _ipAddressesNode = new IPAddressesNode(this, port, csf, ssf);
        _ipAddressesNode.start();
        _networkDevicesNode.serverNode.serversNode.rootNode.nodeAdded();
    }

    synchronized void stop() {
        IPAddressesNode ipAddressesNode = this._ipAddressesNode;
        if(ipAddressesNode!=null) {
            ipAddressesNode.stop();
            this._ipAddressesNode = null;
            _networkDevicesNode.serverNode.serversNode.rootNode.nodeRemoved();
        }

        NetDeviceBondingNode netDeviceBondingNode = this._netDeviceBondingNode;
        if(netDeviceBondingNode!=null) {
            netDeviceBondingNode.stop();
            this._netDeviceBondingNode = null;
            _networkDevicesNode.serverNode.serversNode.rootNode.nodeRemoved();
        }

        NetDeviceBitRateNode netDeviceBitRateNode = this._netDeviceBitRateNode;
        if(netDeviceBitRateNode!=null) {
            netDeviceBitRateNode.stop();
            this._netDeviceBitRateNode = null;
            _networkDevicesNode.serverNode.serversNode.rootNode.nodeRemoved();
        }
    }

    File getPersistenceDirectory() throws IOException {
        File dir = new File(_networkDevicesNode.getPersistenceDirectory(), _label);
        if(!dir.exists()) {
            if(!dir.mkdir()) {
                throw new IOException(
                    ApplicationResourcesAccessor.getMessage(
                        _networkDevicesNode.serverNode.serversNode.rootNode.locale,
                        "error.mkdirFailed",
                        dir.getCanonicalPath()
                    )
                );
            }
        }
        return dir;
    }
}
