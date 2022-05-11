/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2009, 2014, 2016, 2017, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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
import javax.swing.SwingUtilities;

/**
 * The node per MySQL server.
 *
 * @author  AO Industries, Inc.
 */
public class ServerNode extends NodeImpl {

  private static final long serialVersionUID = 1L;

  final ServersNode serversNode;
  private final Server server;
  private final Server.Name label;

  private boolean started;
  private volatile SlavesNode slavesNode;
  private volatile DatabasesNode databasesNode;

  ServerNode(ServersNode serversNode, Server server, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException, IOException {
    super(port, csf, ssf);
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    this.serversNode = serversNode;
    this.server = server;
    this.label = server.getName();
  }

  @Override
  public ServersNode getParent() {
    return serversNode;
  }

  public Server getServer() {
    return server;
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public List<NodeImpl> getChildren() {
    return getSnapshot(
        this.slavesNode,
        this.databasesNode
    );
  }

  /**
   * The alert level is equal to the highest alert level of its children.
   */
  @Override
  public AlertLevel getAlertLevel() {
    return constrainAlertLevel(
        AlertLevelUtils.getMaxAlertLevel(
            this.slavesNode,
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
    return label.toString();
  }

  private final TableListener tableListener = (Table<?> table) -> {
    try {
      verifyFailoverMysqlReplications();
    } catch (IOException | SQLException err) {
      throw new WrappedException(err);
    }
  };

  void start() throws IOException, SQLException {
    RootNodeImpl rootNode = serversNode.hostNode.hostsNode.rootNode;
    synchronized (this) {
      if (started) {
        throw new IllegalStateException();
      }
      started = true;
      rootNode.conn.getBackup().getMysqlReplication().addTableListener(tableListener, 100);
      rootNode.conn.getNet().getHost().addTableListener(tableListener, 100);
    }
    verifyFailoverMysqlReplications();
    synchronized (this) {
      if (started) {
        if (databasesNode == null) {
          databasesNode = new DatabasesNode(this, port, csf, ssf);
          databasesNode.start();
          rootNode.nodeAdded();
        }
      }
    }
  }

  void stop() {
    RootNodeImpl rootNode = serversNode.hostNode.hostsNode.rootNode;
    synchronized (this) {
      started = false;
      // TODO: Review for other missing removeTableListener
      rootNode.conn.getBackup().getMysqlReplication().removeTableListener(tableListener);
      rootNode.conn.getNet().getHost().removeTableListener(tableListener);
      if (slavesNode != null) {
        slavesNode.stop();
        slavesNode = null;
        rootNode.nodeRemoved();
      }

      if (databasesNode != null) {
        databasesNode.stop();
        databasesNode = null;
        rootNode.nodeRemoved();
      }
    }
  }

  private void verifyFailoverMysqlReplications() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (this) {
      if (!started) {
        return;
      }
    }

    boolean hasMysqlReplication = server.getFailoverMysqlReplications().stream()
        .anyMatch(mysqlReplication -> WrappedException.call(mysqlReplication::isMonitoringEnabled));
    synchronized (this) {
      if (started) {
        if (hasMysqlReplication) {
          if (slavesNode == null) {
            slavesNode = new SlavesNode(this, port, csf, ssf);
            slavesNode.start();
            serversNode.hostNode.hostsNode.rootNode.nodeAdded();
          }
        } else {
          if (slavesNode != null) {
            slavesNode.stop();
            slavesNode = null;
            serversNode.hostNode.hostsNode.rootNode.nodeRemoved();
          }
        }
      }
    }
  }

  File getPersistenceDirectory() throws IOException {
    File dir = new File(serversNode.getPersistenceDirectory(), label.toString());
    if (!dir.exists()) {
      if (!dir.mkdir()) {
        throw new IOException(
            PACKAGE_RESOURCES.getMessage(
                serversNode.hostNode.hostsNode.rootNode.locale,
                "error.mkdirFailed",
                dir.getCanonicalPath()
            )
        );
      }
    }
    return dir;
  }
}
