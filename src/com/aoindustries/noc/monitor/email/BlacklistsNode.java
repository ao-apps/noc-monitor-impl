/*
 * Copyright 2009, 2014, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.email;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.TableResultNodeImpl;
import com.aoindustries.noc.monitor.net.IpAddressNode;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;

/**
 * The node for the blacklist monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class BlacklistsNode extends TableResultNodeImpl {

	private static final long serialVersionUID = 1L;

	//private final IpAddress ipAddress;

	public BlacklistsNode(IpAddressNode ipAddressNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException, SQLException {
		super(
			ipAddressNode.ipAddressesNode.rootNode,
			ipAddressNode,
			BlacklistsNodeWorker.getWorker(
				new File(ipAddressNode.getPersistenceDirectory(), "blacklists"),
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
		return accessor.getMessage(rootNode.locale, "BlacklistsNode.label");
	}
}
