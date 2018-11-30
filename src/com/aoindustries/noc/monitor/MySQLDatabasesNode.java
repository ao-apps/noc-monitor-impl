/*
 * Copyright 2009-2013, 2014, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.mysql.MySQLDatabase;
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
 * The node for all MySQLDatabases on one MySQLServer.
 *
 * @author  AO Industries, Inc.
 */
public class MySQLDatabasesNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	final MySQLServerNode mysqlServerNode;
	final MySQLSlaveNode mysqlSlaveNode;
	private final List<MySQLDatabaseNode> mysqlDatabaseNodes = new ArrayList<>();
	private boolean started;

	MySQLDatabasesNode(MySQLServerNode mysqlServerNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.mysqlServerNode = mysqlServerNode;
		this.mysqlSlaveNode = null;
	}

	MySQLDatabasesNode(MySQLSlaveNode mysqlSlaveNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.mysqlServerNode = mysqlSlaveNode.mysqlSlavesNode.mysqlServerNode;
		this.mysqlSlaveNode = mysqlSlaveNode;
	}

	@Override
	public NodeImpl getParent() {
		return mysqlSlaveNode!=null ? mysqlSlaveNode : mysqlServerNode;
	}

	@Override
	public boolean getAllowsChildren() {
		return true;
	}

	@Override
	public List<MySQLDatabaseNode> getChildren() {
		synchronized(mysqlDatabaseNodes) {
			return getSnapshot(mysqlDatabaseNodes);
		}
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	public AlertLevel getAlertLevel() {
		AlertLevel level;
		synchronized(mysqlDatabaseNodes) {
			level = AlertLevelUtils.getMaxAlertLevel(
				mysqlDatabaseNodes
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
		return accessor.getMessage(mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.locale, "MySQLDatabasesNode.label");
	}

	private final TableListener tableListener = (Table<?> table) -> {
		try {
			verifyMySQLDatabases();
		} catch(IOException | SQLException err) {
			throw new WrappedException(err);
		}
	};

	void start() throws IOException, SQLException {
		synchronized(mysqlDatabaseNodes) {
			if(started) throw new IllegalStateException();
			started = true;
			mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.conn.getMysqlDatabases().addTableListener(tableListener, 100);
		}
		verifyMySQLDatabases();
	}

	void stop() {
		synchronized(mysqlDatabaseNodes) {
			started = false;
			mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.conn.getMysqlDatabases().removeTableListener(tableListener);
			for(MySQLDatabaseNode mysqlDatabaseNode : mysqlDatabaseNodes) {
				mysqlDatabaseNode.stop();
				mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.nodeRemoved();
			}
			mysqlDatabaseNodes.clear();
		}
	}

	private void verifyMySQLDatabases() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(mysqlDatabaseNodes) {
			if(!started) return;
		}

		List<MySQLDatabase> mysqlDatabases = mysqlServerNode.getMySQLServer().getMySQLDatabases();
		synchronized(mysqlDatabaseNodes) {
			if(started) {
				// Remove old ones
				Iterator<MySQLDatabaseNode> mysqlDatabaseNodeIter = mysqlDatabaseNodes.iterator();
				while(mysqlDatabaseNodeIter.hasNext()) {
					MySQLDatabaseNode mysqlDatabaseNode = mysqlDatabaseNodeIter.next();
					MySQLDatabase mysqlDatabase = mysqlDatabaseNode.getMySQLDatabase();
					if(!mysqlDatabases.contains(mysqlDatabase)) {
						mysqlDatabaseNode.stop();
						mysqlDatabaseNodeIter.remove();
						mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.nodeRemoved();
					}
				}
				// Add new ones
				for(int c=0;c<mysqlDatabases.size();c++) {
					MySQLDatabase mysqlDatabase = mysqlDatabases.get(c);
					if(c>=mysqlDatabaseNodes.size() || !mysqlDatabase.equals(mysqlDatabaseNodes.get(c).getMySQLDatabase())) {
						// Insert into proper index
						MySQLDatabaseNode mysqlDatabaseNode = new MySQLDatabaseNode(this, mysqlDatabase, mysqlSlaveNode!=null ? mysqlSlaveNode.getFailoverMySQLReplication() : null, port, csf, ssf);
						mysqlDatabaseNodes.add(c, mysqlDatabaseNode);
						mysqlDatabaseNode.start();
						mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.nodeAdded();
					}
				}
			}
		}
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File((mysqlSlaveNode!=null ? mysqlSlaveNode.getPersistenceDirectory() : mysqlServerNode.getPersistenceDirectory()), "mysql_databases");
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				throw new IOException(
					accessor.getMessage(
						mysqlServerNode._mysqlServersNode.serverNode.serversNode.rootNode.locale,
						"error.mkdirFailed",
						dir.getCanonicalPath()
					)
				);
			}
		}
		return dir;
	}
}
