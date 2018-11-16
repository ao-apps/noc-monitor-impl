/*
 * Copyright 2008-2009, 2014, 2016, 2017, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.IPAddress;
import com.aoindustries.aoserv.client.NetBind;
import com.aoindustries.aoserv.client.NetDevice;
import com.aoindustries.aoserv.client.Server;
import com.aoindustries.net.InetAddress;
import com.aoindustries.net.Port;
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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * The node per NetBind.
 *
 * TODO: Add output of netstat -ln / ss -lnt here to detect extra ports.
 *
 * @author  AO Industries, Inc.
 */
public class NetBindsNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	final IPAddressNode ipAddressNode;
	private final List<NetBindNode> netBindNodes = new ArrayList<>();
	private boolean started;

	NetBindsNode(IPAddressNode ipAddressNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);

		this.ipAddressNode = ipAddressNode;
	}

	@Override
	public IPAddressNode getParent() {
		return ipAddressNode;
	}

	@Override
	public boolean getAllowsChildren() {
		return true;
	}

	@Override
	public List<NetBindNode> getChildren() {
		synchronized(netBindNodes) {
			return getSnapshot(netBindNodes);
		}
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	public AlertLevel getAlertLevel() {
		AlertLevel level;
		synchronized(netBindNodes) {
			level = AlertLevelUtils.getMaxAlertLevel(netBindNodes);
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
		return accessor.getMessage(ipAddressNode.ipAddressesNode.rootNode.locale, "NetBindsNode.label");
	}

	private final TableListener tableListener = (Table<?> table) -> {
		try {
			verifyNetBinds();
		} catch(IOException | SQLException err) {
			throw new WrappedException(err);
		}
	};

	void start() throws IOException, SQLException {
		AOServConnector conn = ipAddressNode.ipAddressesNode.rootNode.conn;
		synchronized(netBindNodes) {
			if(started) throw new IllegalStateException();
			started = true;
			conn.getHttpdJBossSites().addTableListener(tableListener, 100);
			conn.getHttpdSharedTomcats().addTableListener(tableListener, 100);
			conn.getHttpdSites().addTableListener(tableListener, 100);
			conn.getHttpdTomcatSites().addTableListener(tableListener, 100);
			conn.getHttpdTomcatStdSites().addTableListener(tableListener, 100);
			conn.getHttpdWorkers().addTableListener(tableListener, 100);
			conn.getIpAddresses().addTableListener(tableListener, 100);
			conn.getNetBinds().addTableListener(tableListener, 100);
			conn.getNetDevices().addTableListener(tableListener, 100);
		}
		verifyNetBinds();
	}

	void stop() {
		RootNodeImpl rootNode = ipAddressNode.ipAddressesNode.rootNode;
		AOServConnector conn = rootNode.conn;
		synchronized(netBindNodes) {
			started = false;
			conn.getHttpdJBossSites().removeTableListener(tableListener);
			conn.getHttpdSharedTomcats().removeTableListener(tableListener);
			conn.getHttpdSites().removeTableListener(tableListener);
			conn.getHttpdTomcatSites().removeTableListener(tableListener);
			conn.getHttpdTomcatStdSites().removeTableListener(tableListener);
			conn.getHttpdWorkers().removeTableListener(tableListener);
			conn.getIpAddresses().removeTableListener(tableListener);
			conn.getNetBinds().removeTableListener(tableListener);
			conn.getNetDevices().removeTableListener(tableListener);
			for(NetBindNode netBindNode : netBindNodes) {
				netBindNode.stop();
				rootNode.nodeRemoved();
			}
			netBindNodes.clear();
		}
	}

	static class NetMonitorSetting implements Comparable<NetMonitorSetting> {

		private final Server server;
		private final NetBind netBind;
		private final InetAddress ipAddress;
		private final Port port;

		private NetMonitorSetting(Server server, NetBind netBind, InetAddress ipAddress, Port port) {
			this.server = server;
			this.netBind = netBind;
			this.ipAddress = ipAddress;
			this.port = port;
		}

		@Override
		public int compareTo(NetMonitorSetting o) {
			// Server
			int diff = server.compareTo(o.server);
			if(diff!=0) return diff;
			// IP
			diff = ipAddress.compareTo(o.ipAddress);
			if(diff!=0) return diff;
			// port
			return port.compareTo(o.port);
		}

		@Override
		public boolean equals(Object O) {
			if(O==null) return false;
			if(!(O instanceof NetMonitorSetting)) return false;
			NetMonitorSetting other = (NetMonitorSetting)O;
			return
				port==other.port
				&& server.equals(other.server)
				&& netBind.equals(other.netBind)
				&& ipAddress.equals(other.ipAddress)
			;
		}

		@Override
		public int hashCode() {
			int hash = 7;
			hash = 11 * hash + server.hashCode();
			hash = 11 * hash + netBind.hashCode();
			hash = 11 * hash + ipAddress.hashCode();
			hash = 11 * hash + port.hashCode();
			return hash;
		}

		/**
		 * Gets the Server for this port.
		 */
		Server getServer() {
			return server;
		}

		NetBind getNetBind() {
			return netBind;
		}

		/**
		 * @return the ipAddress
		 */
		InetAddress getIpAddress() {
			return ipAddress;
		}

		/**
		 * @return the port
		 */
		Port getPort() {
			return port;
		}
	}

	/**
	 * The list of net binds is:
	 * The binds directly on the IP address plus the wildcard binds
	 */
	static List<NetMonitorSetting> getSettings(IPAddress ipAddress) throws IOException, SQLException {
		NetDevice netDevice = ipAddress.getNetDevice();
		if(netDevice == null) return Collections.emptyList();
		List<NetBind> directNetBinds = ipAddress.getNetBinds();

		// Find the wildcard IP address, if available
		Server server = netDevice.getServer();
		IPAddress wildcard = null;
		for(IPAddress ia : server.getIPAddresses()) {
			if(ia.getInetAddress().isUnspecified()) {
				wildcard = ia;
				break;
			}
		}
		List<NetBind> wildcardNetBinds;
		if(wildcard==null) wildcardNetBinds = Collections.emptyList();
		else wildcardNetBinds = server.getNetBinds(wildcard);

		InetAddress inetaddress = ipAddress.getInetAddress();
		List<NetMonitorSetting> netMonitorSettings = new ArrayList<>(directNetBinds.size() + wildcardNetBinds.size());
		for(NetBind netBind : directNetBinds) {
			if(netBind.isMonitoringEnabled() && !netBind.isDisabled()) {
				netMonitorSettings.add(
					new NetMonitorSetting(
						server,
						netBind,
						inetaddress,
						netBind.getPort()
					)
				);
			}
		}
		for(NetBind netBind : wildcardNetBinds) {
			if(netBind.isMonitoringEnabled() && !netBind.isDisabled()) {
				netMonitorSettings.add(
					new NetMonitorSetting(
						server,
						netBind,
						inetaddress,
						netBind.getPort()
					)
				);
			}
		}
		Collections.sort(netMonitorSettings);
		return netMonitorSettings;
	}

	private void verifyNetBinds() throws RemoteException, IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(netBindNodes) {
			if(!started) return;
		}

		final RootNodeImpl rootNode = ipAddressNode.ipAddressesNode.rootNode;

		IPAddress ipAddress = ipAddressNode.getIPAddress();
		ipAddress = ipAddress.getTable().getConnector().getIpAddresses().get(ipAddress.getPkey());
		List<NetMonitorSetting> netMonitorSettings = getSettings(ipAddress);

		synchronized(netBindNodes) {
			if(started) {
				// Remove old ones
				Iterator<NetBindNode> netBindNodeIter = netBindNodes.iterator();
				while(netBindNodeIter.hasNext()) {
					NetBindNode netBindNode = netBindNodeIter.next();
					NetMonitorSetting netMonitorSetting = netBindNode.getNetMonitorSetting();
					if(!netMonitorSettings.contains(netMonitorSetting)) {
						netBindNode.stop();
						netBindNodeIter.remove();
						rootNode.nodeRemoved();
					}
				}
				// Add new ones
				for(int c=0;c<netMonitorSettings.size();c++) {
					NetMonitorSetting netMonitorSetting = netMonitorSettings.get(c);
					if(c>=netBindNodes.size() || !netMonitorSetting.equals(netBindNodes.get(c).getNetMonitorSetting())) {
						// Insert into proper index
						NetBindNode netBindNode = new NetBindNode(this, netMonitorSetting, port, csf, ssf);
						netBindNodes.add(c, netBindNode);
						netBindNode.start();
						rootNode.nodeAdded();
					}
				}
			}
		}
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File(ipAddressNode.getPersistenceDirectory(), "net_binds");
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				throw new IOException(
					accessor.getMessage(
						ipAddressNode.ipAddressesNode.rootNode.locale,
						"error.mkdirFailed",
						dir.getCanonicalPath()
					)
				);
			}
		}
		return dir;
	}
}
