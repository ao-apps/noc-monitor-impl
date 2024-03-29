/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2014, 2018, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoapps.lang.i18n.Resources;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import com.aoindustries.noc.monitor.NodeImpl;
import com.aoindustries.noc.monitor.RootNodeImpl;
import com.aoindustries.noc.monitor.common.AlertCategory;
import com.aoindustries.noc.monitor.common.AlertLevel;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

/**
 * The node for unallocated resources that can still be monitored.
 *
 * @author  AO Industries, Inc.
 */
public class UnallocatedNode extends NodeImpl {

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, UnallocatedNode.class);

  private static final long serialVersionUID = 1L;

  final RootNodeImpl rootNode;

  private boolean started;

  private volatile IpAddressesNode ipAddressesNode;

  public UnallocatedNode(RootNodeImpl rootNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
    super(port, csf, ssf);
    this.rootNode = rootNode;
  }

  @Override
  public RootNodeImpl getParent() {
    return rootNode;
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public List<IpAddressesNode> getChildren() {
    return getSnapshot(
        this.ipAddressesNode
    );
  }

  /**
   * The alert level is equal to the highest alert level of its children.
   */
  @Override
  public AlertLevel getAlertLevel() {
    return constrainAlertLevel(
        AlertLevelUtils.getMaxAlertLevel(
            this.ipAddressesNode
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
  public AlertCategory getAlertCategory() {
    return AlertCategory.MONITORING;
  }

  @Override
  public String getLabel() {
    return RESOURCES.getMessage(rootNode.locale, "label");
  }

  public void start() throws IOException, SQLException {
    synchronized (this) {
      if (started) {
        throw new IllegalStateException();
      }
      started = true;
      if (ipAddressesNode == null) {
        ipAddressesNode = new IpAddressesNode(this, port, csf, ssf);
        ipAddressesNode.start();
        rootNode.nodeAdded();
      }
    }
  }

  public void stop() {
    synchronized (this) {
      started = false;
      if (ipAddressesNode != null) {
        ipAddressesNode.stop();
        ipAddressesNode = null;
        rootNode.nodeRemoved();
      }
    }
  }

  File getPersistenceDirectory() throws IOException {
    return rootNode.mkdir(
        new File(
            rootNode.getPersistenceDirectory(),
            "unallocated"
        )
    );
  }
}
