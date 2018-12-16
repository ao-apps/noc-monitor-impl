/*
 * Copyright 2009, 2014, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.linux.Server;
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
 * The node for all MySQLServers on one Server.
 *
 * @author  AO Industries, Inc.
 */
public class MySQLServersNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	final ServerNode serverNode;
	private final Server aoServer;
	private final List<MySQLServerNode> mysqlServerNodes = new ArrayList<>();
	private boolean started;

	MySQLServersNode(ServerNode serverNode, Server aoServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.serverNode = serverNode;
		this.aoServer = aoServer;
	}

	@Override
	public ServerNode getParent() {
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
	public List<MySQLServerNode> getChildren() {
		synchronized(mysqlServerNodes) {
			return getSnapshot(mysqlServerNodes);
		}
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	public AlertLevel getAlertLevel() {
		AlertLevel level;
		synchronized(mysqlServerNodes) {
			level = AlertLevelUtils.getMaxAlertLevel(mysqlServerNodes);
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
		return accessor.getMessage(serverNode.serversNode.rootNode.locale, "MySQLServersNode.label");
	}

	private final TableListener tableListener = (Table<?> table) -> {
		try {
			verifyMySQLServers();
		} catch(IOException | SQLException err) {
			throw new WrappedException(err);
		}
	};

	void start() throws IOException, SQLException {
		synchronized(mysqlServerNodes) {
			if(started) throw new IllegalStateException();
			started = true;
			serverNode.serversNode.rootNode.conn.getMysql().getMysqlServers().addTableListener(tableListener, 100);
		}
		verifyMySQLServers();
	}

	void stop() {
		synchronized(mysqlServerNodes) {
			started = false;
			serverNode.serversNode.rootNode.conn.getMysql().getMysqlServers().removeTableListener(tableListener);
			for(MySQLServerNode mysqlServerNode : mysqlServerNodes) {
				mysqlServerNode.stop();
				serverNode.serversNode.rootNode.nodeRemoved();
			}
			mysqlServerNodes.clear();
		}
	}

	private void verifyMySQLServers() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(mysqlServerNodes) {
			if(!started) return;
		}

		List<com.aoindustries.aoserv.client.mysql.Server> mysqlServers = aoServer.getMySQLServers();
		synchronized(mysqlServerNodes) {
			if(started) {
				// Remove old ones
				Iterator<MySQLServerNode> mysqlServerNodeIter = mysqlServerNodes.iterator();
				while(mysqlServerNodeIter.hasNext()) {
					MySQLServerNode mysqlServerNode = mysqlServerNodeIter.next();
					com.aoindustries.aoserv.client.mysql.Server mysqlServer = mysqlServerNode.getMySQLServer();
					if(!mysqlServers.contains(mysqlServer)) {
						mysqlServerNode.stop();
						mysqlServerNodeIter.remove();
						serverNode.serversNode.rootNode.nodeRemoved();
					}
				}
				// Add new ones
				for(int c=0;c<mysqlServers.size();c++) {
					com.aoindustries.aoserv.client.mysql.Server mysqlServer = mysqlServers.get(c);
					if(c>=mysqlServerNodes.size() || !mysqlServer.equals(mysqlServerNodes.get(c).getMySQLServer())) {
						// Insert into proper index
						MySQLServerNode mysqlServerNode = new MySQLServerNode(this, mysqlServer, port, csf, ssf);
						mysqlServerNodes.add(c, mysqlServerNode);
						mysqlServerNode.start();
						serverNode.serversNode.rootNode.nodeAdded();
					}
				}
			}
		}
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File(serverNode.getPersistenceDirectory(), "mysql_servers");
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
