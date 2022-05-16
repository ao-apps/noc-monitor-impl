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

package com.aoindustries.noc.monitor.infrastructure;

import com.aoapps.lang.i18n.Resources;
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import com.aoindustries.noc.monitor.NodeImpl;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.net.HostNode;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.List;
import java.util.ResourceBundle;

/**
 * The node for hard drives.
 *
 * @author  AO Industries, Inc.
 */
public class HardDrivesNode extends NodeImpl {

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, HardDrivesNode.class);

  private static final long serialVersionUID = 1L;

  final HostNode hostNode;
  private final Server server;

  private boolean started;

  private volatile HardDrivesTemperatureNode hardDriveTemperatureNode;

  public HardDrivesNode(HostNode hostNode, Server server, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
    super(port, csf, ssf);
    this.hostNode = hostNode;
    this.server = server;
  }

  @Override
  public HostNode getParent() {
    return hostNode;
  }

  public Server getServer() {
    return server;
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public List<HardDrivesTemperatureNode> getChildren() {
    return getSnapshot(this.hardDriveTemperatureNode);
  }

  /**
   * The alert level is equal to the highest alert level of its children.
   */
  @Override
  public AlertLevel getAlertLevel() {
    return constrainAlertLevel(
        AlertLevelUtils.getMaxAlertLevel(
            this.hardDriveTemperatureNode
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
    return RESOURCES.getMessage(hostNode.hostsNode.rootNode.locale, "label");
  }

  public void start() throws IOException {
    synchronized (this) {
      if (started) {
        throw new IllegalStateException();
      }
      started = true;
      if (hardDriveTemperatureNode == null) {
        hardDriveTemperatureNode = new HardDrivesTemperatureNode(this, port, csf, ssf);
        hardDriveTemperatureNode.start();
        hostNode.hostsNode.rootNode.nodeAdded();
      }
    }
  }

  public void stop() {
    synchronized (this) {
      started = false;
      if (hardDriveTemperatureNode != null) {
        hardDriveTemperatureNode.stop();
        hardDriveTemperatureNode = null;
        hostNode.hostsNode.rootNode.nodeRemoved();
      }
    }
  }

  File getPersistenceDirectory() throws IOException {
    return hostNode.hostsNode.rootNode.mkdir(
        new File(
            hostNode.getPersistenceDirectory(),
            "hard_drives"
        )
    );
  }
}
