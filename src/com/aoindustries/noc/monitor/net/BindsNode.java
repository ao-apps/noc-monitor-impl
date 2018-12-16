/*
 * Copyright 2008-2009, 2014, 2016, 2017, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.net;

import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.net.Bind;
import com.aoindustries.aoserv.client.net.Device;
import com.aoindustries.aoserv.client.net.Host;
import com.aoindustries.aoserv.client.net.IpAddress;
import com.aoindustries.net.InetAddress;
import com.aoindustries.net.Port;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.NodeImpl;
import com.aoindustries.noc.monitor.RootNodeImpl;
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
 * The node per Bind.
 *
 * TODO: Add output of netstat -ln / ss -lnt here to detect extra ports.
 *
 * @author  AO Industries, Inc.
 */
public class BindsNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	final IpAddressNode ipAddressNode;
	private final List<BindNode> netBindNodes = new ArrayList<>();
	private boolean started;

	BindsNode(IpAddressNode ipAddressNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);

		this.ipAddressNode = ipAddressNode;
	}

	@Override
	public IpAddressNode getParent() {
		return ipAddressNode;
	}

	@Override
	public boolean getAllowsChildren() {
		return true;
	}

	@Override
	public List<BindNode> getChildren() {
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
			conn.getWeb_jboss().getSite().addTableListener(tableListener, 100);
			conn.getWeb_tomcat().getSharedTomcat().addTableListener(tableListener, 100);
			conn.getWeb().getSite().addTableListener(tableListener, 100);
			conn.getWeb_tomcat().getSite().addTableListener(tableListener, 100);
			conn.getWeb_tomcat().getPrivateTomcatSite().addTableListener(tableListener, 100);
			conn.getWeb_tomcat().getWorker().addTableListener(tableListener, 100);
			conn.getNet().getIpAddress().addTableListener(tableListener, 100);
			conn.getNet().getBind().addTableListener(tableListener, 100);
			conn.getNet().getDevice().addTableListener(tableListener, 100);
		}
		verifyNetBinds();
	}

	void stop() {
		RootNodeImpl rootNode = ipAddressNode.ipAddressesNode.rootNode;
		AOServConnector conn = rootNode.conn;
		synchronized(netBindNodes) {
			started = false;
			conn.getWeb_jboss().getSite().removeTableListener(tableListener);
			conn.getWeb_tomcat().getSharedTomcat().removeTableListener(tableListener);
			conn.getWeb().getSite().removeTableListener(tableListener);
			conn.getWeb_tomcat().getSite().removeTableListener(tableListener);
			conn.getWeb_tomcat().getPrivateTomcatSite().removeTableListener(tableListener);
			conn.getWeb_tomcat().getWorker().removeTableListener(tableListener);
			conn.getNet().getIpAddress().removeTableListener(tableListener);
			conn.getNet().getBind().removeTableListener(tableListener);
			conn.getNet().getDevice().removeTableListener(tableListener);
			for(BindNode netBindNode : netBindNodes) {
				netBindNode.stop();
				rootNode.nodeRemoved();
			}
			netBindNodes.clear();
		}
	}

	static class NetMonitorSetting implements Comparable<NetMonitorSetting> {

		private final Host server;
		private final Bind netBind;
		private final InetAddress ipAddress;
		private final Port port;

		private NetMonitorSetting(Host server, Bind netBind, InetAddress ipAddress, Port port) {
			this.server = server;
			this.netBind = netBind;
			this.ipAddress = ipAddress;
			this.port = port;
		}

		@Override
		public int compareTo(NetMonitorSetting o) {
			// Host
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
		 * Gets the Host for this port.
		 */
		Host getServer() {
			return server;
		}

		Bind getNetBind() {
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
	static List<NetMonitorSetting> getSettings(IpAddress ipAddress) throws IOException, SQLException {
		Device device = ipAddress.getDevice();
		if(device == null) return Collections.emptyList();
		List<Bind> directNetBinds = ipAddress.getNetBinds();

		// Find the wildcard IP address, if available
		Host server = device.getServer();
		IpAddress wildcard = null;
		for(IpAddress ia : server.getIPAddresses()) {
			if(ia.getInetAddress().isUnspecified()) {
				wildcard = ia;
				break;
			}
		}
		List<Bind> wildcardNetBinds;
		if(wildcard==null) wildcardNetBinds = Collections.emptyList();
		else wildcardNetBinds = server.getNetBinds(wildcard);

		InetAddress inetaddress = ipAddress.getInetAddress();
		List<NetMonitorSetting> netMonitorSettings = new ArrayList<>(directNetBinds.size() + wildcardNetBinds.size());
		for(Bind netBind : directNetBinds) {
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
		for(Bind netBind : wildcardNetBinds) {
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

		IpAddress ipAddress = ipAddressNode.getIpAddress();
		ipAddress = ipAddress.getTable().getConnector().getNet().getIpAddress().get(ipAddress.getPkey());
		List<NetMonitorSetting> netMonitorSettings = getSettings(ipAddress);

		synchronized(netBindNodes) {
			if(started) {
				// Remove old ones
				Iterator<BindNode> netBindNodeIter = netBindNodes.iterator();
				while(netBindNodeIter.hasNext()) {
					BindNode netBindNode = netBindNodeIter.next();
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
						BindNode netBindNode = new BindNode(this, netMonitorSetting, port, csf, ssf);
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
