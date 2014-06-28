/*
 * Copyright 2008-2009, 2014 by AO Industries, Inc.,
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
			// Must be allocated
			ipAddressesNode.netDeviceNode != null
			// Must have ping monitoring enabled
			&& ipAddress.isPingMonitorEnabled()
			// Must be publicly addressable
			&& (
				(externalIp!=null && !(externalIp.isUniqueLocal() || externalIp.isLooback()))
				|| !(ip.isUniqueLocal() || ip.isLooback())
			)
			// Must not be on loopback device
			&& !ipAddress.getNetDevice().getNetDeviceID().isLoopback()
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

	@Override
	public List<NodeImpl> getChildren() {
		return getSnapshot(
			this.pingNode,
			this.netBindsNode,
			this.reverseDnsNode,
			this.blacklistsNode
		);
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	public AlertLevel getAlertLevel() {
		return constrainAlertLevel(
			AlertLevelUtils.getMaxAlertLevel(
				this.pingNode,
				this.netBindsNode,
				this.reverseDnsNode,
				this.blacklistsNode
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
		return label;
	}

	synchronized void start() throws RemoteException, IOException, SQLException {
		RootNodeImpl rootNode = ipAddressesNode.rootNode;
		if(isPingable && pingNode==null) {
			pingNode = new PingNode(this, port, csf, ssf);
			pingNode.start();
			rootNode.nodeAdded();
		}
		if(ipAddressesNode.netDeviceNode!=null && netBindsNode==null) {
			netBindsNode = new NetBindsNode(this, port, csf, ssf);
			netBindsNode.start();
			rootNode.nodeAdded();
		}
		// Skip loopback device
		if(
			ipAddressesNode.netDeviceNode == null
			|| !ipAddressesNode.netDeviceNode.getNetDevice().getNetDeviceID().isLoopback()
		) {
			if(reverseDnsNode==null) {
				InetAddress ip = ipAddress.getExternalIpAddress();
				if(ip==null) ip = ipAddress.getInetAddress();
				// Skip private IP addresses
				if(!(ip.isUniqueLocal() || ip.isLooback())) {
					reverseDnsNode = new ReverseDnsNode(this, port, csf, ssf);
					reverseDnsNode.start();
					rootNode.nodeAdded();
				}
			}
			if(blacklistsNode==null) {
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
	}

	synchronized void stop() {
		RootNodeImpl rootNode = ipAddressesNode.rootNode;

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
						//ipAddressesNode.rootNode.locale,
						"error.mkdirFailed",
						dir.getCanonicalPath()
					)
				);
			}
		}
		return dir;
	}
}
