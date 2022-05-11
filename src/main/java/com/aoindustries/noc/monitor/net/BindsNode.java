/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2014, 2016, 2017, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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

import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;

import com.aoapps.hodgepodge.table.Table;
import com.aoapps.hodgepodge.table.TableListener;
import com.aoapps.lang.exception.WrappedException;
import com.aoapps.net.InetAddress;
import com.aoapps.net.Port;
import com.aoindustries.aoserv.client.AoservConnector;
import com.aoindustries.aoserv.client.net.Bind;
import com.aoindustries.aoserv.client.net.Device;
import com.aoindustries.aoserv.client.net.Host;
import com.aoindustries.aoserv.client.net.IpAddress;
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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * The node per Bind.
 * <p>
 * TODO: Add output of <code>netstat -ln</code> / <code>ss -lnt</code> here to detect extra ports.
 * </p>
 *
 * @author  AO Industries, Inc.
 */
public class BindsNode extends NodeImpl {

  private static final long serialVersionUID = 1L;

  final IpAddressNode ipAddressNode;
  private final List<BindNode> netBindNodes = new ArrayList<>();
  private boolean started;

  BindsNode(IpAddressNode ipAddressNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
    super(port, csf, ssf);

    this.ipAddressNode = ipAddressNode;
  }

  @Override
  public IpAddressNode getParent() {
    return ipAddressNode;
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public List<BindNode> getChildren() {
    synchronized (netBindNodes) {
      return getSnapshot(netBindNodes);
    }
  }

  /**
   * The alert level is equal to the highest alert level of its children.
   */
  @Override
  public AlertLevel getAlertLevel() {
    AlertLevel level;
    synchronized (netBindNodes) {
      level = AlertLevelUtils.getMaxAlertLevel(netBindNodes);
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
    return PACKAGE_RESOURCES.getMessage(ipAddressNode.ipAddressesNode.rootNode.locale, "NetBindsNode.label");
  }

  private final TableListener tableListener = (Table<?> table) -> {
    try {
      verifyNetBinds();
    } catch (IOException | SQLException err) {
      throw new WrappedException(err);
    }
  };

  void start() throws IOException, SQLException {
    AoservConnector conn = ipAddressNode.ipAddressesNode.rootNode.conn;
    synchronized (netBindNodes) {
      if (started) {
        throw new IllegalStateException();
      }
      started = true;
      conn.getWeb_jboss().getSite().addTableListener(tableListener, 100);
      conn.getWeb_tomcat().getSharedTomcat().addTableListener(tableListener, 100);
      conn.getWeb().getSite().addTableListener(tableListener, 100);
      conn.getWeb_tomcat().getSite().addTableListener(tableListener, 100);
      conn.getWeb_tomcat().getPrivateTomcatSite().addTableListener(tableListener, 100);
      conn.getWeb_tomcat().getWorker().addTableListener(tableListener, 100);
      conn.getNet().getIpAddress().addTableListener(tableListener, 100);
      conn.getNet().getBind().addTableListener(tableListener, 100);
      conn.getNet().getDevice().addTableListener(tableListener, 100);
    }
    verifyNetBinds();
  }

  void stop() {
    RootNodeImpl rootNode = ipAddressNode.ipAddressesNode.rootNode;
    AoservConnector conn = rootNode.conn;
    synchronized (netBindNodes) {
      started = false;
      conn.getWeb_jboss().getSite().removeTableListener(tableListener);
      conn.getWeb_tomcat().getSharedTomcat().removeTableListener(tableListener);
      conn.getWeb().getSite().removeTableListener(tableListener);
      conn.getWeb_tomcat().getSite().removeTableListener(tableListener);
      conn.getWeb_tomcat().getPrivateTomcatSite().removeTableListener(tableListener);
      conn.getWeb_tomcat().getWorker().removeTableListener(tableListener);
      conn.getNet().getIpAddress().removeTableListener(tableListener);
      conn.getNet().getBind().removeTableListener(tableListener);
      conn.getNet().getDevice().removeTableListener(tableListener);
      for (BindNode netBindNode : netBindNodes) {
        netBindNode.stop();
        rootNode.nodeRemoved();
      }
      netBindNodes.clear();
    }
  }

  static class NetMonitorSetting implements Comparable<NetMonitorSetting> {

    private final Host host;
    private final Bind netBind;
    private final InetAddress ipAddress;
    private final Port port;

    private NetMonitorSetting(Host host, Bind netBind, InetAddress ipAddress, Port port) {
      this.host = host;
      this.netBind = netBind;
      this.ipAddress = ipAddress;
      this.port = port;
    }

    @Override
    public String toString() {
      return host + ", " + netBind + ", " + ipAddress + ":" + port;
    }

    @Override
    public int compareTo(NetMonitorSetting o) {
      // Host
      int diff = host.compareTo(o.host);
      if (diff != 0) {
        return diff;
      }
      // IP
      diff = ipAddress.compareTo(o.ipAddress);
      if (diff != 0) {
        return diff;
      }
      // port
      return port.compareTo(o.port);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof NetMonitorSetting)) {
        return false;
      }
      NetMonitorSetting other = (NetMonitorSetting) obj;
      return
          port == other.port
              && host.equals(other.host)
              && netBind.equals(other.netBind)
              && ipAddress.equals(other.ipAddress);
    }

    @Override
    public int hashCode() {
      int hash = 7;
      hash = 11 * hash + host.hashCode();
      hash = 11 * hash + netBind.hashCode();
      hash = 11 * hash + ipAddress.hashCode();
      hash = 11 * hash + port.hashCode();
      return hash;
    }

    /**
     * Gets the Host for this port.
     */
    Host getServer() {
      return host;
    }

    Bind getNetBind() {
      return netBind;
    }

    /**
     * @return the ipAddress
     */
    InetAddress getIpAddress() {
      return ipAddress;
    }

    /**
     * @return the port
     */
    Port getPort() {
      return port;
    }
  }

