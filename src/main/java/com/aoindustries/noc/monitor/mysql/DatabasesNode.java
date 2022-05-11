/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2009-2013, 2014, 2016, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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
 * along with noc-monitor-impl.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoindustries.noc.monitor.mysql;

import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;

import com.aoapps.hodgepodge.table.Table;
import com.aoapps.hodgepodge.table.TableListener;
import com.aoapps.lang.exception.WrappedException;
import com.aoindustries.aoserv.client.mysql.Database;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import com.aoindustries.noc.monitor.NodeImpl;
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
 * The node for all MysqlDatabases on one Server.
 *
 * @author  AO Industries, Inc.
 */
public class DatabasesNode extends NodeImpl {

  private static final long serialVersionUID = 1L;

  final ServerNode serverNode;
  final SlaveNode slaveNode;
  private final List<DatabaseNode> databaseNodes = new ArrayList<>();
  private boolean started;

  DatabasesNode(ServerNode serverNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
    super(port, csf, ssf);
    this.serverNode = serverNode;
    this.slaveNode = null;
  }

  DatabasesNode(SlaveNode slaveNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
    super(port, csf, ssf);
    this.serverNode = slaveNode.slavesNode.serverNode;
    this.slaveNode = slaveNode;
  }

  @Override
  public NodeImpl getParent() {
    return slaveNode != null ? slaveNode : serverNode;
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public List<DatabaseNode> getChildren() {
    synchronized (databaseNodes) {
      return getSnapshot(databaseNodes);
    }
  }

  /**
   * The alert level is equal to the highest alert level of its children.
   */
  @Override
  public AlertLevel getAlertLevel() {
    AlertLevel level;
    synchronized (databaseNodes) {
      level = AlertLevelUtils.getMaxAlertLevel(
          databaseNodes
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
    return PACKAGE_RESOURCES.getMessage(serverNode.serversNode.hostNode.hostsNode.rootNode.locale, "MysqlDatabasesNode.label");
  }

  private final TableListener tableListener = (Table<?> table) -> {
    try {
      verifyMysqlDatabases();
    } catch (IOException | SQLException err) {
      throw new WrappedException(err);
    }
  };

  void start() throws IOException, SQLException {
    synchronized (databaseNodes) {
      if (started) {
        throw new IllegalStateException();
      }
      started = true;
      serverNode.serversNode.hostNode.hostsNode.rootNode.conn.getMysql().getDatabase().addTableListener(tableListener, 100);
    }
    verifyMysqlDatabases();
  }

  void stop() {
    synchronized (databaseNodes) {
      started = false;
      serverNode.serversNode.hostNode.hostsNode.rootNode.conn.getMysql().getDatabase().removeTableListener(tableListener);
      for (DatabaseNode mysqlDatabaseNode : databaseNodes) {
        mysqlDatabaseNode.stop();
        serverNode.serversNode.hostNode.hostsNode.rootNode.nodeRemoved();
      }
      databaseNodes.clear();
    }
  }

  private void verifyMysqlDatabases() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (databaseNodes) {
      if (!started) {
        return;
      }
    }

    List<Database> databases = serverNode.getServer().getMysqlDatabases();
    synchronized (databaseNodes) {
      if (started) {
        // Remove old ones
        Iterator<DatabaseNode> databaseNodeIter = databaseNodes.iterator();
        while (databaseNodeIter.hasNext()) {
          DatabaseNode databaseNode = databaseNodeIter.next();
          Database database = databaseNode.getDatabase();
          if (!databases.contains(database)) {
            databaseNode.stop();
            databaseNodeIter.remove();
            serverNode.serversNode.hostNode.hostsNode.rootNode.nodeRemoved();
          }
        }
        // Add new ones
        for (int c = 0; c < databases.size(); c++) {
          Database database = databases.get(c);
          if (c >= databaseNodes.size() || !database.equals(databaseNodes.get(c).getDatabase())) {
            // Insert into proper index
            DatabaseNode databaseNode = new DatabaseNode(this, database, slaveNode != null ? slaveNode.getMysqlReplication() : null, port, csf, ssf);
            databaseNodes.add(c, databaseNode);
            databaseNode.start();
            serverNode.serversNode.hostNode.hostsNode.rootNode.nodeAdded();
          }
        }
      }
    }
  }

  File getPersistenceDirectory() throws IOException {
    File dir = new File((slaveNode != null ? slaveNode.getPersistenceDirectory() : serverNode.getPersistenceDirectory()), "mysql_databases");
    if (!dir.exists()) {
      if (!dir.mkdir()) {
        throw new IOException(
            PACKAGE_RESOURCES.getMessage(
                serverNode.serversNode.hostNode.hostsNode.rootNode.locale,
                "error.mkdirFailed",
                dir.getCanonicalPath()
            )
        );
      }
    }
    return dir;
  }
}
