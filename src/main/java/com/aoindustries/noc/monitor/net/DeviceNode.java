/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008-2012, 2014, 2016, 2018, 2019, 2020, 2021, 2022, 2025  AO Industries, Inc.
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
import com.aoindustries.aoserv.client.AoservConnector;
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.aoserv.client.net.Device;
import com.aoindustries.aoserv.client.net.DeviceId;
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
 * The node per server.
 *
 * @author  AO Industries, Inc.
 */
public class DeviceNode extends NodeImpl {

  private static final long serialVersionUID = 1L;

  final DevicesNode devicesNode;
  private final Device device;
  private final String label;

  private static class ChildLock {
    // Empty lock class to help heap profile
  }

  private final ChildLock childLock = new ChildLock();
  private boolean started;

  private volatile DeviceBitRateNode deviceBitRateNode;
  private volatile DeviceBondingNode deviceBondingNode;
  private volatile IpAddressesNode ipAddressesNode;

  DeviceNode(DevicesNode devicesNode, Device device, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException, SQLException, IOException {
    super(port, csf, ssf);
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    this.devicesNode = devicesNode;
    this.device = device;
    this.label = device.getDeviceId().getName();
  }

  @Override
  public DevicesNode getParent() {
    return devicesNode;
  }

  public Device getDevice() {
    return device;
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public List<NodeImpl> getChildren() {
    return getSnapshot(
        this.deviceBitRateNode,
        this.deviceBondingNode,
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
            this.deviceBitRateNode,
            this.deviceBondingNode,
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
  public String getLabel() {
    return label;
  }

  private final TableListener tableListener = (Table<?> table) -> {
    try {
      verifyChildren();
    } catch (IOException | SQLException err) {
      throw new WrappedException(err);
    }
  };

  void start() throws IOException, SQLException {
    AoservConnector conn = devicesNode.hostNode.hostsNode.rootNode.conn;
    synchronized (childLock) {
      if (started) {
        throw new IllegalStateException();
      }
      started = true;
      conn.getNet().getIpAddress().addTableListener(tableListener, 100);
      conn.getNet().getDevice().addTableListener(tableListener, 100);
    }
    verifyChildren();
  }

  void stop() {
    RootNodeImpl rootNode = devicesNode.hostNode.hostsNode.rootNode;
    AoservConnector conn = rootNode.conn;
    synchronized (childLock) {
      started = false;
      conn.getNet().getIpAddress().removeTableListener(tableListener);
      conn.getNet().getDevice().removeTableListener(tableListener);
      if (ipAddressesNode != null) {
        ipAddressesNode.stop();
        ipAddressesNode = null;
        rootNode.nodeRemoved();
      }
      if (deviceBondingNode != null) {
        deviceBondingNode.stop();
        deviceBondingNode = null;
        rootNode.nodeRemoved();
      }
      if (deviceBitRateNode != null) {
        deviceBitRateNode.stop();
        deviceBitRateNode = null;
        rootNode.nodeRemoved();
      }
    }
  }

  private void verifyChildren() throws RemoteException, IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (childLock) {
      if (!started) {
        return;
      }
    }

    RootNodeImpl rootNode = devicesNode.hostNode.hostsNode.rootNode;

    Server linuxServer = devicesNode.getHost().getLinuxServer();
    Device currentNetDevice = device.getTable().getConnector().getNet().getDevice().get(device.getPkey());
    DeviceId netDeviceId = currentNetDevice.getDeviceId();
    boolean hasIpAddresses = !currentNetDevice.getIpAddresses().isEmpty();

    synchronized (childLock) {
      if (started) {
        // bit rate and network bonding monitoring only supported for Server
        if (
            linuxServer != null
                // bit rate for non-loopback devices
                && !netDeviceId.isLoopback()
                // and non-BMC
                && !netDeviceId.getName().equals(DeviceId.BMC)
        ) {
          if (deviceBitRateNode == null) {
            deviceBitRateNode = new DeviceBitRateNode(this, port, csf, ssf);
            deviceBitRateNode.start();
            rootNode.nodeAdded();
          }
        } else {
          if (deviceBitRateNode != null) {
            deviceBitRateNode.stop();
            deviceBitRateNode = null;
            rootNode.nodeRemoved();
          }
        }
        // bonding
        if (
            linuxServer != null
                && (
                label.equals(DeviceId.BOND0) // TODO: Flag for "net_devices.isBonded"
                    || label.equals(DeviceId.BOND1)
                    || label.equals(DeviceId.BOND2)
              )
        ) {
          if (deviceBondingNode == null) {
            deviceBondingNode = new DeviceBondingNode(this, port, csf, ssf);
            deviceBondingNode.start();
            rootNode.nodeAdded();
          }
        } else {
          if (deviceBondingNode != null) {
            deviceBondingNode.stop();
            deviceBondingNode = null;
            rootNode.nodeRemoved();
          }
        }
        // IP Addresses
        if (hasIpAddresses) {
          if (ipAddressesNode == null) {
            ipAddressesNode = new IpAddressesNode(this, port, csf, ssf);
            ipAddressesNode.start();
            rootNode.nodeAdded();
          }
        } else {
          if (ipAddressesNode != null) {
            ipAddressesNode.stop();
            ipAddressesNode = null;
            rootNode.nodeRemoved();
          }
        }
      }
    }
  }

  File getPersistenceDirectory() throws IOException {
    return devicesNode.hostNode.hostsNode.rootNode.mkdir(
        new File(
            devicesNode.getPersistenceDirectory(),
            label
        )
    );
  }
}