  /**
   * The list of net binds is the binds directly on the IP address plus the wildcard binds.
   */
  static List<NetMonitorSetting> getSettings(IpAddress ipAddress) throws IOException, SQLException {
    Device device = ipAddress.getDevice();
    if (device == null) {
      return Collections.emptyList();
    }
    List<Bind> directNetBinds = ipAddress.getNetBinds();

    // Find the wildcard IP address, if available
    Host host = device.getHost();
    IpAddress wildcard = null;
    for (IpAddress ia : host.getIpAddresses()) {
      if (ia.getInetAddress().isUnspecified()) {
        wildcard = ia;
        break;
      }
    }
    List<Bind> wildcardNetBinds;
    if (wildcard == null) {
      wildcardNetBinds = Collections.emptyList();
    } else {
      wildcardNetBinds = host.getNetBinds(wildcard);
    }

    InetAddress inetaddress = ipAddress.getInetAddress();
    List<NetMonitorSetting> netMonitorSettings = new ArrayList<>(directNetBinds.size() + wildcardNetBinds.size());
    for (Bind netBind : directNetBinds) {
      if (netBind.isMonitoringEnabled() && !netBind.isDisabled()) {
        netMonitorSettings.add(
            new NetMonitorSetting(
                host,
                netBind,
                inetaddress,
                netBind.getPort()
            )
        );
      }
    }
    for (Bind netBind : wildcardNetBinds) {
      if (netBind.isMonitoringEnabled() && !netBind.isDisabled()) {
        netMonitorSettings.add(
            new NetMonitorSetting(
                host,
                netBind,
                inetaddress,
                netBind.getPort()
            )
        );
      }
    }
    Collections.sort(netMonitorSettings);
    return netMonitorSettings;
  }

  private void verifyNetBinds() throws RemoteException, IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (netBindNodes) {
      if (!started) {
        return;
      }
    }

    final RootNodeImpl rootNode = ipAddressNode.ipAddressesNode.rootNode;

    IpAddress ipAddress = ipAddressNode.getIpAddress();
    ipAddress = ipAddress.getTable().getConnector().getNet().getIpAddress().get(ipAddress.getPkey());
    List<NetMonitorSetting> netMonitorSettings = getSettings(ipAddress);

    synchronized (netBindNodes) {
      if (started) {
        // Remove old ones
        Iterator<BindNode> netBindNodeIter = netBindNodes.iterator();
        while (netBindNodeIter.hasNext()) {
          BindNode netBindNode = netBindNodeIter.next();
          NetMonitorSetting netMonitorSetting = netBindNode.getNetMonitorSetting();
          if (!netMonitorSettings.contains(netMonitorSetting)) {
            netBindNode.stop();
            netBindNodeIter.remove();
            rootNode.nodeRemoved();
          }
        }
        // Add new ones
        for (int c = 0; c < netMonitorSettings.size(); c++) {
          NetMonitorSetting netMonitorSetting = netMonitorSettings.get(c);
          if (c >= netBindNodes.size() || !netMonitorSetting.equals(netBindNodes.get(c).getNetMonitorSetting())) {
            // Insert into proper index
            BindNode netBindNode = new BindNode(this, netMonitorSetting, port, csf, ssf);
            netBindNodes.add(c, netBindNode);
            netBindNode.start();
            rootNode.nodeAdded();
          }
        }
      }
    }
  }

  File getPersistenceDirectory() throws IOException {
    File dir = new File(ipAddressNode.getPersistenceDirectory(), "net_binds");
    if (!dir.exists()) {
      if (!dir.mkdir()) {
        throw new IOException(
            PACKAGE_RESOURCES.getMessage(
                ipAddressNode.ipAddressesNode.rootNode.locale,
                "error.mkdirFailed",
                dir.getCanonicalPath()
            )
        );
      }
    }
    return dir;
  }
}
