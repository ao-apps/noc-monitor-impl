/*
 * Copyright 2018, 2019, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
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

	private final Certificate _sslCertificate;
	private final String _label;

	static String getLabel(Certificate cert) throws IOException, SQLException {
		return cert.getCommonName().getName();
	}

	CertificateNode(CertificatesNode sslCertificatesNode, Certificate sslCertificate, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException, SQLException {
		super(
			sslCertificatesNode.hostNode.hostsNode.rootNode,
			sslCertificatesNode,
			CertificateNodeWorker.getWorker(
				new File(sslCertificatesNode.getPersistenceDirectory(), Integer.toString(sslCertificate.getPkey())),
				sslCertificate
			),
			port,
			csf,
			ssf
		);
		this._sslCertificate = sslCertificate;
		this._label = getLabel(sslCertificate);
	}

	@Override
	public String getLabel() {
		return _label;
	}

	public Certificate getSslCertificate() {
		return _sslCertificate;
	}
}
