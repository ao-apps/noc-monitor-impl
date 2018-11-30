/*
 * Copyright 2008-2009, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.linux.Server;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;

/**
 * The node for the filesystem monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class FilesystemsNode extends TableResultNodeImpl {

	private static final long serialVersionUID = 1L;

	private final Server _aoServer;

	FilesystemsNode(ServerNode serverNode, Server aoServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			serverNode.serversNode.rootNode,
			serverNode,
			FilesystemsNodeWorker.getWorker(
				new File(serverNode.getPersistenceDirectory(), "filesystems"),
				aoServer
			),
			port,
			csf,
			ssf
		);
		this._aoServer = aoServer;
	}

	@Override
	public String getLabel() {
		return accessor.getMessage(rootNode.locale, "FilesystemsNode.label");
	}
}
