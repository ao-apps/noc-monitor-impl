/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008-2013, 2014, 2016, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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
import com.aoindustries.aoserv.client.distribution.OperatingSystemVersion;
import com.aoindustries.aoserv.client.infrastructure.PhysicalServer;
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.aoserv.client.net.Host;
import com.aoindustries.aoserv.client.web.HttpdServer;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import com.aoindustries.noc.monitor.NodeImpl;
import com.aoindustries.noc.monitor.backup.BackupsNode;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.infrastructure.HardDrivesNode;
import com.aoindustries.noc.monitor.infrastructure.UpsNode;
import com.aoindustries.noc.monitor.linux.FilesystemsNode;
import com.aoindustries.noc.monitor.linux.LoadAverageNode;
import com.aoindustries.noc.monitor.linux.MemoryNode;
import com.aoindustries.noc.monitor.linux.RaidNode;
import com.aoindustries.noc.monitor.linux.TimeNode;
import com.aoindustries.noc.monitor.mysql.ServersNode;
import com.aoindustries.noc.monitor.pki.CertificatesNode;
import com.aoindustries.noc.monitor.web.HttpdServersNode;
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
public class HostNode extends NodeImpl {

  private static final long serialVersionUID = 1L;

  public final HostsNode hostsNode;
  private final Host host;
  private final int packageId;
  private final String name;
  private final String label;

  private boolean started;
  private volatile BackupsNode backupsNode;
  private volatile DevicesNode devicesNode;
  private volatile HttpdServersNode httpdServersNode;
  private volatile ServersNode mysqlServersNode;
  private volatile HardDrivesNode hardDrivesNode;
  private volatile RaidNode raidNode;
  private volatile CertificatesNode sslCertificatesNode;
  private volatile UpsNode upsNode;
  private volatile FilesystemsNode filesystemsNode;
  private volatile LoadAverageNode loadAverageNode;
  private volatile MemoryNode memoryNode;
  private volatile TimeNode timeNode;

