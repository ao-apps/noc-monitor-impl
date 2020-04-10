/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2014, 2016, 2018, 2019, 2020  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of noc-monitor-impl.
 *
 * noc-monitor-impl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * noc-monitor-impl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with noc-monitor-impl.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.noc.monitor.net;

import com.aoindustries.aoserv.client.net.Device;
import com.aoindustries.aoserv.client.net.IpAddress;
import com.aoindustries.aoserv.client.net.monitoring.IpAddressMonitoring;
import com.aoindustries.exception.WrappedException;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.NodeImpl;
import com.aoindustries.noc.monitor.RootNodeImpl;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.table.Table;
import com.aoindustries.table.TableListener;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * The node of all IpAddress per Device.
 *
 * @author  AO Industries, Inc.
 */
public class IpAddressesNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	final DeviceNode netDeviceNode;
	final UnallocatedNode unallocatedNode;
	public final RootNodeImpl rootNode;

	private final List<IpAddressNode> ipAddressNodes = new ArrayList<>();
	private boolean started;

	IpAddressesNode(DeviceNode netDeviceNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);

		this.netDeviceNode = netDeviceNode;
		this.unallocatedNode = null;

		this.rootNode = netDeviceNode._networkDevicesNode.hostNode.hostsNode.rootNode;
	}

	IpAddressesNode(UnallocatedNode unallocatedNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);

		this.netDeviceNode = null;
		this.unallocatedNode = unallocatedNode;

		this.rootNode = unallocatedNode.rootNode;
	}

	@Override
	public NodeImpl getParent() {
		return netDeviceNode!=null ? netDeviceNode : unallocatedNode;
	}

	@Override
	public boolean getAllowsChildren() {
		return true;
	}

	@Override
	public List<IpAddressNode> getChildren() {
		synchronized(ipAddressNodes) {
			return getSnapshot(ipAddressNodes);
		}
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	public AlertLevel getAlertLevel() {
		AlertLevel level;
		synchronized(ipAddressNodes) {
			level = AlertLevelUtils.getMaxAlertLevel(
				ipAddressNodes
			);
		}
		return constrainAlertLevel(level);
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
		return accessor.getMessage(rootNode.locale, "IpAddressesNode.label");
	}

	private final TableListener tableListener = (Table<?> table) -> {
		try {
			verifyIpAddresses();
		} catch(IOException | SQLException err) {
			throw new WrappedException(err);
		}
	};

	void start() throws IOException, SQLException {
		synchronized(ipAddressNodes) {
			if(started) throw new IllegalStateException();
			started = true;
			rootNode.conn.getNet().getIpAddress().addTableListener(tableListener, 100);
		}
		verifyIpAddresses();
	}

	void stop() {
		synchronized(ipAddressNodes) {
			started = false;
			rootNode.conn.getNet().getIpAddress().removeTableListener(tableListener);
			for(IpAddressNode ipAddressNode : ipAddressNodes) {
				ipAddressNode.stop();
				rootNode.nodeRemoved();
			}
			ipAddressNodes.clear();
		}
	}

	private void verifyIpAddresses() throws RemoteException, IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(ipAddressNodes) {
			if(!started) return;
		}

		List<IpAddress> ipAddresses;
		if(netDeviceNode != null) {
			Device device = netDeviceNode.getNetDevice();
			List<IpAddress> ndIPs = device.getIPAddresses();
			ipAddresses = new ArrayList<>(ndIPs.size());
			for(IpAddress ipAddress : ndIPs) {
				if(ipAddress.getInetAddress().isUnspecified()) throw new AssertionError("Unspecified IP address on Device: "+device);
				IpAddressMonitoring iam = ipAddress.getMonitoring();
				if(iam != null && iam.getEnabled()) ipAddresses.add(ipAddress);
			}
		} else {
			// Find all unallocated IP addresses, except the unspecified
			List<IpAddress> allIPs = rootNode.conn.getNet().getIpAddress().getRows();
			ipAddresses = new ArrayList<>(allIPs.size());
			for(IpAddress ip : allIPs) {
				if(
					!ip.getInetAddress().isUnspecified()
					&& ip.getDevice() == null
				) {
					IpAddressMonitoring iam = ip.getMonitoring();
					if(
						iam != null
						&& iam.getEnabled()
					) {
						ipAddresses.add(ip);
					}
				}
			}
		}
		synchronized(ipAddressNodes) {
			if(started) {
				// Remove old ones
				Iterator<IpAddressNode> ipAddressNodeIter = ipAddressNodes.iterator();
				while(ipAddressNodeIter.hasNext()) {
					IpAddressNode ipAddressNode = ipAddressNodeIter.next();
					IpAddress oldIpAddress = ipAddressNode.getIpAddress();
					// Find the any new version of the IP address matching this node
					IpAddress newIpAddress = null;
					for(IpAddress ipAddress : ipAddresses) {
						if(ipAddress.equals(oldIpAddress)) {
							newIpAddress = ipAddress;
							break;
						}
					}
					if(
						// Node no longer exists
						newIpAddress==null
						// Node has a new label
						|| !IpAddressNode.getLabel(newIpAddress).equals(ipAddressNode.getLabel())
					) {
						ipAddressNode.stop();
						ipAddressNodeIter.remove();
						rootNode.nodeRemoved();
					}
				}
				// Add new ones
				for(int c=0;c<ipAddresses.size();c++) {
					IpAddress ipAddress = ipAddresses.get(c);
					if(c>=ipAddressNodes.size() || !ipAddress.equals(ipAddressNodes.get(c).getIpAddress())) {
						// Insert into proper index
						IpAddressNode ipAddressNode = new IpAddressNode(this, ipAddress, port, csf, ssf);
						ipAddressNodes.add(c, ipAddressNode);
						ipAddressNode.start();
						rootNode.nodeAdded();
					}
				}
			}
		}
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File(
			netDeviceNode!=null
				? netDeviceNode.getPersistenceDirectory()
				: unallocatedNode.getPersistenceDirectory(),
			"ip_addresses"
		);
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				throw new IOException(
					accessor.getMessage(
						rootNode.locale,
						"error.mkdirFailed",
						dir.getCanonicalPath()
					)
				);
			}
		}
		return dir;
	}
}
