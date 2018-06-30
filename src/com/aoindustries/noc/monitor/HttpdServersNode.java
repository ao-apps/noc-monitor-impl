/*
 * Copyright 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.aoserv.client.HttpdServer;
import com.aoindustries.lang.ObjectUtils;
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
 * The node for all {@link HttpdServer} on one {@link AOServer}.
 *
 * @author  AO Industries, Inc.
 */
public class HttpdServersNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	final ServerNode serverNode;
	private final AOServer aoServer;
	private final List<HttpdServerNode> httpdServerNodes = new ArrayList<>();

	HttpdServersNode(ServerNode serverNode, AOServer aoServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.serverNode = serverNode;
		this.aoServer = aoServer;
	}

	@Override
	public ServerNode getParent() {
		return serverNode;
	}

	public AOServer getAOServer() {
		return aoServer;
	}

	@Override
	public boolean getAllowsChildren() {
		return true;
	}

	@Override
	public List<HttpdServerNode> getChildren() {
		synchronized(httpdServerNodes) {
			return getSnapshot(httpdServerNodes);
		}
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	public AlertLevel getAlertLevel() {
		AlertLevel level;
		synchronized(httpdServerNodes) {
			level = AlertLevelUtils.getMaxAlertLevel(httpdServerNodes);
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
		return accessor.getMessage(serverNode.serversNode.rootNode.locale, "HttpdServersNode.label");
	}

	private final TableListener tableListener = (Table<?> table) -> {
		try {
			verifyHttpdServers();
		} catch(IOException | SQLException err) {
			throw new WrappedException(err);
		}
	};

	void start() throws IOException, SQLException {
		synchronized(httpdServerNodes) {
			serverNode.serversNode.rootNode.conn.getHttpdServers().addTableListener(tableListener, 100);
			verifyHttpdServers();
		}
	}

	void stop() {
		synchronized(httpdServerNodes) {
			serverNode.serversNode.rootNode.conn.getHttpdServers().removeTableListener(tableListener);
			for(HttpdServerNode httpdServerNode : httpdServerNodes) {
				httpdServerNode.stop();
				serverNode.serversNode.rootNode.nodeRemoved();
			}
			httpdServerNodes.clear();
		}
	}

	@SuppressWarnings("deprecation") // Java 1.7: Do not suppress
	private void verifyHttpdServers() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		List<HttpdServer> httpdServers = aoServer.getHttpdServers();
		synchronized(httpdServerNodes) {
			// Remove old ones
			Iterator<HttpdServerNode> httpdServerNodeIter = httpdServerNodes.iterator();
			while(httpdServerNodeIter.hasNext()) {
				HttpdServerNode httpdServerNode = httpdServerNodeIter.next();
				HttpdServer httpdServer = httpdServerNode.getHttpdServer();
				// Look for existing node with matching HttpdServer with the same name
				boolean hasMatch = false;
				for(HttpdServer hs : httpdServers) {
					if(hs.equals(httpdServer)) {
						if(ObjectUtils.equals(hs.getName(), httpdServer.getName())) {
							hasMatch = true;
						} else {
							// Name changed, remove old node
							// matches remains false
						}
						break;
					}
				}
				if(!hasMatch) {
					httpdServerNode.stop();
					httpdServerNodeIter.remove();
					serverNode.serversNode.rootNode.nodeRemoved();
				}
			}
			// Add new ones
			for(int c = 0; c < httpdServers.size(); c++) {
				HttpdServer httpdServer = httpdServers.get(c);
				if(c >= httpdServerNodes.size() || !httpdServer.equals(httpdServerNodes.get(c).getHttpdServer())) {
					// Insert into proper index
					HttpdServerNode httpdServerNode = new HttpdServerNode(this, httpdServer, port, csf, ssf);
					httpdServerNodes.add(c, httpdServerNode);
					httpdServerNode.start();
					serverNode.serversNode.rootNode.nodeAdded();
				}
			}
		}
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File(serverNode.getPersistenceDirectory(), "httpd_servers");
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
