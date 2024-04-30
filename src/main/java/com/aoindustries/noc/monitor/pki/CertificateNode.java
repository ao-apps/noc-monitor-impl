/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2018, 2019, 2020, 2022, 2024  AO Industries, Inc.
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

import com.aoindustries.aoserv.client.pki.Certificate;
import com.aoindustries.noc.monitor.TableResultNodeImpl;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;

/**
 * The node for the SSL certificate monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class CertificateNode extends TableResultNodeImpl {

  private static final long serialVersionUID = 1L;

  private final Certificate certificate;
  private final String label;

  CertificateNode(CertificatesNode sslCertificatesNode, Certificate sslCertificate, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException, SQLException {
    super(
        sslCertificatesNode.hostNode.hostsNode.rootNode,
        sslCertificatesNode,
        CertificateWorker.getWorker(
            new File(sslCertificatesNode.getPersistenceDirectory(), Integer.toString(sslCertificate.getPkey())),
            sslCertificate
        ),
        port,
        csf,
        ssf
    );
    this.certificate = sslCertificate;
    this.label = getLabel(sslCertificate);
  }

  static String getLabel(Certificate cert) throws IOException, SQLException {
    return cert.getCommonName().getName();
  }

  @Override
  public String getLabel() {
    return label;
  }

  public Certificate getCertificate() {
    return certificate;
  }
}
