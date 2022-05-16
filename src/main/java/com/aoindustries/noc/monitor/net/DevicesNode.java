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
import com.aoapps.lang.i18n.Resources;
import com.aoindustries.aoserv.client.net.Device;
import com.aoindustries.aoserv.client.net.Host;
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
import javax.swing.SwingUtilities;

/**
 * The node per server.
 *
 * @author  AO Industries, Inc.
 */
public class DevicesNode extends NodeImpl {

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, DevicesNode.class);

  private static final long serialVersionUID = 1L;

  final HostNode hostNode;
  private final Host host;
  private final List<DeviceNode> deviceNodes = new ArrayList<>();
  private boolean started;

  DevicesNode(HostNode hostNode, Host host, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
    super(port, csf, ssf);
    this.hostNode = hostNode;
    this.host = host;
  }

  @Override
  public HostNode getParent() {
    return hostNode;
  }

  public Host getHost() {
    return host;
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public List<DeviceNode> getChildren() {
    synchronized (deviceNodes) {
      return getSnapshot(deviceNodes);
    }
  }

  /**
   * The alert level is equal to the highest alert level of its children.
   */
  @Override
  public AlertLevel getAlertLevel() {
    AlertLevel level;
    synchronized (deviceNodes) {
      level = AlertLevelUtils.getMaxAlertLevel(deviceNodes);
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
    return RESOURCES.getMessage(hostNode.hostsNode.rootNode.locale, "label");
  }

  private final TableListener tableListener = (Table<?> table) -> {
    try {
      verifyDevices();
    } catch (IOException | SQLException err) {
      throw new WrappedException(err);
    }
  };

  void start() throws IOException, SQLException {
    synchronized (deviceNodes) {
      if (started) {
        throw new IllegalStateException();
      }
      started = true;
      hostNode.hostsNode.rootNode.conn.getNet().getDevice().addTableListener(tableListener, 100);
    }
    verifyDevices();
  }

  void stop() {
    synchronized (deviceNodes) {
      started = false;
      hostNode.hostsNode.rootNode.conn.getNet().getDevice().removeTableListener(tableListener);
      for (DeviceNode deviceNode : deviceNodes) {
        deviceNode.stop();
        hostNode.hostsNode.rootNode.nodeRemoved();
      }
      deviceNodes.clear();
    }
  }

  private void verifyDevices() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (deviceNodes) {
      if (!started) {
        return;
      }
    }

    // Filter only those that are enabled
    List<Device> netDevices;
      {
        List<Device> allDevices = host.getNetDevices();
        netDevices = new ArrayList<>(allDevices.size());
        for (Device device : allDevices) {
          if (device.isMonitoringEnabled()) {
            netDevices.add(device);
          }
        }
      }
    synchronized (deviceNodes) {
      if (started) {
        // Remove old ones
        Iterator<DeviceNode> netDeviceNodeIter = deviceNodes.iterator();
        while (netDeviceNodeIter.hasNext()) {
          DeviceNode deviceNode = netDeviceNodeIter.next();
          Device device = deviceNode.getDevice();
          if (!netDevices.contains(device)) {
            deviceNode.stop();
            netDeviceNodeIter.remove();
            hostNode.hostsNode.rootNode.nodeRemoved();
          }
        }
        // Add new ones
        for (int c = 0; c < netDevices.size(); c++) {
          Device device = netDevices.get(c);
          if (c >= deviceNodes.size() || !device.equals(deviceNodes.get(c).getDevice())) {
            // Insert into proper index
            DeviceNode deviceNode = new DeviceNode(this, device, port, csf, ssf);
            deviceNodes.add(c, deviceNode);
            deviceNode.start();
            hostNode.hostsNode.rootNode.nodeAdded();
          }
        }
      }
    }
  }

  File getPersistenceDirectory() throws IOException {
    return hostNode.hostsNode.rootNode.mkdir(
        new File(
            hostNode.getPersistenceDirectory(),
            "net_devices"
        )
    );
  }
}
