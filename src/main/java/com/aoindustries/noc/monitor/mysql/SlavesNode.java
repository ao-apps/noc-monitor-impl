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

import com.aoapps.hodgepodge.table.Table;
import com.aoapps.hodgepodge.table.TableListener;
import com.aoapps.lang.exception.WrappedException;
import com.aoapps.lang.i18n.Resources;
import com.aoindustries.aoserv.client.backup.MysqlReplication;
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
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;

/**
 * The node for all FailoverMysqlReplications on one Server.
 *
 * @author  AO Industries, Inc.
 */
public class SlavesNode extends NodeImpl {

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, SlavesNode.class);

  private static final long serialVersionUID = 1L;

  final ServerNode serverNode;
  private final List<SlaveNode> slaveNodes = new ArrayList<>();
  private boolean started;

  SlavesNode(ServerNode serverNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
    super(port, csf, ssf);
    this.serverNode = serverNode;
  }

  @Override
  public ServerNode getParent() {
    return serverNode;
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public List<SlaveNode> getChildren() {
    synchronized (slaveNodes) {
      return getSnapshot(slaveNodes);
    }
  }

  /**
   * The alert level is equal to the highest alert level of its children.
   */
  @Override
  public AlertLevel getAlertLevel() {
    AlertLevel level;
    synchronized (slaveNodes) {
      level = AlertLevelUtils.getMaxAlertLevel(slaveNodes);
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
    return RESOURCES.getMessage(serverNode.serversNode.hostNode.hostsNode.rootNode.locale, "label");
  }

  private final TableListener tableListener = (Table<?> table) -> {
    try {
      verifyMysqlSlaves();
    } catch (IOException | SQLException err) {
      throw new WrappedException(err);
    }
  };

  void start() throws IOException, SQLException {
    synchronized (slaveNodes) {
      if (started) {
        throw new IllegalStateException();
      }
      started = true;
      serverNode.serversNode.hostNode.hostsNode.rootNode.conn.getBackup().getFileReplication().addTableListener(tableListener, 100);
      serverNode.serversNode.hostNode.hostsNode.rootNode.conn.getBackup().getMysqlReplication().addTableListener(tableListener, 100);
      serverNode.serversNode.hostNode.hostsNode.rootNode.conn.getNet().getHost().addTableListener(tableListener, 100);
    }
    verifyMysqlSlaves();
  }

  void stop() {
    synchronized (slaveNodes) {
      started = false;
      serverNode.serversNode.hostNode.hostsNode.rootNode.conn.getBackup().getFileReplication().removeTableListener(tableListener);
      serverNode.serversNode.hostNode.hostsNode.rootNode.conn.getBackup().getMysqlReplication().removeTableListener(tableListener);
      serverNode.serversNode.hostNode.hostsNode.rootNode.conn.getNet().getHost().removeTableListener(tableListener);
      for (SlaveNode slaveNode : slaveNodes) {
        slaveNode.stop();
        serverNode.serversNode.hostNode.hostsNode.rootNode.nodeRemoved();
      }
      slaveNodes.clear();
    }
  }

  private void verifyMysqlSlaves() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (slaveNodes) {
      if (!started) {
        return;
      }
    }

    List<MysqlReplication> mysqlReplications = serverNode.getServer().getFailoverMysqlReplications().stream()
        .filter(mysqlReplication -> WrappedException.call(mysqlReplication::isMonitoringEnabled))
        .collect(Collectors.toList());
    synchronized (slaveNodes) {
      if (started) {
        // Remove old ones
        Iterator<SlaveNode> slaveNodeIter = slaveNodes.iterator();
        while (slaveNodeIter.hasNext()) {
          SlaveNode slaveNode = slaveNodeIter.next();
          MysqlReplication mysqlReplication = slaveNode.getMysqlReplication();
          if (!mysqlReplications.contains(mysqlReplication)) {
            slaveNode.stop();
            slaveNodeIter.remove();
            serverNode.serversNode.hostNode.hostsNode.rootNode.nodeRemoved();
          }
        }
        // Add new ones
        for (int c = 0; c < mysqlReplications.size(); c++) {
          MysqlReplication mysqlReplication = mysqlReplications.get(c);
          if (c >= slaveNodes.size() || !mysqlReplication.equals(slaveNodes.get(c).getMysqlReplication())) {
            // Insert into proper index
            SlaveNode slaveNode = new SlaveNode(this, mysqlReplication, port, csf, ssf);
            slaveNodes.add(c, slaveNode);
            slaveNode.start();
            serverNode.serversNode.hostNode.hostsNode.rootNode.nodeAdded();
          }
        }
      }
    }
  }

  File getPersistenceDirectory() throws IOException {
    return serverNode.serversNode.hostNode.hostsNode.rootNode.mkdir(
        new File(
            serverNode.getPersistenceDirectory(),
            "mysql_slaves"
        )
    );
  }
}
