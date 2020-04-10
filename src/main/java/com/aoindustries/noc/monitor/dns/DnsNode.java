/*
 * Copyright 2009, 2014, 2016, 2018, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.dns;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.TableResultNodeImpl;
import com.aoindustries.noc.monitor.net.IpAddressNode;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;

/**
 * The node for the DNS monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class DnsNode extends TableResultNodeImpl {

	private static final long serialVersionUID = 1L;

	//private final IpAddress ipAddress;

	public DnsNode(IpAddressNode ipAddressNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException, SQLException {
		super(
			ipAddressNode.ipAddressesNode.rootNode,
			ipAddressNode,
			DnsNodeWorker.getWorker(
				new File(ipAddressNode.getPersistenceDirectory(), "rdns"),
				ipAddressNode.getIpAddress()
			),
			port,
			csf,
			ssf
		);
		//this.ipAddress = ipAddressNode.getIpAddress();
	}

	@Override
	public String getLabel() {
		return accessor.getMessage(rootNode.locale, "DnsNode.label");
	}
}
