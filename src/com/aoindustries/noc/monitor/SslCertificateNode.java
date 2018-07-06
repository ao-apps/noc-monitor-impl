/*
 * Copyright 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.SslCertificate;
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
public class SslCertificateNode extends TableResultNodeImpl {

	private static final long serialVersionUID = 1L;

	private final SslCertificate _sslCertificate;
	private final String _label;

	static String getLabel(SslCertificate cert) throws IOException, SQLException {
		return cert.getCommonName().getName();
	}

	SslCertificateNode(SslCertificatesNode sslCertificatesNode, SslCertificate sslCertificate, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException, SQLException {
		super(
			sslCertificatesNode.serverNode.serversNode.rootNode,
			sslCertificatesNode,
			SslCertificateNodeWorker.getWorker(
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

	public SslCertificate getSslCertificate() {
		return _sslCertificate;
	}
}
