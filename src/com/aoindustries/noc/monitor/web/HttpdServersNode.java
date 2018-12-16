/*
 * Copyright 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.web;

import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.aoserv.client.web.HttpdServer;
import com.aoindustries.lang.ObjectUtils;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.NodeImpl;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.net.HostNode;
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
 * The node for all {@link HttpdServer} on one {@link Server}.
 *
 * @author  AO Industries, Inc.
 */
public class HttpdServersNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	private static final boolean DEBUG = false;

	final HostNode serverNode;
	private final Server aoServer;
	private final List<HttpdServerNode> httpdServerNodes = new ArrayList<>();
	private boolean started;

	public HttpdServersNode(HostNode serverNode, Server aoServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.serverNode = serverNode;
		this.aoServer = aoServer;
	}

	@Override
	public HostNode getParent() {
		return serverNode;
	}

	public Server getAOServer() {
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
		return accessor.getMessage(serverNode.hostsNode.rootNode.locale, "HttpdServersNode.label");
	}

	private final TableListener tableListener = (Table<?> table) -> {
		try {
			verifyHttpdServers();
		} catch(IOException | SQLException err) {
			throw new WrappedException(err);
		}
	};

	public void start() throws IOException, SQLException {
		synchronized(httpdServerNodes) {
			if(started) throw new IllegalStateException();
			started = true;
			serverNode.hostsNode.rootNode.conn.getWeb().getHttpdServer().addTableListener(tableListener, 100);
		}
		verifyHttpdServers();
	}

	public void stop() {
		synchronized(httpdServerNodes) {
			started = false;
			serverNode.hostsNode.rootNode.conn.getWeb().getHttpdServer().removeTableListener(tableListener);
			for(HttpdServerNode httpdServerNode : httpdServerNodes) {
				httpdServerNode.stop();
				serverNode.hostsNode.rootNode.nodeRemoved();
			}
			httpdServerNodes.clear();
		}
	}

	@SuppressWarnings("deprecation") // Java 1.7: Do not suppress
	private void verifyHttpdServers() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(httpdServerNodes) {
			if(!started) return;
		}

		List<HttpdServer> httpdServers = aoServer.getHttpdServers();
		if(DEBUG) System.err.println("httpdServers = " + httpdServers);
		synchronized(httpdServerNodes) {
			if(started) {
				// Remove old ones
				Iterator<HttpdServerNode> httpdServerNodeIter = httpdServerNodes.iterator();
				while(httpdServerNodeIter.hasNext()) {
					HttpdServerNode httpdServerNode = httpdServerNodeIter.next();
					HttpdServer existingHttpdServer = httpdServerNode.getHttpdServer();
					// Look for existing node with matching HttpdServer with the same name
					boolean hasMatch = false;
					for(HttpdServer httpdServer : httpdServers) {
						if(httpdServer.equals(existingHttpdServer)) {
							if(ObjectUtils.equals(httpdServer.getName(), existingHttpdServer.getName())) {
								if(DEBUG) System.err.println("Found with matching name " + existingHttpdServer.getName() + ", keeping node");
								hasMatch = true;
							} else {
								if(DEBUG) System.err.println("Name changed from " + existingHttpdServer.getName() + " to " + httpdServer.getName() + ", removing node");
								// Name changed, remove old node
								// matches remains false
							}
							break;
						}
					}
					if(!hasMatch) {
						httpdServerNode.stop();
						httpdServerNodeIter.remove();
						serverNode.hostsNode.rootNode.nodeRemoved();
					}
				}
				// Add new ones
				for(int c = 0; c < httpdServers.size(); c++) {
					HttpdServer httpdServer = httpdServers.get(c);
					if(c >= httpdServerNodes.size() || !httpdServer.equals(httpdServerNodes.get(c).getHttpdServer())) {
						if(DEBUG) System.err.println("Adding node for " + httpdServer.getName());
						try {
							// Insert into proper index
							if(DEBUG) System.err.println("Creating node for " + httpdServer.getName());
							HttpdServerNode httpdServerNode = new HttpdServerNode(this, httpdServer, port, csf, ssf);
							if(DEBUG) System.err.println("Adding node to list for " + httpdServer.getName());
							httpdServerNodes.add(c, httpdServerNode);
							if(DEBUG) System.err.println("Starting node for " + httpdServer.getName());
							httpdServerNode.start();
							if(DEBUG) System.err.println("Notifying added for " + httpdServer.getName());
							serverNode.hostsNode.rootNode.nodeAdded();
						} catch(IOException | RuntimeException e) {
							if(DEBUG) e.printStackTrace(System.err);
							throw e;
						}
					}
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
						serverNode.hostsNode.rootNode.locale,
						"error.mkdirFailed",
						dir.getCanonicalPath()
					)
				);
			}
		}
		return dir;
	}
}
