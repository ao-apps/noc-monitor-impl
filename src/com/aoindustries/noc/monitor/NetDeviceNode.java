/*
 * Copyright 2008-2012, 2014, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.linux.AOServer;
import com.aoindustries.aoserv.client.net.NetDevice;
import com.aoindustries.aoserv.client.net.NetDeviceID;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.table.Table;
import com.aoindustries.table.TableListener;
import com.aoindustries.util.WrappedException;
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
	private final NetDevice _device;
	private final String _label;

	private static class ChildLock {}
	private final ChildLock childLock = new ChildLock();
	private boolean started;

	volatile private NetDeviceBitRateNode _netDeviceBitRateNode;
	volatile private NetDeviceBondingNode _netDeviceBondingNode;
	volatile private IPAddressesNode _ipAddressesNode;

	NetDeviceNode(NetDevicesNode networkDevicesNode, NetDevice device, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException, SQLException, IOException {
		super(port, csf, ssf);
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		this._networkDevicesNode = networkDevicesNode;
		this._device = device;
		this._label = device.getDeviceId().getName();
	}

	@Override
	public NetDevicesNode getParent() {
		return _networkDevicesNode;
	}

	public NetDevice getNetDevice() {
		return _device;
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

	private final TableListener tableListener = (Table<?> table) -> {
		try {
			verifyChildren();
		} catch(IOException | SQLException err) {
			throw new WrappedException(err);
		}
	};

	void start() throws IOException, SQLException {
		AOServConnector conn = _networkDevicesNode.serverNode.serversNode.rootNode.conn;
		synchronized(childLock) {
			if(started) throw new IllegalStateException();
			started = true;
			conn.getIpAddresses().addTableListener(tableListener, 100);
			conn.getNetDevices().addTableListener(tableListener, 100);
		}
		verifyChildren();
	}

	void stop() {
		RootNodeImpl rootNode = _networkDevicesNode.serverNode.serversNode.rootNode;
		AOServConnector conn = rootNode.conn;
		synchronized(childLock) {
			started = false;
			conn.getIpAddresses().removeTableListener(tableListener);
			conn.getNetDevices().removeTableListener(tableListener);
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
	}

	private void verifyChildren() throws RemoteException, IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(childLock) {
			if(!started) return;
		}

		RootNodeImpl rootNode = _networkDevicesNode.serverNode.serversNode.rootNode;

		AOServer aoServer = _networkDevicesNode.getServer().getAOServer();
		NetDevice currentNetDevice = _device.getTable().getConnector().getNetDevices().get(_device.getPkey());
		NetDeviceID netDeviceID = currentNetDevice.getDeviceId();
		boolean hasIpAddresses = !currentNetDevice.getIPAddresses().isEmpty();

		synchronized(childLock) {
			if(started) {
				// bit rate and network bonding monitoring only supported for AOServer
				if(
					aoServer != null
					// bit rate for non-loopback devices
					&& !netDeviceID.isLoopback()
					// and non-BMC
					&& !netDeviceID.getName().equals(NetDeviceID.BMC)
				) {
					if(_netDeviceBitRateNode==null) {
						_netDeviceBitRateNode = new NetDeviceBitRateNode(this, port, csf, ssf);
						_netDeviceBitRateNode.start();
						rootNode.nodeAdded();
					}
				} else {
					if(_netDeviceBitRateNode!=null) {
						_netDeviceBitRateNode.stop();
						_netDeviceBitRateNode = null;
						rootNode.nodeRemoved();
					}
				}
				// bonding
				if(
					aoServer != null
					&& (
						_label.equals(NetDeviceID.BOND0) // TODO: Flag for "net_devices.isBonded"
						|| _label.equals(NetDeviceID.BOND1)
						|| _label.equals(NetDeviceID.BOND2)
					)
				) {
					if(_netDeviceBondingNode==null) {
						_netDeviceBondingNode = new NetDeviceBondingNode(this, port, csf, ssf);
						_netDeviceBondingNode.start();
						rootNode.nodeAdded();
					}
				} else {
					if(_netDeviceBondingNode!=null) {
						_netDeviceBondingNode.stop();
						_netDeviceBondingNode = null;
						rootNode.nodeRemoved();
					}
				}
				// IP Addresses
				if(hasIpAddresses) {
					if(_ipAddressesNode==null) {
						_ipAddressesNode = new IPAddressesNode(this, port, csf, ssf);
						_ipAddressesNode.start();
						rootNode.nodeAdded();
					}
				} else {
					if(_ipAddressesNode!=null) {
						_ipAddressesNode.stop();
						_ipAddressesNode = null;
						rootNode.nodeRemoved();
					}
				}
			}
		}
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File(_networkDevicesNode.getPersistenceDirectory(), _label);
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				throw new IOException(
					accessor.getMessage(
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
