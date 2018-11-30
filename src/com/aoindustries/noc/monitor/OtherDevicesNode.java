/*
 * Copyright 2008-2009, 2014, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.infrastructure.PhysicalServer;
import com.aoindustries.aoserv.client.linux.AOServer;
import com.aoindustries.aoserv.client.net.Server;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
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
public class OtherDevicesNode extends ServersNode {

	private static final long serialVersionUID = 1L;

	OtherDevicesNode(RootNodeImpl rootNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(rootNode, port, csf, ssf);
	}
	@Override
	public String getLabel() {
		return accessor.getMessage(rootNode.locale, "OtherDevicesNode.label");
	}

	@Override
	boolean includeServer(Server server) throws SQLException, IOException {
		PhysicalServer physicalServer = server.getPhysicalServer();
		AOServer aoServer = server.getAOServer();
		return
			// Is not a physical server
			(physicalServer==null || physicalServer.getRam()==-1)
			// Is not a Xen dom0
			&& server.getVirtualServer()==null
			// Is not an ao-box in fail-over
			&& (aoServer==null || aoServer.getFailoverServer()==null)
		;
	}
}
