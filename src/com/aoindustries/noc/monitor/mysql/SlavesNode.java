/*
 * Copyright 2009, 2014, 2016, 2018, 2019, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.mysql;

import com.aoindustries.aoserv.client.backup.MysqlReplication;
import com.aoindustries.exception.WrappedException;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.NodeImpl;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.table.Table;
import com.aoindustries.table.TableListener;
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
 * The node for all FailoverMySQLReplications on one Server.
 *
 * @author  AO Industries, Inc.
 */
public class SlavesNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	final ServerNode mysqlServerNode;
	private final List<SlaveNode> mysqlSlaveNodes = new ArrayList<>();
	private boolean started;

	SlavesNode(ServerNode mysqlServerNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.mysqlServerNode = mysqlServerNode;
	}

	@Override
	public ServerNode getParent() {
		return mysqlServerNode;
	}

	@Override
	public boolean getAllowsChildren() {
		return true;
	}

	@Override
	public List<SlaveNode> getChildren() {
		synchronized(mysqlSlaveNodes) {
			return getSnapshot(mysqlSlaveNodes);
		}
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	public AlertLevel getAlertLevel() {
		AlertLevel level;
		synchronized(mysqlSlaveNodes) {
			level = AlertLevelUtils.getMaxAlertLevel(mysqlSlaveNodes);
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
		return accessor.getMessage(mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode.locale, "MySQLSlavesNode.label");
	}

	private final TableListener tableListener = (Table<?> table) -> {
		try {
			verifyMySQLSlaves();
		} catch(IOException | SQLException err) {
			throw new WrappedException(err);
		}
	};

	void start() throws IOException, SQLException {
		synchronized(mysqlSlaveNodes) {
			if(started) throw new IllegalStateException();
			started = true;
			mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode.conn.getBackup().getFileReplication().addTableListener(tableListener, 100);
			mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode.conn.getBackup().getMysqlReplication().addTableListener(tableListener, 100);
		}
		verifyMySQLSlaves();
	}

	void stop() {
		synchronized(mysqlSlaveNodes) {
			started = false;
			mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode.conn.getBackup().getFileReplication().removeTableListener(tableListener);
			mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode.conn.getBackup().getMysqlReplication().removeTableListener(tableListener);
			for(SlaveNode mysqlSlaveNode : mysqlSlaveNodes) {
				mysqlSlaveNode.stop();
				mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode.nodeRemoved();
			}
			mysqlSlaveNodes.clear();
		}
	}

	private void verifyMySQLSlaves() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(mysqlSlaveNodes) {
			if(!started) return;
		}

		List<MysqlReplication> mysqlReplications = mysqlServerNode.getMySQLServer().getFailoverMySQLReplications();
		synchronized(mysqlSlaveNodes) {
			if(started) {
				// Remove old ones
				Iterator<SlaveNode> mysqlSlaveNodeIter = mysqlSlaveNodes.iterator();
				while(mysqlSlaveNodeIter.hasNext()) {
					SlaveNode mysqlSlaveNode = mysqlSlaveNodeIter.next();
					MysqlReplication mysqlReplication = mysqlSlaveNode.getFailoverMySQLReplication();
					if(!mysqlReplications.contains(mysqlReplication)) {
						mysqlSlaveNode.stop();
						mysqlSlaveNodeIter.remove();
						mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode.nodeRemoved();
					}
				}
				// Add new ones
				for(int c=0;c<mysqlReplications.size();c++) {
					MysqlReplication mysqlReplication = mysqlReplications.get(c);
					if(c>=mysqlSlaveNodes.size() || !mysqlReplication.equals(mysqlSlaveNodes.get(c).getFailoverMySQLReplication())) {
						// Insert into proper index
						SlaveNode mysqlSlaveNode = new SlaveNode(this, mysqlReplication, port, csf, ssf);
						mysqlSlaveNodes.add(c, mysqlSlaveNode);
						mysqlSlaveNode.start();
						mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode.nodeAdded();
					}
				}
			}
		}
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File(mysqlServerNode.getPersistenceDirectory(), "mysql_slaves");
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				throw new IOException(
					accessor.getMessage(
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
