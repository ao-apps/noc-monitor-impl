/*
 * Copyright 2008-2009, 2016 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.aoserv.client.Server;
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
public class VirtualServersNode extends ServersNode {

	private static final long serialVersionUID = 1L;

	VirtualServersNode(RootNodeImpl rootNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(rootNode, port, csf, ssf);
	}

	@Override
	public String getLabel() {
		return accessor.getMessage(/*rootNode.locale,*/ "VirtualServersNode.label");
	}

	@Override
	boolean includeServer(Server server) throws SQLException, IOException {
		AOServer aoServer = server.getAOServer();
		return
			// Is Xen dom0
			server.getVirtualServer()!=null
			|| (
				// Is ao-box in fail-over
				aoServer!=null && aoServer.getFailoverServer()!=null
			)
		;
	}
}
