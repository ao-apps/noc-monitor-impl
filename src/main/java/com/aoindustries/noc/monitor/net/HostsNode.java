/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2014, 2016, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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

package com.aoindustries.noc.monitor.net;

import com.aoapps.hodgepodge.table.Table;
import com.aoapps.hodgepodge.table.TableListener;
import com.aoapps.lang.exception.WrappedException;
import com.aoindustries.aoserv.client.net.Host;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * The top-level node has one child for each of the servers.
 *
 * @author  AO Industries, Inc.
 */
public abstract class HostsNode extends NodeImpl {

  private static final long serialVersionUID = 1L;

  public final RootNodeImpl rootNode;

  private final List<HostNode> hostNodes = new ArrayList<>();
  private boolean started;

  protected HostsNode(RootNodeImpl rootNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
    super(port, csf, ssf);
    this.rootNode = rootNode;
  }

  @Override
  public final RootNodeImpl getParent() {
    return rootNode;
  }

  @Override
  public final boolean getAllowsChildren() {
    return true;
  }

  @Override
  public final List<HostNode> getChildren() {
    synchronized (hostNodes) {
      return getSnapshot(hostNodes);
    }
  }

  /**
   * The alert level is equal to the highest alert level of its children.
   */
  @Override
  public final AlertLevel getAlertLevel() {
    AlertLevel level;
    synchronized (hostNodes) {
      level = AlertLevelUtils.getMaxAlertLevel(hostNodes);
    }
    return constrainAlertLevel(level);
  }

  /**
   * No alert messages.
   */
  @Override
  public final String getAlertMessage() {
    return null;
  }

  private final TableListener tableListener = (Table<?> table) -> {
    try {
      verifyServers();
    } catch (IOException | SQLException err) {
      throw new WrappedException(err);
    }
  };

  public final void start() throws IOException, SQLException {
    synchronized (hostNodes) {
      if (started) {
        throw new IllegalStateException();
      }
      started = true;
      rootNode.conn.getNet().getHost().addTableListener(tableListener, 100);
    }
    verifyServers();
  }

  final void stop() {
    synchronized (hostNodes) {
      started = false;
      rootNode.conn.getNet().getHost().removeTableListener(tableListener);
      for (HostNode hostNode : hostNodes) {
        hostNode.stop();
        rootNode.nodeRemoved();
      }
      hostNodes.clear();
    }
  }

  private void verifyServers() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (hostNodes) {
      if (!started) {
        return;
      }
    }

    // Get all the servers that have monitoring enabled
    List<Host> allHosts = rootNode.conn.getNet().getHost().getRows();
    List<Host> hosts = new ArrayList<>(allHosts.size());
    for (Host host : allHosts) {
      if (host.isMonitoringEnabled() && includeHost(host)) {
        hosts.add(host);
      }
    }
    synchronized (hostNodes) {
      if (started) {
        // Remove old ones
        Iterator<HostNode> hostNodeIter = hostNodes.iterator();
        while (hostNodeIter.hasNext()) {
          HostNode hostNode = hostNodeIter.next();
          Host host = hostNode.getHost();
          if (!hosts.contains(host)) {
            hostNode.stop();
            hostNodeIter.remove();
            rootNode.nodeRemoved();
          }
        }
        // Add new ones
        for (int c = 0; c < hosts.size(); c++) {
          Host host = hosts.get(c);
          if (c >= hostNodes.size() || !host.equals(hostNodes.get(c).getHost())) {
            // Insert into proper index
            HostNode hostNode = new HostNode(this, host, port, csf, ssf);
            hostNodes.add(c, hostNode);
            hostNode.start();
            rootNode.nodeAdded();
          }
        }
      }
    }
  }

  /**
   * Gets the top-level persistence directory.
   */
  final File getPersistenceDirectory() throws IOException {
    return rootNode.mkdir(
        new File(
            rootNode.getPersistenceDirectory(),
            "servers"
        )
    );
  }

  protected abstract boolean includeHost(Host host) throws SQLException, IOException;
}
