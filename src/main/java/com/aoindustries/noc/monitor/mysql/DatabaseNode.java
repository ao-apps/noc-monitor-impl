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

  final DatabaseWorker databaseWorker;
  final DatabasesNode databasesNode;
  final Database database;
  private final MysqlReplication slave;
  private final Database.Name label;

  private boolean started;

  private volatile CheckTablesNode checkTablesNode;

  DatabaseNode(
      DatabasesNode databasesNode,
      Database database,
      MysqlReplication slave,
      int port,
      RMIClientSocketFactory csf,
      RMIServerSocketFactory ssf
  ) throws IOException, SQLException {
    super(
        databasesNode.serverNode.serversNode.hostNode.hostsNode.rootNode,
        databasesNode,
        DatabaseWorker.getWorker(
            new File(databasesNode.getPersistenceDirectory(), database.getName() + ".show_full_tables"),
            database,
            slave
        ),
        port,
        csf,
        ssf
    );
    this.databaseWorker = (DatabaseWorker) worker;
    this.databasesNode = databasesNode;
    this.database = database;
    this.slave = slave;
    this.label = database.getName();
  }

  Database getDatabase() {
    return database;
  }

  MysqlReplication getSlave() {
    return slave;
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public List<CheckTablesNode> getChildren() {
    return getSnapshot(this.checkTablesNode);
  }

  /**
   * The alert level is equal to the highest alert level of its children.
   */
  @Override
  public AlertLevel getAlertLevel() {
    return constrainAlertLevel(
        AlertLevelUtils.getMaxAlertLevel(
            super.getAlertLevel(),
            this.checkTablesNode
        )
    );
  }

  @Override
  public String getLabel() {
    return label.toString();
  }

  File getPersistenceDirectory() throws IOException {
    return databasesNode.serverNode.serversNode.hostNode.hostsNode.rootNode.mkdir(
        new File(
            databasesNode.getPersistenceDirectory(),
            label.toString()
        )
    );
  }

  @Override
  public void start() throws IOException {
    synchronized (this) {
      if (started) {
        throw new IllegalStateException();
      }
      started = true;
      if (checkTablesNode == null) {
        checkTablesNode = new CheckTablesNode(this, port, csf, ssf);
        checkTablesNode.start();
        databasesNode.serverNode.serversNode.hostNode.hostsNode.rootNode.nodeAdded();
      }
      super.start();
    }
  }

  @Override
  public void stop() {
    synchronized (this) {
      started = false;
      super.stop();
      if (checkTablesNode != null) {
        checkTablesNode.stop();
        checkTablesNode = null;
        databasesNode.serverNode.serversNode.hostNode.hostsNode.rootNode.nodeRemoved();
      }
    }
  }
}