  HostNode(HostsNode hostsNode, Host host, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException, IOException {
    super(port, csf, ssf);
    this.hostsNode = hostsNode;
    this.host = host;
    this.packageId = host.getPackageId();
    this.name = host.getName();
    this.label = host.toString();
  }

  @Override
  public HostsNode getParent() {
    return hostsNode;
  }

  public Host getHost() {
    return host;
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public List<NodeImpl> getChildren() {
    return getSnapshot(
        this.backupsNode,
        this.devicesNode,
        this.httpdServersNode,
        this.mysqlServersNode,
        this.hardDrivesNode,
        this.raidNode,
        this.sslCertificatesNode,
        this.upsNode,
        this.filesystemsNode,
        this.loadAverageNode,
        this.memoryNode,
        this.timeNode
    );
  }

  /**
   * The alert level is equal to the highest alert level of its children.
   */
  @Override
  public AlertLevel getAlertLevel() {
    return constrainAlertLevel(
        AlertLevelUtils.getMaxAlertLevel(
            this.backupsNode,
            this.devicesNode,
            this.httpdServersNode,
            this.mysqlServersNode,
            this.hardDrivesNode,
            this.raidNode,
            this.sslCertificatesNode,
            this.upsNode,
            this.filesystemsNode,
            this.loadAverageNode,
            this.memoryNode,
            this.timeNode
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
      verifyNetDevices();
      verifyHttpdServers();
      verifyMysqlServers();
      verifyHardDrives();
      verifyRaid();
      verifySslCertificates();
      verifyUps();
      verifyFilesystems();
      verifyLoadAverage();
      verifyMemory();
      verifyTime();
    } catch (IOException | SQLException err) {
      throw new WrappedException(err);
    }
  };

  /**
   * Starts this node after it is added to the parent.
   */
  public void start() throws IOException, SQLException {
    synchronized (this) {
      if (started) {
        throw new IllegalStateException();
      }
      started = true;
      hostsNode.rootNode.conn.getLinux().getServer().addTableListener(tableListener, 100);
      hostsNode.rootNode.conn.getNet().getDevice().addTableListener(tableListener, 100);
      hostsNode.rootNode.conn.getWeb().getHttpdServer().addTableListener(tableListener, 100);
      hostsNode.rootNode.conn.getMysql().getServer().addTableListener(tableListener, 100);
      hostsNode.rootNode.conn.getInfrastructure().getPhysicalServer().addTableListener(tableListener, 100);
      hostsNode.rootNode.conn.getNet().getHost().addTableListener(tableListener, 100);
      hostsNode.rootNode.conn.getPki().getCertificate().addTableListener(tableListener, 100);
      if (backupsNode == null) {
        backupsNode = new BackupsNode(this, port, csf, ssf);
        backupsNode.start();
        hostsNode.rootNode.nodeAdded();
      }
    }
    verifyNetDevices();
    verifyHttpdServers();
    verifyMysqlServers();
    verifyHardDrives();
    verifyRaid();
    verifySslCertificates();
    verifyUps();
    verifyFilesystems();
    verifyLoadAverage();
    verifyMemory();
    verifyTime();
  }

  /**
   * Stops this node before it is removed from the parent.
   */
  public void stop() {
    synchronized (this) {
      started = false;
      hostsNode.rootNode.conn.getLinux().getServer().removeTableListener(tableListener);
      hostsNode.rootNode.conn.getNet().getDevice().removeTableListener(tableListener);
      hostsNode.rootNode.conn.getWeb().getHttpdServer().removeTableListener(tableListener);
      hostsNode.rootNode.conn.getMysql().getServer().removeTableListener(tableListener);
      hostsNode.rootNode.conn.getInfrastructure().getPhysicalServer().removeTableListener(tableListener);
      hostsNode.rootNode.conn.getNet().getHost().removeTableListener(tableListener);
      hostsNode.rootNode.conn.getPki().getCertificate().removeTableListener(tableListener);
      if (timeNode != null) {
        timeNode.stop();
        timeNode = null;
        hostsNode.rootNode.nodeRemoved();
      }
      if (memoryNode != null) {
        memoryNode.stop();
        memoryNode = null;
        hostsNode.rootNode.nodeRemoved();
      }
      if (loadAverageNode != null) {
        loadAverageNode.stop();
        loadAverageNode = null;
        hostsNode.rootNode.nodeRemoved();
      }
      if (filesystemsNode != null) {
        filesystemsNode.stop();
        filesystemsNode = null;
        hostsNode.rootNode.nodeRemoved();
      }
      if (upsNode != null) {
        upsNode.stop();
        upsNode = null;
        hostsNode.rootNode.nodeRemoved();
      }
      if (sslCertificatesNode != null) {
        sslCertificatesNode.stop();
        sslCertificatesNode = null;
        hostsNode.rootNode.nodeRemoved();
      }
      if (raidNode != null) {
        raidNode.stop();
        raidNode = null;
        hostsNode.rootNode.nodeRemoved();
      }
      if (hardDrivesNode != null) {
        hardDrivesNode.stop();
        hardDrivesNode = null;
        hostsNode.rootNode.nodeRemoved();
      }
      if (mysqlServersNode != null) {
        mysqlServersNode.stop();
        mysqlServersNode = null;
        hostsNode.rootNode.nodeRemoved();
      }
      if (httpdServersNode != null) {
        httpdServersNode.stop();
        httpdServersNode = null;
        hostsNode.rootNode.nodeRemoved();
      }
      if (devicesNode != null) {
        devicesNode.stop();
        devicesNode = null;
        hostsNode.rootNode.nodeRemoved();
      }
      if (backupsNode != null) {
        backupsNode.stop();
        backupsNode = null;
        hostsNode.rootNode.nodeRemoved();
      }
    }
  }

  private void verifyNetDevices() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";
    synchronized (this) {
      if (started) {
        if (devicesNode == null) {
          devicesNode = new DevicesNode(this, host, port, csf, ssf);
          devicesNode.start();
          hostsNode.rootNode.nodeAdded();
        }
      }
    }
  }

  private void verifyHttpdServers() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (this) {
      if (!started) {
        return;
      }
    }

    Server linuxServer = host.getLinuxServer();
    List<HttpdServer> httpdServers = linuxServer == null ? null : linuxServer.getHttpdServers();
    synchronized (this) {
      if (started) {
        if (httpdServers != null && !httpdServers.isEmpty()) {
          // Has HTTPD server
          if (httpdServersNode == null) {
            httpdServersNode = new HttpdServersNode(this, linuxServer, port, csf, ssf);
            httpdServersNode.start();
            hostsNode.rootNode.nodeAdded();
          }
        } else {
          // No HTTPD server
          if (httpdServersNode != null) {
            httpdServersNode.stop();
            httpdServersNode = null;
            hostsNode.rootNode.nodeRemoved();
          }
        }
      }
    }
  }

  private void verifyMysqlServers() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (this) {
      if (!started) {
        return;
      }
    }

    Server linuxServer = host.getLinuxServer();
    List<com.aoindustries.aoserv.client.mysql.Server> mysqlServers = linuxServer == null ? null : linuxServer.getMysqlServers();
    synchronized (this) {
      if (started) {
        if (mysqlServers != null && !mysqlServers.isEmpty()) {
          // Has MySQL server
          if (mysqlServersNode == null) {
            mysqlServersNode = new ServersNode(this, linuxServer, port, csf, ssf);
            mysqlServersNode.start();
            hostsNode.rootNode.nodeAdded();
          }
        } else {
          // No MySQL server
          if (mysqlServersNode != null) {
            mysqlServersNode.stop();
            mysqlServersNode = null;
            hostsNode.rootNode.nodeRemoved();
          }
        }
      }
    }
  }

