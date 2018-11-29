/*
 * Copyright 2008-2009, 2014, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.IPAddress;
import com.aoindustries.aoserv.client.IpAddressMonitoring;
import com.aoindustries.aoserv.client.NetDevice;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * The node of all IPAddresses per NetDevice.
 *
 * @author  AO Industries, Inc.
 */
public class IPAddressesNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	final NetDeviceNode netDeviceNode;
	final UnallocatedNode unallocatedNode;
	final RootNodeImpl rootNode;

	private final List<IPAddressNode> ipAddressNodes = new ArrayList<>();
	private boolean started;

	IPAddressesNode(NetDeviceNode netDeviceNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);

		this.netDeviceNode = netDeviceNode;
		this.unallocatedNode = null;

		this.rootNode = netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode;
	}

	IPAddressesNode(UnallocatedNode unallocatedNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
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
	public List<IPAddressNode> getChildren() {
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
		return accessor.getMessage(rootNode.locale, "IPAddressesNode.label");
	}

	private final TableListener tableListener = (Table<?> table) -> {
		try {
			verifyIPAddresses();
		} catch(IOException | SQLException err) {
			throw new WrappedException(err);
		}
	};

	void start() throws IOException, SQLException {
		synchronized(ipAddressNodes) {
			if(started) throw new IllegalStateException();
			started = true;
			rootNode.conn.getIpAddresses().addTableListener(tableListener, 100);
		}
		verifyIPAddresses();
	}

	void stop() {
		synchronized(ipAddressNodes) {
			started = false;
			rootNode.conn.getIpAddresses().removeTableListener(tableListener);
			for(IPAddressNode ipAddressNode : ipAddressNodes) {
				ipAddressNode.stop();
				rootNode.nodeRemoved();
			}
			ipAddressNodes.clear();
		}
	}

	private void verifyIPAddresses() throws RemoteException, IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(ipAddressNodes) {
			if(!started) return;
		}

		List<IPAddress> ipAddresses;
		if(netDeviceNode != null) {
			NetDevice device = netDeviceNode.getNetDevice();
			List<IPAddress> ndIPs = device.getIPAddresses();
			ipAddresses = new ArrayList<>(ndIPs.size());
			for(IPAddress ipAddress : ndIPs) {
				if(ipAddress.getInetAddress().isUnspecified()) throw new AssertionError("Unspecified IP address on NetDevice: "+device);
				IpAddressMonitoring iam = ipAddress.getMonitoring();
				if(iam != null && iam.getEnabled()) ipAddresses.add(ipAddress);
			}
		} else {
			// Find all unallocated IP addresses, except the unspecified
			List<IPAddress> allIPs = rootNode.conn.getIpAddresses().getRows();
			ipAddresses = new ArrayList<>(allIPs.size());
			for(IPAddress ip : allIPs) {
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
				Iterator<IPAddressNode> ipAddressNodeIter = ipAddressNodes.iterator();
				while(ipAddressNodeIter.hasNext()) {
					IPAddressNode ipAddressNode = ipAddressNodeIter.next();
					IPAddress oldIpAddress = ipAddressNode.getIPAddress();
					// Find the any new version of the IP address matching this node
					IPAddress newIpAddress = null;
					for(IPAddress ipAddress : ipAddresses) {
						if(ipAddress.equals(oldIpAddress)) {
							newIpAddress = ipAddress;
							break;
						}
					}
					if(
						// Node no longer exists
						newIpAddress==null
						// Node has a new label
						|| !IPAddressNode.getLabel(newIpAddress).equals(ipAddressNode.getLabel())
					) {
						ipAddressNode.stop();
						ipAddressNodeIter.remove();
						rootNode.nodeRemoved();
					}
				}
				// Add new ones
				for(int c=0;c<ipAddresses.size();c++) {
					IPAddress ipAddress = ipAddresses.get(c);
					if(c>=ipAddressNodes.size() || !ipAddress.equals(ipAddressNodes.get(c).getIPAddress())) {
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
