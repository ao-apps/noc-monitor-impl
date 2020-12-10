/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2009, 2014, 2016, 2017, 2018, 2019, 2020  AO Industries, Inc.
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

import com.aoindustries.aoserv.client.mysql.Server;
import com.aoindustries.exception.WrappedException;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import com.aoindustries.noc.monitor.NodeImpl;
import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;
import com.aoindustries.noc.monitor.RootNodeImpl;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.table.Table;
import com.aoindustries.table.TableListener;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * The node per MySQL server.
 *
 * @author  AO Industries, Inc.
 */
public class ServerNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	final ServersNode _mysqlServersNode;
	private final Server _mysqlServer;
	private final Server.Name _label;

	private boolean started;
	volatile private SlavesNode _mysqlSlavesNode;
	volatile private DatabasesNode _mysqlDatabasesNode;

	ServerNode(ServersNode mysqlServersNode, Server mysqlServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException, SQLException, IOException {
		super(port, csf, ssf);
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		this._mysqlServersNode = mysqlServersNode;
		this._mysqlServer = mysqlServer;
		this._label = mysqlServer.getName();
	}

	@Override
	public ServersNode getParent() {
		return _mysqlServersNode;
	}

	public Server getMySQLServer() {
		return _mysqlServer;
	}

	@Override
	public boolean getAllowsChildren() {
		return true;
	}

	@Override
	public List<NodeImpl> getChildren() {
		return getSnapshot(
			this._mysqlSlavesNode,
			this._mysqlDatabasesNode
		);
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	public AlertLevel getAlertLevel() {
		return constrainAlertLevel(
			AlertLevelUtils.getMaxAlertLevel(
				this._mysqlSlavesNode,
				this._mysqlDatabasesNode
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
		return _label.toString();
	}

	private final TableListener tableListener = (Table<?> table) -> {
		try {
			verifyFailoverMySQLReplications();
		} catch(IOException | SQLException err) {
			throw new WrappedException(err);
		}
	};

	void start() throws IOException, SQLException {
		RootNodeImpl rootNode = _mysqlServersNode.hostNode.hostsNode.rootNode;
		synchronized(this) {
			if(started) throw new IllegalStateException();
			started = true;
			rootNode.conn.getBackup().getMysqlReplication().addTableListener(tableListener, 100);
			rootNode.conn.getNet().getHost().addTableListener(tableListener, 100);
		}
		verifyFailoverMySQLReplications();
		synchronized(this) {
			if(started) {
				if(_mysqlDatabasesNode==null) {
					_mysqlDatabasesNode = new DatabasesNode(this, port, csf, ssf);
					_mysqlDatabasesNode.start();
					rootNode.nodeAdded();
				}
			}
		}
	}

	void stop() {
		RootNodeImpl rootNode = _mysqlServersNode.hostNode.hostsNode.rootNode;
		synchronized(this) {
			started = false;
			// TODO: Review for other missing removeTableListener
			rootNode.conn.getBackup().getMysqlReplication().removeTableListener(tableListener);
			rootNode.conn.getNet().getHost().removeTableListener(tableListener);
			if(_mysqlSlavesNode!=null) {
				_mysqlSlavesNode.stop();
				_mysqlSlavesNode = null;
				rootNode.nodeRemoved();
			}

			if(_mysqlDatabasesNode!=null) {
				_mysqlDatabasesNode.stop();
				_mysqlDatabasesNode = null;
				rootNode.nodeRemoved();
			}
		}
	}

	private void verifyFailoverMySQLReplications() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(this) {
			if(!started) return;
		}

		boolean hasMysqlReplication = _mysqlServer.getFailoverMySQLReplications().stream()
			.anyMatch(mysqlReplication -> WrappedException.call(mysqlReplication::isMonitoringEnabled));
		synchronized(this) {
			if(started) {
				if(hasMysqlReplication) {
					if(_mysqlSlavesNode==null) {
						_mysqlSlavesNode = new SlavesNode(this, port, csf, ssf);
						_mysqlSlavesNode.start();
						_mysqlServersNode.hostNode.hostsNode.rootNode.nodeAdded();
					}
				} else {
					if(_mysqlSlavesNode!=null) {
						_mysqlSlavesNode.stop();
						_mysqlSlavesNode = null;
						_mysqlServersNode.hostNode.hostsNode.rootNode.nodeRemoved();
					}
				}
			}
		}
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File(_mysqlServersNode.getPersistenceDirectory(), _label.toString());
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				throw new IOException(
					PACKAGE_RESOURCES.getMessage(
						_mysqlServersNode.hostNode.hostsNode.rootNode.locale,
						"error.mkdirFailed",
						dir.getCanonicalPath()
					)
				);
			}
		}
		return dir;
	}
}
