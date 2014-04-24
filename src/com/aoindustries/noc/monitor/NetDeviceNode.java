/*
 * Copyright 2008-2012, 2014 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.NetDevice;
import com.aoindustries.aoserv.client.NetDeviceID;
import com.aoindustries.noc.monitor.common.AlertLevel;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * The node per server.
 *
 * @author  AO Industries, Inc.
 */
public class NetDeviceNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	final NetDevicesNode _networkDevicesNode;
	private final NetDevice _netDevice;
	private final String _label;

	volatile private NetDeviceBitRateNode _netDeviceBitRateNode;
	volatile private NetDeviceBondingNode _netDeviceBondingNode;
	volatile private IPAddressesNode _ipAddressesNode;

	NetDeviceNode(NetDevicesNode networkDevicesNode, NetDevice netDevice, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException, SQLException, IOException {
		super(port, csf, ssf);
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		this._networkDevicesNode = networkDevicesNode;
		this._netDevice = netDevice;
		this._label = netDevice.getNetDeviceID().getName();
	}

	@Override
	public NetDevicesNode getParent() {
		return _networkDevicesNode;
	}

	public NetDevice getNetDevice() {
		return _netDevice;
	}

	@Override
	public boolean getAllowsChildren() {
		return true;
	}

	@Override
	public List<NodeImpl> getChildren() {
		return getSnapshot(
			this._netDeviceBitRateNode,
			this._netDeviceBondingNode,
			this._ipAddressesNode
		);
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	public AlertLevel getAlertLevel() {
		return constrainAlertLevel(
			AlertLevelUtils.getMaxAlertLevel(
				this._netDeviceBitRateNode,
				this._netDeviceBondingNode,
				this._ipAddressesNode
			)
		);
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
		return _label;
	}

	synchronized void start() throws IOException, SQLException {
		final RootNodeImpl rootNode = _networkDevicesNode.serverNode.serversNode.rootNode;
		// bit rate and network bonding monitoring only supported for AOServer
		if(_networkDevicesNode.getServer().getAOServer()!=null) {
			NetDeviceID netDeviceID = _netDevice.getNetDeviceID();
			if(
				// bit rate for non-loopback devices
				!netDeviceID.isLoopback()
				// and non-BMC
				&& !netDeviceID.getName().equals(NetDeviceID.BMC)
			) {
				if(_netDeviceBitRateNode==null) {
					_netDeviceBitRateNode = new NetDeviceBitRateNode(this, port, csf, ssf);
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
					_netDeviceBondingNode = new NetDeviceBondingNode(this, port, csf, ssf);
					_netDeviceBondingNode.start();
					rootNode.nodeAdded();
				}
			}
		}

		if(_ipAddressesNode==null) {
			_ipAddressesNode = new IPAddressesNode(this, port, csf, ssf);
			_ipAddressesNode.start();
			rootNode.nodeAdded();
		}
	}

	synchronized void stop() {
		final RootNodeImpl rootNode = _networkDevicesNode.serverNode.serversNode.rootNode;
		if(_ipAddressesNode!=null) {
			_ipAddressesNode.stop();
			_ipAddressesNode = null;
			rootNode.nodeRemoved();
		}

		if(_netDeviceBondingNode!=null) {
			_netDeviceBondingNode.stop();
			_netDeviceBondingNode = null;
			rootNode.nodeRemoved();
		}

		if(_netDeviceBitRateNode!=null) {
			_netDeviceBitRateNode.stop();
			_netDeviceBitRateNode = null;
			rootNode.nodeRemoved();
		}
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File(_networkDevicesNode.getPersistenceDirectory(), _label);
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
