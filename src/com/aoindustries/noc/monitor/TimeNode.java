/*
 * Copyright 2008-2012, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.TimeResult;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Collections;
import java.util.List;

/**
 * @author  AO Industries, Inc.
 */
public class TimeNode extends TableMultiResultNodeImpl<TimeResult> {

	private static final long serialVersionUID = 1L;

	private final AOServer _aoServer;

	TimeNode(ServerNode serverNode, AOServer aoServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			serverNode.serversNode.rootNode,
			serverNode,
			TimeNodeWorker.getWorker(
				serverNode.getPersistenceDirectory(),
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
		return accessor.getMessage(rootNode.locale, "TimeNode.label");
	}

	@Override
	public List<?> getColumnHeaders() {
		return Collections.singletonList(
			accessor.getMessage(rootNode.locale, "TimeNode.columnHeader.clockSkew")
		);
	}
}
