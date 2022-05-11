/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2009, 2014, 2016, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoindustries.aoserv.client.backup.BackupPartition;
import com.aoindustries.aoserv.client.backup.FileReplication;
import com.aoindustries.aoserv.client.backup.MysqlReplication;
import com.aoindustries.aoserv.client.mysql.Server;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import com.aoindustries.noc.monitor.NodeImpl;
import com.aoindustries.noc.monitor.RootNodeImpl;
import com.aoindustries.noc.monitor.common.AlertLevel;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.List;

/**
 * The node for all FailoverMysqlReplications on one Server.
 *
 * @author  AO Industries, Inc.
 */
public class SlaveNode extends NodeImpl {

  private static final long serialVersionUID = 1L;

  final SlavesNode slavesNode;
  private final MysqlReplication mysqlReplication;
  private final String label;

  private boolean started;

  private volatile SlaveStatusNode slaveStatusNode;
  private volatile DatabasesNode databasesNode;

  SlaveNode(SlavesNode slavesNode, MysqlReplication mysqlReplication, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException, IOException, SQLException {
    super(port, csf, ssf);
    this.slavesNode = slavesNode;
    this.mysqlReplication = mysqlReplication;
    FileReplication replication = mysqlReplication.getFailoverFileReplication();
    if (replication != null) {
      // replication-based
      com.aoindustries.aoserv.client.linux.Server linuxServer = slavesNode.serverNode.serversNode.getLinuxServer();
      Server mysqlServer = slavesNode.serverNode.getServer();
      BackupPartition bp = mysqlReplication.getFailoverFileReplication().getBackupPartition();
      this.label = bp.getLinuxServer().getHostname() + ":" + bp.getPath() + "/" + linuxServer.getHostname() + "/var/lib/mysql/" + mysqlServer.getName();
    } else {
      // ao_server-based
      Server mysqlServer = slavesNode.serverNode.getServer();
      this.label = mysqlReplication.getLinuxServer().getHostname() + ":/var/lib/mysql/" + mysqlServer.getName();
    }
  }

  MysqlReplication getMysqlReplication() {
    return mysqlReplication;
  }

  @Override
  public SlavesNode getParent() {
    return slavesNode;
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public List<NodeImpl> getChildren() {
    return getSnapshot(
        this.slaveStatusNode,
        this.databasesNode
    );
  }

  /**
   * The maximum alert level is constrained by the failover_mysql_replications table.
   */
  @Override
  protected AlertLevel getMaxAlertLevel() {
    return AlertLevelUtils.getMonitoringAlertLevel(
        mysqlReplication.getMaxAlertLevel()
    );
  }

  /**
   * The alert level is equal to the highest alert level of its children.
   */
  @Override
  public AlertLevel getAlertLevel() {
    return constrainAlertLevel(
        AlertLevelUtils.getMaxAlertLevel(
            this.slaveStatusNode,
            this.databasesNode
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
    return label;
  }

  void start() throws IOException, SQLException {
    RootNodeImpl rootNode = slavesNode.serverNode.serversNode.hostNode.hostsNode.rootNode;
    synchronized (this) {
      if (started) {
        throw new IllegalStateException();
      }
      started = true;
      if (slaveStatusNode == null) {
        slaveStatusNode = new SlaveStatusNode(this, port, csf, ssf);
        slaveStatusNode.start();
        rootNode.nodeAdded();
      }
      if (databasesNode == null) {
        databasesNode = new DatabasesNode(this, port, csf, ssf);
        databasesNode.start();
        rootNode.nodeAdded();
      }
    }
  }

  void stop() {
    RootNodeImpl rootNode = slavesNode.serverNode.serversNode.hostNode.hostsNode.rootNode;
    synchronized (this) {
      started = false;
      if (slaveStatusNode != null) {
        slaveStatusNode.stop();
        slaveStatusNode = null;
        rootNode.nodeRemoved();
      }

      if (databasesNode != null) {
        databasesNode.stop();
        databasesNode = null;
        rootNode.nodeRemoved();
      }
    }
  }

  File getPersistenceDirectory() throws IOException {
    File dir = new File(slavesNode.getPersistenceDirectory(), Integer.toString(mysqlReplication.getPkey()));
    if (!dir.exists()) {
      if (!dir.mkdir()) {
        throw new IOException(
            PACKAGE_RESOURCES.getMessage(
                slavesNode.serverNode.serversNode.hostNode.hostsNode.rootNode.locale,
                "error.mkdirFailed",
                dir.getCanonicalPath()
            )
        );
      }
    }
    return dir;
  }
}
