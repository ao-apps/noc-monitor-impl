/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2014, 2016, 2017, 2018, 2020, 2021, 2022  AO Industries, Inc.
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
import com.aoapps.net.InetAddress;
import com.aoindustries.aoserv.client.AoservConnector;
import com.aoindustries.aoserv.client.net.IpAddress;
import com.aoindustries.aoserv.client.net.monitoring.IpAddressMonitoring;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import com.aoindustries.noc.monitor.NodeImpl;
import com.aoindustries.noc.monitor.RootNodeImpl;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.dns.DnsNode;
import com.aoindustries.noc.monitor.email.BlacklistsNode;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * The node per IpAddress.
 *
 * @author  AO Industries, Inc.
 */
public class IpAddressNode extends NodeImpl {

  private static final long serialVersionUID = 1L;

  static String getLabel(IpAddress ipAddress) {
    InetAddress ip = ipAddress.getInetAddress();
    InetAddress externalIp = ipAddress.getExternalInetAddress();
    return
        (externalIp == null ? ip.toString() : (ip.toString() + "@" + externalIp.toString()))
            + "/" + ipAddress.getHostname();
  }

  static boolean isPingable(IpAddressesNode ipAddressesNode, IpAddress ipAddress) throws SQLException, IOException {
    // Private IPs and loopback IPs are not externally pingable
    InetAddress ip = ipAddress.getInetAddress();
    InetAddress externalIp = ipAddress.getExternalInetAddress();
    IpAddressMonitoring iam;
    return
        // Must have ping monitoring enabled
        ((iam = ipAddress.getMonitoring()) != null)
            && iam.getPingMonitorEnabled();
  }

  public final IpAddressesNode ipAddressesNode;
  private final IpAddress ipAddress;
  private final String label;

  private static class ChildLock {
    // Empty lock class to help heap profile
  }

  private final ChildLock childLock = new ChildLock();
  private boolean started;

  private volatile PingNode pingNode;
  private volatile BindsNode netBindsNode;
  private volatile DnsNode dnsNode;
  private volatile BlacklistsNode blacklistsNode;

