/*
 * Copyright 2008-2009, 2016, 2018, 2019 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.linux;

import com.aoindustries.aoserv.client.linux.Server;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.TableResultNodeImpl;
import com.aoindustries.noc.monitor.net.HostNode;
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

	private final Server _linuxServer;

	public FilesystemsNode(HostNode hostNode, Server linuxServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			hostNode.hostsNode.rootNode,
			hostNode,
			FilesystemsNodeWorker.getWorker(
				new File(hostNode.getPersistenceDirectory(), "filesystems"),
				linuxServer
			),
			port,
			csf,
			ssf
		);
		this._linuxServer = linuxServer;
	}

	@Override
	public String getLabel() {
		return accessor.getMessage(rootNode.locale, "FilesystemsNode.label");
	}
}
