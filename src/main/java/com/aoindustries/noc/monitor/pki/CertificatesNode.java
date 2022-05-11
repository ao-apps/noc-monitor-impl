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

package com.aoindustries.noc.monitor.pki;

import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;

import com.aoapps.hodgepodge.table.Table;
import com.aoapps.hodgepodge.table.TableListener;
import com.aoapps.lang.exception.WrappedException;
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.aoserv.client.pki.Certificate;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import com.aoindustries.noc.monitor.NodeImpl;
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
import javax.swing.SwingUtilities;

/**
 * @author  AO Industries, Inc.
 */
public class CertificatesNode extends NodeImpl {

  private static final long serialVersionUID = 1L;

  final HostNode hostNode;
  private final Server server;
  private final List<CertificateNode> certificateNodes = new ArrayList<>();
  private boolean started;

  public CertificatesNode(HostNode hostNode, Server server, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
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
  public List<CertificateNode> getChildren() {
    synchronized (certificateNodes) {
      return getSnapshot(certificateNodes);
    }
  }

  /**
   * The alert level is equal to the highest alert level of its children.
   */
  @Override
  public AlertLevel getAlertLevel() {
    AlertLevel level;
    synchronized (certificateNodes) {
      level = AlertLevelUtils.getMaxAlertLevel(certificateNodes);
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
    return PACKAGE_RESOURCES.getMessage(hostNode.hostsNode.rootNode.locale, "SslCertificatesNode.label");
  }

  private final TableListener tableListener = (Table<?> table) -> {
    try {
      verifySslCertificates();
    } catch (IOException | SQLException err) {
      throw new WrappedException(err);
    }
  };

  public void start() throws IOException, SQLException {
    synchronized (certificateNodes) {
      if (started) {
        throw new IllegalStateException();
      }
      started = true;
      hostNode.hostsNode.rootNode.conn.getPki().getCertificate().addTableListener(tableListener, 100);
      hostNode.hostsNode.rootNode.conn.getPki().getCertificateName().addTableListener(tableListener, 100);
    }
    verifySslCertificates();
  }

  public void stop() {
    synchronized (certificateNodes) {
      started = false;
      hostNode.hostsNode.rootNode.conn.getPki().getCertificateName().removeTableListener(tableListener);
      hostNode.hostsNode.rootNode.conn.getPki().getCertificate().removeTableListener(tableListener);
      for (CertificateNode sslCertificateNode : certificateNodes) {
        sslCertificateNode.stop();
        hostNode.hostsNode.rootNode.nodeRemoved();
      }
      certificateNodes.clear();
    }
  }

  private void verifySslCertificates() throws IOException, SQLException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (certificateNodes) {
      if (!started) {
        return;
      }
    }

    List<Certificate> certificates = server.getSslCertificates();
    synchronized (certificateNodes) {
      if (started) {
        // Remove old ones
        Iterator<CertificateNode> sslCertificateNodeIter = certificateNodes.iterator();
        while (sslCertificateNodeIter.hasNext()) {
          CertificateNode sslCertificateNode = sslCertificateNodeIter.next();
          Certificate sslCertificate = sslCertificateNode.getCertificate();
          // Find matching new state
          Certificate newCert = null;
          for (Certificate cert : certificates) {
            if (cert.equals(sslCertificate)) {
              newCert = cert;
              break;
            }
          }
          if (
              // Does not exist
              newCert == null
                  // or label changed
                  || !CertificateNode.getLabel(newCert).equals(sslCertificateNode.getLabel())
          ) {
            sslCertificateNode.stop();
            sslCertificateNodeIter.remove();
            hostNode.hostsNode.rootNode.nodeRemoved();
          }
        }
        // Add new ones
        for (int c = 0; c < certificates.size(); c++) {
          Certificate sslCertificate = certificates.get(c);
          if (c >= certificateNodes.size() || !sslCertificate.equals(certificateNodes.get(c).getCertificate())) {
            // Insert into proper index
            CertificateNode sslCertificateNode = new CertificateNode(this, sslCertificate, port, csf, ssf);
            certificateNodes.add(c, sslCertificateNode);
            sslCertificateNode.start();
            hostNode.hostsNode.rootNode.nodeAdded();
          }
        }
        // Prune any extra nodes that can happen when they are reordered
        while (certificateNodes.size() > certificates.size()) {
          CertificateNode sslCertificateNode = certificateNodes.remove(certificateNodes.size() - 1);
          sslCertificateNode.stop();
          hostNode.hostsNode.rootNode.nodeRemoved();
        }
      }
    }
  }

  File getPersistenceDirectory() throws IOException {
    File dir = new File(hostNode.getPersistenceDirectory(), "ssl_certificates");
    if (!dir.exists()) {
      if (!dir.mkdir()) {
        throw new IOException(
            PACKAGE_RESOURCES.getMessage(hostNode.hostsNode.rootNode.locale,
                "error.mkdirFailed",
                dir.getCanonicalPath()
            )
        );
      }
    }
    return dir;
  }
}