  IpAddressNode(IpAddressesNode ipAddressesNode, IpAddress ipAddress, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException, IOException {
    super(port, csf, ssf);
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    this.ipAddressesNode = ipAddressesNode;
    this.ipAddress = ipAddress;
    this.label = getLabel(ipAddress);
  }

  @Override
  public IpAddressesNode getParent() {
    return ipAddressesNode;
  }

  public IpAddress getIpAddress() {
    return ipAddress;
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public List<NodeImpl> getChildren() {
    return getSnapshot(
        this.pingNode,
        this.netBindsNode,
        this.dnsNode,
        this.blacklistsNode
    );
  }

  /**
   * The alert level is equal to the highest alert level of its children.
   */
  @Override
  public AlertLevel getAlertLevel() {
    return constrainAlertLevel(
        AlertLevelUtils.getMaxAlertLevel(
            this.pingNode,
            this.netBindsNode,
            this.dnsNode,
            this.blacklistsNode
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

  void start() throws RemoteException, IOException, SQLException {
    AoservConnector conn = ipAddressesNode.rootNode.conn;
    synchronized (childLock) {
      if (started) {
        throw new IllegalStateException();
      }
      started = true;
      conn.getNet().getIpAddress().addTableListener(tableListener, 100);
      conn.getNet().getMonitoring().getIpAddressMonitoring().addTableListener(tableListener, 100);
      conn.getNet().getBind().addTableListener(tableListener, 100);
      conn.getNet().getDevice().addTableListener(tableListener, 100);
      conn.getNet().getDeviceId().addTableListener(tableListener, 100);
      conn.getNet().getHost().addTableListener(tableListener, 100);
    }
    verifyChildren();
  }

  void stop() {
    RootNodeImpl rootNode = ipAddressesNode.rootNode;
    AoservConnector conn = rootNode.conn;
    synchronized (childLock) {
      started = false;
      conn.getNet().getIpAddress().removeTableListener(tableListener);
      conn.getNet().getMonitoring().getIpAddressMonitoring().removeTableListener(tableListener);
      conn.getNet().getBind().removeTableListener(tableListener);
      conn.getNet().getDevice().removeTableListener(tableListener);
      conn.getNet().getDeviceId().removeTableListener(tableListener);
      conn.getNet().getHost().removeTableListener(tableListener);
      if (blacklistsNode != null) {
        blacklistsNode.stop();
        blacklistsNode = null;
        rootNode.nodeRemoved();
      }
      if (dnsNode != null) {
        dnsNode.stop();
        dnsNode = null;
        rootNode.nodeRemoved();
      }
      if (netBindsNode != null) {
        netBindsNode.stop();
        netBindsNode = null;
        rootNode.nodeRemoved();
      }
      if (pingNode != null) {
        pingNode.stop();
        pingNode = null;
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

    RootNodeImpl rootNode = ipAddressesNode.rootNode;

    IpAddress currentIpAddress = ipAddress.getTable().getConnector().getNet().getIpAddress().get(ipAddress.getPkey());
    boolean isPingable = isPingable(ipAddressesNode, currentIpAddress);
    boolean isLoopback =
        ipAddressesNode.deviceNode != null
            && ipAddressesNode.deviceNode.getDevice().getDeviceId().isLoopback();
    InetAddress ip = currentIpAddress.getExternalInetAddress();
    if (ip == null) {
      ip = currentIpAddress.getInetAddress();
    }
    boolean hasNetBinds = ipAddressesNode.deviceNode != null && !BindsNode.getSettings(currentIpAddress).isEmpty();
    IpAddressMonitoring iam = currentIpAddress.getMonitoring();

    synchronized (childLock) {
      if (started) {
        if (isPingable) {
          if (pingNode == null) {
            pingNode = new PingNode(this, port, csf, ssf);
            pingNode.start();
            rootNode.nodeAdded();
          }
        } else {
          if (pingNode != null) {
            pingNode.stop();
            pingNode = null;
            rootNode.nodeRemoved();
          }
        }
        if (hasNetBinds) {
          if (netBindsNode == null) {
            netBindsNode = new BindsNode(this, port, csf, ssf);
            netBindsNode.start();
            rootNode.nodeAdded();
          }
        } else {
          if (netBindsNode != null) {
            netBindsNode.stop();
            netBindsNode = null;
            rootNode.nodeRemoved();
          }
        }
        if (
            // Must have DNS verification enabled
            iam != null
                && (
                iam.getVerifyDnsPtr()
                    || iam.getVerifyDnsA()
            )
        ) {
          if (dnsNode == null) {
            dnsNode = new DnsNode(this, port, csf, ssf);
            dnsNode.start();
            rootNode.nodeAdded();
          }
        } else {
          if (dnsNode != null) {
            dnsNode.stop();
            dnsNode = null;
            rootNode.nodeRemoved();
          }
        }
        if (
            // Skip loopback device
            !isLoopback
                // Skip private IP addresses
                && !(ip.isUniqueLocal() || ip.isLoopback())
        ) {
          if (blacklistsNode == null) {
            blacklistsNode = new BlacklistsNode(this, port, csf, ssf);
            blacklistsNode.start();
            rootNode.nodeAdded();
          }
        } else {
          if (blacklistsNode != null) {
            blacklistsNode.stop();
            blacklistsNode = null;
            rootNode.nodeRemoved();
          }
        }
      }
    }
  }

  public File getPersistenceDirectory() throws IOException {
    return ipAddressesNode.rootNode.mkdir(
        new File(
            ipAddressesNode.getPersistenceDirectory(),
            ipAddress.getInetAddress().toString()
        )
    );
  }
}
