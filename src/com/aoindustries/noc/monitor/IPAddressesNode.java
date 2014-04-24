/*
 * Copyright 2008-2009, 2014 by AO Industries, Inc.,
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
	private final List<IPAddressNode> ipAddressNodes = new ArrayList<>();

	IPAddressesNode(NetDeviceNode netDeviceNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);

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
		return accessor.getMessage(/*netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode.locale,*/ "IPAddressesNode.label");
	}

	private final TableListener tableListener = new TableListener() {
		@Override
		public void tableUpdated(Table<?> table) {
			try {
				verifyIPAddresses();
			} catch(IOException | SQLException err) {
				throw new WrappedException(err);
			}
		}
	};

	void start() throws IOException, SQLException {
		synchronized(ipAddressNodes) {
			netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode.conn.getIpAddresses().addTableListener(tableListener, 100);
			verifyIPAddresses();
		}
	}

	void stop() {
		synchronized(ipAddressNodes) {
			RootNodeImpl rootNode = netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode;
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

		final RootNodeImpl rootNode = netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode;

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
				assert !ipAddress.getInetAddress().isUnspecified() : "Unspecified IP address on NetDevice: "+netDevice;
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