  private void verifyHardDrives() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (this) {
      if (!started) {
        return;
      }
    }

    Server linuxServer = host.getLinuxServer();
    OperatingSystemVersion osvObj = host.getOperatingSystemVersion();
    int osv = osvObj == null ? -1 : osvObj.getPkey();
    synchronized (this) {
      if (started) {
        if (
            linuxServer != null
                && (
                osv == OperatingSystemVersion.CENTOS_5_DOM0_I686
                    || osv == OperatingSystemVersion.CENTOS_5_DOM0_X86_64
                    || osv == OperatingSystemVersion.CENTOS_7_DOM0_X86_64
            )
        ) {
          // Has hddtemp monitoring
          if (hardDrivesNode == null) {
            hardDrivesNode = new HardDrivesNode(this, linuxServer, port, csf, ssf);
            hardDrivesNode.start();
            hostsNode.rootNode.nodeAdded();
          }
        } else {
          // No hddtemp monitoring
          if (hardDrivesNode != null) {
            hardDrivesNode.stop();
            hardDrivesNode = null;
            hostsNode.rootNode.nodeRemoved();
          }
        }
      }
    }
  }

  private void verifyRaid() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (this) {
      if (!started) {
        return;
      }
    }

    Server linuxServer = host.getLinuxServer();
    synchronized (this) {
      if (started) {
        if (linuxServer == null) {
          // No raid monitoring
          if (raidNode != null) {
            raidNode.stop();
            raidNode = null;
            hostsNode.rootNode.nodeRemoved();
          }
        } else {
          // Has raid monitoring
          if (raidNode == null) {
            raidNode = new RaidNode(this, linuxServer, port, csf, ssf);
            raidNode.start();
            hostsNode.rootNode.nodeAdded();
          }
        }
      }
    }
  }

  private void verifySslCertificates() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (this) {
      if (!started) {
        return;
      }
    }

    Server linuxServer = host.getLinuxServer();
    int numCerts = linuxServer == null ? 0 : linuxServer.getSslCertificates().size();
    synchronized (this) {
      if (started) {
        if (numCerts == 0) {
          // No SSL certificate monitoring or no certificates to monitor
          if (sslCertificatesNode != null) {
            sslCertificatesNode.stop();
            sslCertificatesNode = null;
            hostsNode.rootNode.nodeRemoved();
          }
        } else {
          // Has monitored SSL certificates
          if (sslCertificatesNode == null) {
            sslCertificatesNode = new CertificatesNode(this, linuxServer, port, csf, ssf);
            sslCertificatesNode.start();
            hostsNode.rootNode.nodeAdded();
          }
        }
      }
    }
  }

  private void verifyUps() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (this) {
      if (!started) {
        return;
      }
    }

    Server linuxServer = host.getLinuxServer();
    PhysicalServer physicalServer = host.getPhysicalServer();
    synchronized (this) {
      if (started) {
        if (
            linuxServer == null
                || physicalServer == null
                || physicalServer.getUpsType() != PhysicalServer.UpsType.apc
        ) {
          // No UPS monitoring
          if (upsNode != null) {
            upsNode.stop();
            upsNode = null;
            hostsNode.rootNode.nodeRemoved();
          }
        } else {
          // Has UPS monitoring
          if (upsNode == null) {
            upsNode = new UpsNode(this, linuxServer, port, csf, ssf);
            upsNode.start();
            hostsNode.rootNode.nodeAdded();
          }
        }
      }
    }
  }

  private void verifyFilesystems() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (this) {
      if (!started) {
        return;
      }
    }

    Server linuxServer = host.getLinuxServer();
    synchronized (this) {
      if (started) {
        if (linuxServer == null) {
          // No filesystem monitoring
          if (filesystemsNode != null) {
            filesystemsNode.stop();
            filesystemsNode = null;
            hostsNode.rootNode.nodeRemoved();
          }
        } else {
          // Has filesystem monitoring
          if (filesystemsNode == null) {
            filesystemsNode = new FilesystemsNode(this, linuxServer, port, csf, ssf);
            filesystemsNode.start();
            hostsNode.rootNode.nodeAdded();
          }
        }
      }
    }
  }

  private void verifyLoadAverage() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (this) {
      if (!started) {
        return;
      }
    }

    Server linuxServer = host.getLinuxServer();
    synchronized (this) {
      if (started) {
        if (linuxServer == null) {
          // No load monitoring
          if (loadAverageNode != null) {
            loadAverageNode.stop();
            loadAverageNode = null;
            hostsNode.rootNode.nodeRemoved();
          }
        } else {
          // Has load monitoring
          if (loadAverageNode == null) {
            loadAverageNode = new LoadAverageNode(this, linuxServer, port, csf, ssf);
            loadAverageNode.start();
            hostsNode.rootNode.nodeAdded();
          }
        }
      }
    }
  }

  private void verifyMemory() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (this) {
      if (!started) {
        return;
      }
    }

    Server linuxServer = host.getLinuxServer();
    synchronized (this) {
      if (started) {
        if (linuxServer == null) {
          // No memory monitoring
          if (memoryNode != null) {
            memoryNode.stop();
            memoryNode = null;
            hostsNode.rootNode.nodeRemoved();
          }
        } else {
          // Has memory monitoring
          if (memoryNode == null) {
            memoryNode = new MemoryNode(this, linuxServer, port, csf, ssf);
            memoryNode.start();
            hostsNode.rootNode.nodeAdded();
          }
        }
      }
    }
  }

  private void verifyTime() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (this) {
      if (!started) {
        return;
      }
    }

    Server linuxServer = host.getLinuxServer();
    synchronized (this) {
      if (started) {
        if (linuxServer == null) {
          // No time monitoring
          if (timeNode != null) {
            timeNode.stop();
            timeNode = null;
            hostsNode.rootNode.nodeRemoved();
          }
        } else {
          // Has time monitoring
          if (timeNode == null) {
            timeNode = new TimeNode(this, linuxServer, port, csf, ssf);
            timeNode.start();
            hostsNode.rootNode.nodeAdded();
          }
        }
      }
    }
  }

  public File getPersistenceDirectory() throws IOException {
    File packDir = new File(hostsNode.getPersistenceDirectory(), Integer.toString(packageId));
    if (!packDir.exists()) {
      if (!packDir.mkdir()) {
        throw new IOException(
            PACKAGE_RESOURCES.getMessage(hostsNode.rootNode.locale,
                "error.mkdirFailed",
                packDir.getCanonicalPath()
            )
        );
      }
    }
    File serverDir = new File(packDir, name);
    if (!serverDir.exists()) {
      if (!serverDir.mkdir()) {
        throw new IOException(
            PACKAGE_RESOURCES.getMessage(hostsNode.rootNode.locale,
                "error.mkdirFailed",
                serverDir.getCanonicalPath()
            )
        );
      }
    }
    return serverDir;
  }
}
