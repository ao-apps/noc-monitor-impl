/*
 * Copyright 2008-2009, 2014, 2016, 2017, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.IPAddress;
import com.aoindustries.aoserv.client.IpAddressMonitoring;
import com.aoindustries.net.InetAddress;
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
 * The node per IPAddress.
 *
 * @author  AO Industries, Inc.
 */
public class IPAddressNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	static String getLabel(IPAddress ipAddress) {
		InetAddress ip = ipAddress.getInetAddress();
		InetAddress externalIp = ipAddress.getExternalInetAddress();
		return
			(externalIp==null ? ip.toString() : (ip.toString()+"@"+externalIp.toString()))
			+ "/" + ipAddress.getHostname()
		;
	}

	static boolean isPingable(IPAddressesNode ipAddressesNode, IPAddress ipAddress) throws SQLException, IOException {
		// Private IPs and loopback IPs are not externally pingable
		InetAddress ip = ipAddress.getInetAddress();
		InetAddress externalIp = ipAddress.getExternalInetAddress();
		IpAddressMonitoring iam;
		return
			// Must have ping monitoring enabled
			((iam = ipAddress.getMonitoring()) != null)
			&& iam.getPingMonitorEnabled()
		;
	}

	final IPAddressesNode ipAddressesNode;
	private final IPAddress ipAddress;
	private final String label;

	private static class ChildLock {}
	private final ChildLock childLock = new ChildLock();
	private boolean started;

	volatile private PingNode pingNode;
	volatile private NetBindsNode netBindsNode;
	volatile private ReverseDnsNode reverseDnsNode;
	volatile private BlacklistsNode blacklistsNode;

	IPAddressNode(IPAddressesNode ipAddressesNode, IPAddress ipAddress, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException, SQLException, IOException {
		super(port, csf, ssf);
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		this.ipAddressesNode = ipAddressesNode;
		this.ipAddress = ipAddress;
		this.label = getLabel(ipAddress);
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

	private final TableListener tableListener = (Table<?> table) -> {
		try {
			verifyChildren();
		} catch(IOException | SQLException err) {
			throw new WrappedException(err);
		}
	};

	void start() throws RemoteException, IOException, SQLException {
		AOServConnector conn = ipAddressesNode.rootNode.conn;
		synchronized(childLock) {
			if(started) throw new IllegalStateException();
			started = true;
			conn.getIpAddresses().addTableListener(tableListener, 100);
			conn.getIpAddressMonitoring().addTableListener(tableListener, 100);
			conn.getNetBinds().addTableListener(tableListener, 100);
			conn.getNetDevices().addTableListener(tableListener, 100);
			conn.getNetDeviceIDs().addTableListener(tableListener, 100);
			conn.getServers().addTableListener(tableListener, 100);
		}
		verifyChildren();
	}

	void stop() {
		RootNodeImpl rootNode = ipAddressesNode.rootNode;
		AOServConnector conn = rootNode.conn;
		synchronized(childLock) {
			started = false;
			conn.getIpAddresses().removeTableListener(tableListener);
			conn.getIpAddressMonitoring().removeTableListener(tableListener);
			conn.getNetBinds().removeTableListener(tableListener);
			conn.getNetDevices().removeTableListener(tableListener);
			conn.getNetDeviceIDs().removeTableListener(tableListener);
			conn.getServers().removeTableListener(tableListener);
			if(blacklistsNode != null) {
				blacklistsNode.stop();
				blacklistsNode = null;
				rootNode.nodeRemoved();
			}
			if(reverseDnsNode != null) {
				reverseDnsNode.stop();
				reverseDnsNode = null;
				rootNode.nodeRemoved();
			}
			if(netBindsNode != null) {
				netBindsNode.stop();
				netBindsNode = null;
				rootNode.nodeRemoved();
			}
			if(pingNode != null) {
				pingNode.stop();
				pingNode = null;
				rootNode.nodeRemoved();
			}
		}
	}

	private void verifyChildren() throws RemoteException, IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(childLock) {
			if(!started) return;
		}

		final RootNodeImpl rootNode = ipAddressesNode.rootNode;

		IPAddress _currentIpAddress = ipAddress.getTable().getConnector().getIpAddresses().get(ipAddress.getPkey());
		boolean isPingable = isPingable(ipAddressesNode, _currentIpAddress);
		boolean isLoopback = 
			ipAddressesNode.netDeviceNode != null
			&& ipAddressesNode.netDeviceNode.getNetDevice().getDeviceId().isLoopback();
		InetAddress ip = _currentIpAddress.getExternalInetAddress();
		if(ip == null) ip = _currentIpAddress.getInetAddress();
		boolean hasNetBinds = ipAddressesNode.netDeviceNode != null && !NetBindsNode.getSettings(_currentIpAddress).isEmpty();
		IpAddressMonitoring iam = _currentIpAddress.getMonitoring();

		synchronized(childLock) {
			if(started) {
				if(isPingable) {
					if(pingNode == null) {
						pingNode = new PingNode(this, port, csf, ssf);
						pingNode.start();
						rootNode.nodeAdded();
					}
				} else {
					if(pingNode != null) {
						pingNode.stop();
						pingNode = null;
						rootNode.nodeRemoved();
					}
				}
				if(hasNetBinds) {
					if(netBindsNode == null) {
						netBindsNode = new NetBindsNode(this, port, csf, ssf);
						netBindsNode.start();
						rootNode.nodeAdded();
					}
				} else {
					if(netBindsNode != null) {
						netBindsNode.stop();
						netBindsNode = null;
						rootNode.nodeRemoved();
					}
				}
				if(
					// Must have DNS verification enabled
					iam != null
					&& (
						iam.getVerifyDnsPtr()
						|| iam.getVerifyDnsA()
					)
				) {
					if(reverseDnsNode == null) {
						reverseDnsNode = new ReverseDnsNode(this, port, csf, ssf);
						reverseDnsNode.start();
						rootNode.nodeAdded();
					}
				} else {
					if(reverseDnsNode != null) {
						reverseDnsNode.stop();
						reverseDnsNode = null;
						rootNode.nodeRemoved();
					}
				}
				if(
					// Skip loopback device
					!isLoopback
					// Skip private IP addresses
					&& !(ip.isUniqueLocal() || ip.isLoopback())
				) {
					if(blacklistsNode == null) {
						blacklistsNode = new BlacklistsNode(this, port, csf, ssf);
						blacklistsNode.start();
						rootNode.nodeAdded();
					}
				} else {
					if(blacklistsNode != null) {
						blacklistsNode.stop();
						blacklistsNode = null;
						rootNode.nodeRemoved();
					}
				}
			}
		}
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File(ipAddressesNode.getPersistenceDirectory(), ipAddress.getInetAddress().toString());
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				throw new IOException(
					accessor.getMessage(
						ipAddressesNode.rootNode.locale,
						"error.mkdirFailed",
						dir.getCanonicalPath()
					)
				);
			}
		}
		return dir;
	}
}
