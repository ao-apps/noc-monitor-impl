/*
 * Copyright 2008-2009, 2014, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.NetDevice;
import com.aoindustries.aoserv.client.Server;
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
 * The node per server.
 *
 * @author  AO Industries, Inc.
 */
public class NetDevicesNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	final ServerNode serverNode;
	private final Server server;
	private final List<NetDeviceNode> netDeviceNodes = new ArrayList<>();

	NetDevicesNode(ServerNode serverNode, Server server, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.serverNode = serverNode;
		this.server = server;
	}

	@Override
	public ServerNode getParent() {
		return serverNode;
	}

	public Server getServer() {
		return server;
	}

	@Override
	public boolean getAllowsChildren() {
		return true;
	}

	@Override
	public List<NetDeviceNode> getChildren() {
		synchronized(netDeviceNodes) {
			return getSnapshot(netDeviceNodes);
		}
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	public AlertLevel getAlertLevel() {
		AlertLevel level;
		synchronized(netDeviceNodes) {
			level = AlertLevelUtils.getMaxAlertLevel(netDeviceNodes);
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
		return accessor.getMessage(serverNode.serversNode.rootNode.locale, "NetDevicesNode.label");
	}

	private final TableListener tableListener = (Table<?> table) -> {
		try {
			verifyNetDevices();
		} catch(IOException | SQLException err) {
			throw new WrappedException(err);
		}
	};

	void start() throws IOException, SQLException {
		synchronized(netDeviceNodes) {
			serverNode.serversNode.rootNode.conn.getNetDevices().addTableListener(tableListener, 100);
			verifyNetDevices();
		}
	}

	void stop() {
		synchronized(netDeviceNodes) {
			serverNode.serversNode.rootNode.conn.getNetDevices().removeTableListener(tableListener);
			for(NetDeviceNode netDeviceNode : netDeviceNodes) {
				netDeviceNode.stop();
				serverNode.serversNode.rootNode.nodeRemoved();
			}
			netDeviceNodes.clear();
		}
	}

	private void verifyNetDevices() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		// Filter only those that are enabled
		List<NetDevice> netDevices;
		{
			List<NetDevice> allNetDevices = server.getNetDevices();
			netDevices = new ArrayList<>(allNetDevices.size());
			for(NetDevice netDevice : allNetDevices) {
				if(netDevice.isMonitoringEnabled()) netDevices.add(netDevice);
			}
		}
		synchronized(netDeviceNodes) {
			// Remove old ones
			Iterator<NetDeviceNode> netDeviceNodeIter = netDeviceNodes.iterator();
			while(netDeviceNodeIter.hasNext()) {
				NetDeviceNode netDeviceNode = netDeviceNodeIter.next();
				NetDevice netDevice = netDeviceNode.getNetDevice();
				if(!netDevices.contains(netDevice)) {
					netDeviceNode.stop();
					netDeviceNodeIter.remove();
					serverNode.serversNode.rootNode.nodeRemoved();
				}
			}
			// Add new ones
			for(int c=0;c<netDevices.size();c++) {
				NetDevice netDevice = netDevices.get(c);
				if(c>=netDeviceNodes.size() || !netDevice.equals(netDeviceNodes.get(c).getNetDevice())) {
					// Insert into proper index
					NetDeviceNode netDeviceNode = new NetDeviceNode(this, netDevice, port, csf, ssf);
					netDeviceNodes.add(c, netDeviceNode);
					netDeviceNode.start();
					serverNode.serversNode.rootNode.nodeAdded();
				}
			}
		}
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File(serverNode.getPersistenceDirectory(), "net_devices");
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				throw new IOException(
					accessor.getMessage(
						serverNode.serversNode.rootNode.locale,
						"error.mkdirFailed",
						dir.getCanonicalPath()
					)
				);
			}
		}
		return dir;
	}
}
