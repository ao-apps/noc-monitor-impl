/*
 * Copyright 2008-2012, 2016 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.MemoryResult;
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

	//private final AOServer _aoServer;

	MemoryNode(ServerNode serverNode, AOServer aoServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			serverNode.serversNode.rootNode,
			serverNode,
			MemoryNodeWorker.getWorker(
				serverNode.getPersistenceDirectory(),
				aoServer
			),
			port,
			csf,
			ssf
		);
		//this._aoServer = aoServer;
	}

	@Override
	public String getLabel() {
		return accessor.getMessage(/*rootNode.locale,*/ "MemoryNode.label");
	}

	@Override
	public List<String> getColumnHeaders(/*Locale locale*/) {
		return Arrays.asList(
			accessor.getMessage(/*locale,*/ "MemoryNode.columnHeader.memTotal"),
			accessor.getMessage(/*locale,*/ "MemoryNode.columnHeader.memFree"),
			accessor.getMessage(/*locale,*/ "MemoryNode.columnHeader.buffers"),
			accessor.getMessage(/*locale,*/ "MemoryNode.columnHeader.cached"),
			accessor.getMessage(/*locale,*/ "MemoryNode.columnHeader.swapTotal"),
			accessor.getMessage(/*locale,*/ "MemoryNode.columnHeader.swapFree")
		);
	}
}
