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
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import com.aoindustries.noc.monitor.NodeImpl;
import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.net.HostNode;
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
 * The node for all MySQLServers on one Server.
 *
 * @author  AO Industries, Inc.
 */
public class ServersNode extends NodeImpl {

  private static final long serialVersionUID = 1L;

  final HostNode hostNode;
  private final Server linuxServer;
  private final List<ServerNode> mysqlServerNodes = new ArrayList<>();
  private boolean started;

  public ServersNode(HostNode hostNode, Server linuxServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
    super(port, csf, ssf);
    this.hostNode = hostNode;
    this.linuxServer = linuxServer;
  }

  @Override
  public HostNode getParent() {
    return hostNode;
  }

  public Server getAOServer() {
    return linuxServer;
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public List<ServerNode> getChildren() {
    synchronized (mysqlServerNodes) {
      return getSnapshot(mysqlServerNodes);
    }
  }

  /**
   * The alert level is equal to the highest alert level of its children.
   */
  @Override
  public AlertLevel getAlertLevel() {
    AlertLevel level;
    synchronized (mysqlServerNodes) {
      level = AlertLevelUtils.getMaxAlertLevel(mysqlServerNodes);
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
    return PACKAGE_RESOURCES.getMessage(hostNode.hostsNode.rootNode.locale, "MySQLServersNode.label");
  }

  private final TableListener tableListener = (Table<?> table) -> {
    try {
      verifyMySQLServers();
    } catch (IOException | SQLException err) {
      throw new WrappedException(err);
    }
  };

  public void start() throws IOException, SQLException {
    synchronized (mysqlServerNodes) {
      if (started) {
        throw new IllegalStateException();
      }
      started = true;
      hostNode.hostsNode.rootNode.conn.getMysql().getServer().addTableListener(tableListener, 100);
    }
    verifyMySQLServers();
  }

  public void stop() {
    synchronized (mysqlServerNodes) {
      started = false;
      hostNode.hostsNode.rootNode.conn.getMysql().getServer().removeTableListener(tableListener);
      for (ServerNode mysqlServerNode : mysqlServerNodes) {
        mysqlServerNode.stop();
        hostNode.hostsNode.rootNode.nodeRemoved();
      }
      mysqlServerNodes.clear();
    }
  }

  private void verifyMySQLServers() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (mysqlServerNodes) {
      if (!started) {
        return;
      }
    }

    List<com.aoindustries.aoserv.client.mysql.Server> mysqlServers = linuxServer.getMySQLServers();
    synchronized (mysqlServerNodes) {
      if (started) {
        // Remove old ones
        Iterator<ServerNode> mysqlServerNodeIter = mysqlServerNodes.iterator();
        while (mysqlServerNodeIter.hasNext()) {
          ServerNode mysqlServerNode = mysqlServerNodeIter.next();
          com.aoindustries.aoserv.client.mysql.Server mysqlServer = mysqlServerNode.getMySQLServer();
          if (!mysqlServers.contains(mysqlServer)) {
            mysqlServerNode.stop();
            mysqlServerNodeIter.remove();
            hostNode.hostsNode.rootNode.nodeRemoved();
          }
        }
        // Add new ones
        for (int c=0;c<mysqlServers.size();c++) {
          com.aoindustries.aoserv.client.mysql.Server mysqlServer = mysqlServers.get(c);
          if (c >= mysqlServerNodes.size() || !mysqlServer.equals(mysqlServerNodes.get(c).getMySQLServer())) {
            // Insert into proper index
            ServerNode mysqlServerNode = new ServerNode(this, mysqlServer, port, csf, ssf);
            mysqlServerNodes.add(c, mysqlServerNode);
            mysqlServerNode.start();
            hostNode.hostsNode.rootNode.nodeAdded();
          }
        }
      }
    }
  }

  File getPersistenceDirectory() throws IOException {
    File dir = new File(hostNode.getPersistenceDirectory(), "mysql_servers");
    if (!dir.exists()) {
      if (!dir.mkdir()) {
        throw new IOException(
          PACKAGE_RESOURCES.getMessage(
            hostNode.hostsNode.rootNode.locale,
            "error.mkdirFailed",
            dir.getCanonicalPath()
          )
        );
      }
    }
    return dir;
  }
}
