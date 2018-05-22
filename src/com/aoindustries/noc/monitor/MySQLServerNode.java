/*
 * Copyright 2009, 2014, 2016, 2017, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.FailoverMySQLReplication;
import com.aoindustries.aoserv.client.MySQLServer;
import com.aoindustries.aoserv.client.validator.MySQLServerName;
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
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * The node per MySQL server.
 *
 * @author  AO Industries, Inc.
 */
public class MySQLServerNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	final MySQLServersNode _mysqlServersNode;
	private final MySQLServer _mysqlServer;
	private final MySQLServerName _label;

	volatile private MySQLSlavesNode _mysqlSlavesNode;
	volatile private MySQLDatabasesNode _mysqlDatabasesNode;

	MySQLServerNode(MySQLServersNode mysqlServersNode, MySQLServer mysqlServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException, SQLException, IOException {
		super(port, csf, ssf);
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		this._mysqlServersNode = mysqlServersNode;
		this._mysqlServer = mysqlServer;
		this._label = mysqlServer.getName();
	}

	@Override
	public MySQLServersNode getParent() {
		return _mysqlServersNode;
	}

	public MySQLServer getMySQLServer() {
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

	synchronized void start() throws IOException, SQLException {
		RootNodeImpl rootNode = _mysqlServersNode.serverNode.serversNode.rootNode;
		rootNode.conn.getFailoverMySQLReplications().addTableListener(tableListener, 100);
		verifyFailoverMySQLReplications();
		if(_mysqlDatabasesNode==null) {
			_mysqlDatabasesNode = new MySQLDatabasesNode(this, port, csf, ssf);
			_mysqlDatabasesNode.start();
			rootNode.nodeAdded();
		}
	}

	synchronized void stop() {
		RootNodeImpl rootNode = _mysqlServersNode.serverNode.serversNode.rootNode;
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

	synchronized private void verifyFailoverMySQLReplications() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		List<FailoverMySQLReplication> failoverMySQLReplications = _mysqlServer.getFailoverMySQLReplications();
		if(!failoverMySQLReplications.isEmpty()) {
			if(_mysqlSlavesNode==null) {
				_mysqlSlavesNode = new MySQLSlavesNode(this, port, csf, ssf);
				_mysqlSlavesNode.start();
				_mysqlServersNode.serverNode.serversNode.rootNode.nodeAdded();
			}
		} else {
			if(_mysqlSlavesNode!=null) {
				_mysqlSlavesNode.stop();
				_mysqlSlavesNode = null;
				_mysqlServersNode.serverNode.serversNode.rootNode.nodeRemoved();
			}
		}
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File(_mysqlServersNode.getPersistenceDirectory(), _label.toString());
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				throw new IOException(
					accessor.getMessage(
						_mysqlServersNode.serverNode.serversNode.rootNode.locale,
						"error.mkdirFailed",
						dir.getCanonicalPath()
					)
				);
			}
		}
		return dir;
	}
}
