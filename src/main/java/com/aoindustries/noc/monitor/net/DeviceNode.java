/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008-2012, 2014, 2016, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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
import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.aoserv.client.net.Device;
import com.aoindustries.aoserv.client.net.DeviceId;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import com.aoindustries.noc.monitor.NodeImpl;
import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;
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

  final DevicesNode _networkDevicesNode;
  private final Device _device;
  private final String _label;

  private static class ChildLock {
    // Empty lock class to help heap profile
  }
  private final ChildLock childLock = new ChildLock();
  private boolean started;

  private volatile DeviceBitRateNode _netDeviceBitRateNode;
  private volatile DeviceBondingNode _netDeviceBondingNode;
  private volatile IpAddressesNode _ipAddressesNode;

  DeviceNode(DevicesNode networkDevicesNode, Device device, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException, SQLException, IOException {
    super(port, csf, ssf);
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    this._networkDevicesNode = networkDevicesNode;
    this._device = device;
    this._label = device.getDeviceId().getName();
  }

  @Override
  public DevicesNode getParent() {
    return _networkDevicesNode;
  }

  public Device getNetDevice() {
    return _device;
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public List<NodeImpl> getChildren() {
    return getSnapshot(
        this._netDeviceBitRateNode,
        this._netDeviceBondingNode,
        this._ipAddressesNode
    );
  }

  /**
   * The alert level is equal to the highest alert level of its children.
   */
  @Override
  public AlertLevel getAlertLevel() {
    return constrainAlertLevel(
        AlertLevelUtils.getMaxAlertLevel(
            this._netDeviceBitRateNode,
            this._netDeviceBondingNode,
            this._ipAddressesNode
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
    return _label;
  }

  private final TableListener tableListener = (Table<?> table) -> {
    try {
      verifyChildren();
    } catch (IOException | SQLException err) {
      throw new WrappedException(err);
    }
  };

  void start() throws IOException, SQLException {
    AOServConnector conn = _networkDevicesNode.hostNode.hostsNode.rootNode.conn;
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
    RootNodeImpl rootNode = _networkDevicesNode.hostNode.hostsNode.rootNode;
    AOServConnector conn = rootNode.conn;
    synchronized (childLock) {
      started = false;
      conn.getNet().getIpAddress().removeTableListener(tableListener);
      conn.getNet().getDevice().removeTableListener(tableListener);
      if (_ipAddressesNode != null) {
        _ipAddressesNode.stop();
        _ipAddressesNode = null;
        rootNode.nodeRemoved();
      }
      if (_netDeviceBondingNode != null) {
        _netDeviceBondingNode.stop();
        _netDeviceBondingNode = null;
        rootNode.nodeRemoved();
      }
      if (_netDeviceBitRateNode != null) {
        _netDeviceBitRateNode.stop();
        _netDeviceBitRateNode = null;
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

    RootNodeImpl rootNode = _networkDevicesNode.hostNode.hostsNode.rootNode;

    Server linuxServer = _networkDevicesNode.getHost().getLinuxServer();
    Device currentNetDevice = _device.getTable().getConnector().getNet().getDevice().get(_device.getPkey());
    DeviceId netDeviceID = currentNetDevice.getDeviceId();
    boolean hasIpAddresses = !currentNetDevice.getIPAddresses().isEmpty();

    synchronized (childLock) {
      if (started) {
        // bit rate and network bonding monitoring only supported for Server
        if (
            linuxServer != null
                // bit rate for non-loopback devices
                && !netDeviceID.isLoopback()
                // and non-BMC
                && !netDeviceID.getName().equals(DeviceId.BMC)
        ) {
          if (_netDeviceBitRateNode == null) {
            _netDeviceBitRateNode = new DeviceBitRateNode(this, port, csf, ssf);
            _netDeviceBitRateNode.start();
            rootNode.nodeAdded();
          }
        } else {
          if (_netDeviceBitRateNode != null) {
            _netDeviceBitRateNode.stop();
            _netDeviceBitRateNode = null;
            rootNode.nodeRemoved();
          }
        }
        // bonding
        if (
            linuxServer != null
                && (
                _label.equals(DeviceId.BOND0) // TODO: Flag for "net_devices.isBonded"
                    || _label.equals(DeviceId.BOND1)
                    || _label.equals(DeviceId.BOND2)
            )
        ) {
          if (_netDeviceBondingNode == null) {
            _netDeviceBondingNode = new DeviceBondingNode(this, port, csf, ssf);
            _netDeviceBondingNode.start();
            rootNode.nodeAdded();
          }
        } else {
          if (_netDeviceBondingNode != null) {
            _netDeviceBondingNode.stop();
            _netDeviceBondingNode = null;
            rootNode.nodeRemoved();
          }
        }
        // IP Addresses
        if (hasIpAddresses) {
          if (_ipAddressesNode == null) {
            _ipAddressesNode = new IpAddressesNode(this, port, csf, ssf);
            _ipAddressesNode.start();
            rootNode.nodeAdded();
          }
        } else {
          if (_ipAddressesNode != null) {
            _ipAddressesNode.stop();
            _ipAddressesNode = null;
            rootNode.nodeRemoved();
          }
        }
      }
    }
  }

  File getPersistenceDirectory() throws IOException {
    File dir = new File(_networkDevicesNode.getPersistenceDirectory(), _label);
    if (!dir.exists()) {
      if (!dir.mkdir()) {
        throw new IOException(
            PACKAGE_RESOURCES.getMessage(
                _networkDevicesNode.hostNode.hostsNode.rootNode.locale,
                "error.mkdirFailed",
                dir.getCanonicalPath()
            )
        );
      }
    }
    return dir;
  }
}
