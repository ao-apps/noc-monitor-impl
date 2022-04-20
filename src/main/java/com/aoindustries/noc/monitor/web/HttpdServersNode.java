/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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

package com.aoindustries.noc.monitor.web;

import com.aoapps.hodgepodge.table.Table;
import com.aoapps.hodgepodge.table.TableListener;
import com.aoapps.lang.exception.WrappedException;
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.aoserv.client.web.HttpdServer;
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
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/**
 * The node for all {@link HttpdServer} on one {@link Server}.
 *
 * @author  AO Industries, Inc.
 */
public class HttpdServersNode extends NodeImpl {

  private static final Logger logger = Logger.getLogger(HttpdServersNode.class.getName());

  private static final long serialVersionUID = 1L;

  final HostNode hostNode;
  private final Server linuxServer;
  private final List<HttpdServerNode> httpdServerNodes = new ArrayList<>();
  private boolean started;

  public HttpdServersNode(HostNode hostNode, Server linuxServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
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
  public List<HttpdServerNode> getChildren() {
    synchronized (httpdServerNodes) {
      return getSnapshot(httpdServerNodes);
    }
  }

  /**
   * The alert level is equal to the highest alert level of its children.
   */
  @Override
  public AlertLevel getAlertLevel() {
    AlertLevel level;
    synchronized (httpdServerNodes) {
      level = AlertLevelUtils.getMaxAlertLevel(httpdServerNodes);
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
    return PACKAGE_RESOURCES.getMessage(hostNode.hostsNode.rootNode.locale, "HttpdServersNode.label");
  }

  private final TableListener tableListener = (Table<?> table) -> {
    try {
      verifyHttpdServers();
    } catch (IOException | SQLException err) {
      throw new WrappedException(err);
    }
  };

  public void start() throws IOException, SQLException {
    synchronized (httpdServerNodes) {
      if (started) {
        throw new IllegalStateException();
      }
      started = true;
      hostNode.hostsNode.rootNode.conn.getWeb().getHttpdServer().addTableListener(tableListener, 100);
    }
    verifyHttpdServers();
  }

  public void stop() {
    synchronized (httpdServerNodes) {
      started = false;
      hostNode.hostsNode.rootNode.conn.getWeb().getHttpdServer().removeTableListener(tableListener);
      for (HttpdServerNode httpdServerNode : httpdServerNodes) {
        httpdServerNode.stop();
        hostNode.hostsNode.rootNode.nodeRemoved();
      }
      httpdServerNodes.clear();
    }
  }

  @SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
  private void verifyHttpdServers() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (httpdServerNodes) {
      if (!started) {
        return;
      }
    }

    List<HttpdServer> httpdServers = linuxServer.getHttpdServers();
    if (logger.isLoggable(Level.FINER)) {
      logger.finer("httpdServers = " + httpdServers);
    }
    synchronized (httpdServerNodes) {
      if (started) {
        // Remove old ones
        Iterator<HttpdServerNode> httpdServerNodeIter = httpdServerNodes.iterator();
        while (httpdServerNodeIter.hasNext()) {
          HttpdServerNode httpdServerNode = httpdServerNodeIter.next();
          HttpdServer existingHttpdServer = httpdServerNode.getHttpdServer();
          // Look for existing node with matching HttpdServer with the same name
          boolean hasMatch = false;
          for (HttpdServer httpdServer : httpdServers) {
            if (httpdServer.equals(existingHttpdServer)) {
              if (Objects.equals(httpdServer.getName(), existingHttpdServer.getName())) {
                if (logger.isLoggable(Level.FINER)) {
                  logger.finer("Found with matching name " + existingHttpdServer.getName() + ", keeping node");
                }
                hasMatch = true;
              } else {
                if (logger.isLoggable(Level.FINE)) {
                  logger.fine("Name changed from " + existingHttpdServer.getName() + " to " + httpdServer.getName() + ", removing node");
                }
                // Name changed, remove old node
                // matches remains false
              }
              break;
            }
          }
          if (!hasMatch) {
            httpdServerNode.stop();
            httpdServerNodeIter.remove();
            hostNode.hostsNode.rootNode.nodeRemoved();
          }
        }
        // Add new ones
        for (int c = 0; c < httpdServers.size(); c++) {
          HttpdServer httpdServer = httpdServers.get(c);
          if (c >= httpdServerNodes.size() || !httpdServer.equals(httpdServerNodes.get(c).getHttpdServer())) {
            if (logger.isLoggable(Level.FINER)) {
              logger.finer("Adding node for " + httpdServer.getName());
            }
            try {
              // Insert into proper index
              if (logger.isLoggable(Level.FINER)) {
                logger.finer("Creating node for " + httpdServer.getName());
              }
              HttpdServerNode httpdServerNode = new HttpdServerNode(this, httpdServer, port, csf, ssf);
              if (logger.isLoggable(Level.FINER)) {
                logger.finer("Adding node to list for " + httpdServer.getName());
              }
              httpdServerNodes.add(c, httpdServerNode);
              if (logger.isLoggable(Level.FINER)) {
                logger.finer("Starting node for " + httpdServer.getName());
              }
              httpdServerNode.start();
              if (logger.isLoggable(Level.FINE)) {
                logger.fine("Notifying added for " + httpdServer.getName());
              }
              hostNode.hostsNode.rootNode.nodeAdded();
            } catch (ThreadDeath td) {
              throw td;
            } catch (Throwable t) {
              logger.log(Level.SEVERE, null, t);
              throw t;
            }
          }
        }
      }
    }
  }

  File getPersistenceDirectory() throws IOException {
    File dir = new File(hostNode.getPersistenceDirectory(), "httpd_servers");
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
