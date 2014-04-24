/*
 * Copyright 2008-2009, 2014 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.Server;
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
 * The top-level node has one child for each of the servers.
 *
 * @author  AO Industries, Inc.
 */
abstract public class ServersNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	final RootNodeImpl rootNode;

	private final List<ServerNode> serverNodes = new ArrayList<>();
	private boolean stopped = false;

	ServersNode(RootNodeImpl rootNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.rootNode = rootNode;
	}

	@Override
	final public RootNodeImpl getParent() {
		return rootNode;
	}

	@Override
	final public boolean getAllowsChildren() {
		return true;
	}

	/**
	 * For thread safety and encapsulation, returns an unmodifiable copy of the array.
	 */
	@Override
	final public List<ServerNode> getChildren() {
		synchronized(serverNodes) {
			return getSnapshot(serverNodes);
		}
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	final public AlertLevel getAlertLevel() {
		AlertLevel level;
		synchronized(serverNodes) {
			level = AlertLevelUtils.getMaxAlertLevel(serverNodes);
		}
		return constrainAlertLevel(level);
	}

	/**
	 * No alert messages.
	 */
	@Override
	final public String getAlertMessage() {
		return null;
	}

	private final TableListener tableListener = new TableListener() {
		@Override
		public void tableUpdated(Table<?> table) {
			try {
				verifyServers();
			} catch(IOException | SQLException err) {
				throw new WrappedException(err);
			}
		}
	};

	final void start() throws IOException, SQLException {
		synchronized(serverNodes) {
			stopped = false;
		}
		rootNode.conn.getServers().addTableListener(tableListener, 100);
		verifyServers();
	}

	final void stop() {
		rootNode.conn.getServers().removeTableListener(tableListener);
		synchronized(serverNodes) {
			stopped = true;
			for(ServerNode serverNode : serverNodes) {
				serverNode.stop();
				rootNode.nodeRemoved();
			}
			serverNodes.clear();
		}
	}

	private void verifyServers() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		// Get all the servers that have monitoring enabled
		List<Server> allServers = rootNode.conn.getServers().getRows();
		List<Server> servers = new ArrayList<>(allServers.size());
		for(Server server : allServers) {
			if(server.isMonitoringEnabled() && includeServer(server)) servers.add(server);
		}
		synchronized(serverNodes) {
			if(!stopped) {
				// Remove old ones
				Iterator<ServerNode> serverNodeIter = serverNodes.iterator();
				while(serverNodeIter.hasNext()) {
					ServerNode serverNode = serverNodeIter.next();
					Server server = serverNode.getServer();
					if(!servers.contains(server)) {
						serverNode.stop();
						serverNodeIter.remove();
						rootNode.nodeRemoved();
					}
				}
				// Add new ones
				for(int c=0;c<servers.size();c++) {
					Server server = servers.get(c);
					if(c>=serverNodes.size() || !server.equals(serverNodes.get(c).getServer())) {
						// Insert into proper index
						ServerNode serverNode = new ServerNode(this, server, port, csf, ssf);
						serverNodes.add(c, serverNode);
						serverNode.start();
						rootNode.nodeAdded();
					}
				}
			}
		}
	}

	/**
	 * Gets the top-level persistence directory.
	 */
	final File getPersistenceDirectory() throws IOException {
		File dir = new File(rootNode.getPersistenceDirectory(), "servers");
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				throw new IOException(
					accessor.getMessage(
						//rootNode.locale,
						"error.mkdirFailed",
						dir.getCanonicalPath()
					)
				);
			}
		}
		return dir;
	}

	abstract boolean includeServer(Server server) throws SQLException, IOException;
}
