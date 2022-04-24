/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2009-2013, 2014, 2016, 2017, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoindustries.aoserv.client.backup.MysqlReplication;
import com.aoindustries.aoserv.client.mysql.Database;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;
import com.aoindustries.noc.monitor.TableResultNodeImpl;
import com.aoindustries.noc.monitor.common.AlertLevel;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.List;

/**
 * The node for one Database.
 *
 * @author  AO Industries, Inc.
 */
public class DatabaseNode extends TableResultNodeImpl {

  private static final long serialVersionUID = 1L;

  final DatabaseNodeWorker databaseWorker;
  final DatabasesNode mysqlDatabasesNode;
  final Database mysqlDatabase;
  private final MysqlReplication mysqlSlave;
  private final Database.Name _label;

  private boolean started;

  private volatile CheckTablesNode mysqlCheckTablesNode;

  DatabaseNode(DatabasesNode mysqlDatabasesNode, Database mysqlDatabase, MysqlReplication mysqlSlave, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException, SQLException {
    super(
        mysqlDatabasesNode.mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode,
        mysqlDatabasesNode,
        DatabaseNodeWorker.getWorker(
            new File(mysqlDatabasesNode.getPersistenceDirectory(), mysqlDatabase.getName() + ".show_full_tables"),
            mysqlDatabase,
            mysqlSlave
        ),
        port,
        csf,
        ssf
    );
    this.databaseWorker = (DatabaseNodeWorker) worker;
    this.mysqlDatabasesNode = mysqlDatabasesNode;
    this.mysqlDatabase = mysqlDatabase;
    this.mysqlSlave = mysqlSlave;
    this._label = mysqlDatabase.getName();
  }

  Database getMySQLDatabase() {
    return mysqlDatabase;
  }

  MysqlReplication getMySQLSlave() {
    return mysqlSlave;
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public List<CheckTablesNode> getChildren() {
    return getSnapshot(this.mysqlCheckTablesNode);
  }

  /**
   * The alert level is equal to the highest alert level of its children.
   */
  @Override
  public AlertLevel getAlertLevel() {
    return constrainAlertLevel(
        AlertLevelUtils.getMaxAlertLevel(
            super.getAlertLevel(),
            this.mysqlCheckTablesNode
        )
    );
  }

  @Override
  public String getLabel() {
    return _label.toString();
  }

  File getPersistenceDirectory() throws IOException {
    File dir = new File(mysqlDatabasesNode.getPersistenceDirectory(), _label.toString());
    if (!dir.exists()) {
      if (!dir.mkdir()) {
        throw new IOException(
            PACKAGE_RESOURCES.getMessage(
                mysqlDatabasesNode.mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode.locale,
                "error.mkdirFailed",
                dir.getCanonicalPath()
            )
        );
      }
    }
    return dir;
  }

  @Override
  public void start() throws IOException {
    synchronized (this) {
      if (started) {
        throw new IllegalStateException();
      }
      started = true;
      if (mysqlCheckTablesNode == null) {
        mysqlCheckTablesNode = new CheckTablesNode(this, port, csf, ssf);
        mysqlCheckTablesNode.start();
        mysqlDatabasesNode.mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode.nodeAdded();
      }
      super.start();
    }
  }

  @Override
  public void stop() {
    synchronized (this) {
      started = false;
      super.stop();
      if (mysqlCheckTablesNode != null) {
        mysqlCheckTablesNode.stop();
        mysqlCheckTablesNode = null;
        mysqlDatabasesNode.mysqlServerNode._mysqlServersNode.hostNode.hostsNode.rootNode.nodeRemoved();
      }
    }
  }
}
