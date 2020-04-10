/*
 * Copyright 2008-2009, 2016, 2018, 2019, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.infrastructure;

import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.aoserv.client.net.Host;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.RootNodeImpl;
import com.aoindustries.noc.monitor.common.AlertCategory;
import com.aoindustries.noc.monitor.net.HostsNode;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;

/**
 * The top-level node has one child for each of the servers.
 *
 * @author  AO Industries, Inc.
 */
public class VirtualServersNode extends HostsNode {

	private static final long serialVersionUID = 1L;

	public VirtualServersNode(RootNodeImpl rootNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(rootNode, port, csf, ssf);
	}

	@Override
	public AlertCategory getAlertCategory() {
		return AlertCategory.MONITORING;
	}

	@Override
	public String getLabel() {
		return accessor.getMessage(rootNode.locale, "VirtualServersNode.label");
	}

	@Override
	protected boolean includeHost(Host host) throws SQLException, IOException {
		Server linuxServer = host.getLinuxServer();
		return
			// Is Xen dom0
			host.getVirtualServer()!=null
			|| (
				// Is ao-box in fail-over
				linuxServer!=null && linuxServer.getFailoverServer()!=null
			)
		;
	}
}
