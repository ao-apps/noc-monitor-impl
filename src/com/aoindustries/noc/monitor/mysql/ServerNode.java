/*
 * Copyright 2009, 2014, 2016, 2017, 2018, 2019 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.mysql;

import com.aoindustries.aoserv.client.backup.MysqlReplication;
import com.aoindustries.aoserv.client.mysql.Server;
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
		synchronized(this) {
			started = false;
			RootNodeImpl rootNode = _mysqlServersNode.hostNode.hostsNode.rootNode;
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

		List<MysqlReplication> failoverMySQLReplications = _mysqlServer.getFailoverMySQLReplications();
		synchronized(this) {
			if(started) {
				if(!failoverMySQLReplications.isEmpty()) {
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
					accessor.getMessage(
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
