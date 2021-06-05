/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2009-2013, 2014, 2016, 2018, 2019, 2020, 2021  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of noc-monitor-impl.
 *
 * noc-monitor-impl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * noc-monitor-impl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with noc-monitor-impl.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.noc.monitor.mysql;

import com.aoapps.hodgepodge.table.Table;
import com.aoapps.hodgepodge.table.TableListener;
import com.aoapps.lang.exception.WrappedException;
import com.aoindustries.aoserv.client.mysql.Database;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import com.aoindustries.noc.monitor.NodeImpl;
import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;
import com.aoindustries.noc.monitor.common.AlertLevel;
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
 * The node for all MySQLDatabases on one Server.
 *
 * @author  AO Industries, Inc.
 */
public class DatabasesNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	final ServerNode mysqlServerNode;
	final SlaveNode mysqlSlaveNode;
	private final List<DatabaseNode> mysqlDatabaseNodes = new ArrayList<>();
	private boolean started;

	DatabasesNode(ServerNode mysqlServerNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.mysqlServerNode = mysqlServerNode;
		this.mysqlSlaveNode = null;
	}

	DatabasesNode(SlaveNode mysqlSlaveNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
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
	public List<DatabaseNode> getChildren() {
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
		return PACKAGE_RESOURCES.getMessage(mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode.locale, "MySQLDatabasesNode.label");
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
			mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode.conn.getMysql().getDatabase().addTableListener(tableListener, 100);
		}
		verifyMySQLDatabases();
	}

	void stop() {
		synchronized(mysqlDatabaseNodes) {
			started = false;
			mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode.conn.getMysql().getDatabase().removeTableListener(tableListener);
			for(DatabaseNode mysqlDatabaseNode : mysqlDatabaseNodes) {
				mysqlDatabaseNode.stop();
				mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode.nodeRemoved();
			}
			mysqlDatabaseNodes.clear();
		}
	}

	private void verifyMySQLDatabases() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(mysqlDatabaseNodes) {
			if(!started) return;
		}

		List<Database> mysqlDatabases = mysqlServerNode.getMySQLServer().getMySQLDatabases();
		synchronized(mysqlDatabaseNodes) {
			if(started) {
				// Remove old ones
				Iterator<DatabaseNode> mysqlDatabaseNodeIter = mysqlDatabaseNodes.iterator();
				while(mysqlDatabaseNodeIter.hasNext()) {
					DatabaseNode mysqlDatabaseNode = mysqlDatabaseNodeIter.next();
					Database mysqlDatabase = mysqlDatabaseNode.getMySQLDatabase();
					if(!mysqlDatabases.contains(mysqlDatabase)) {
						mysqlDatabaseNode.stop();
						mysqlDatabaseNodeIter.remove();
						mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode.nodeRemoved();
					}
				}
				// Add new ones
				for(int c=0;c<mysqlDatabases.size();c++) {
					Database mysqlDatabase = mysqlDatabases.get(c);
					if(c>=mysqlDatabaseNodes.size() || !mysqlDatabase.equals(mysqlDatabaseNodes.get(c).getMySQLDatabase())) {
						// Insert into proper index
						DatabaseNode mysqlDatabaseNode = new DatabaseNode(this, mysqlDatabase, mysqlSlaveNode!=null ? mysqlSlaveNode.getFailoverMySQLReplication() : null, port, csf, ssf);
						mysqlDatabaseNodes.add(c, mysqlDatabaseNode);
						mysqlDatabaseNode.start();
						mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode.nodeAdded();
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
					PACKAGE_RESOURCES.getMessage(
						mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode.locale,
						"error.mkdirFailed",
						dir.getCanonicalPath()
					)
				);
			}
		}
		return dir;
	}
}
