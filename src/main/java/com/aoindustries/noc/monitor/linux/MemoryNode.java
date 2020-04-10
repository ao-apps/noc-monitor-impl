/*
 * Copyright 2008-2012, 2016, 2018, 2019, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.linux;

import com.aoindustries.aoserv.client.linux.Server;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.TableMultiResultNodeImpl;
import com.aoindustries.noc.monitor.common.MemoryResult;
import com.aoindustries.noc.monitor.net.HostNode;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Arrays;
import java.util.List;

/**
 * @author  AO Industries, Inc.
 */
public class MemoryNode extends TableMultiResultNodeImpl<MemoryResult> {

	private static final long serialVersionUID = 1L;

	//private final Server _linuxServer;

	public MemoryNode(HostNode hostNode, Server linuxServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			hostNode.hostsNode.rootNode,
			hostNode,
			MemoryNodeWorker.getWorker(
				hostNode.getPersistenceDirectory(),
				linuxServer
			),
			port,
			csf,
			ssf
		);
		//this._linuxServer = linuxServer;
	}

	@Override
	public String getLabel() {
		return accessor.getMessage(rootNode.locale, "MemoryNode.label");
	}

	@Override
	public List<String> getColumnHeaders() {
		return Arrays.asList(
			accessor.getMessage(rootNode.locale, "MemoryNode.columnHeader.memTotal"),
			accessor.getMessage(rootNode.locale, "MemoryNode.columnHeader.memFree"),
			accessor.getMessage(rootNode.locale, "MemoryNode.columnHeader.buffers"),
			accessor.getMessage(rootNode.locale, "MemoryNode.columnHeader.cached"),
			accessor.getMessage(rootNode.locale, "MemoryNode.columnHeader.swapTotal"),
			accessor.getMessage(rootNode.locale, "MemoryNode.columnHeader.swapFree")
		);
	}
}
