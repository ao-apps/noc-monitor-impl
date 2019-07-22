/*
 * Copyright 2008-2012, 2016, 2018, 2019 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.linux;

import com.aoindustries.aoserv.client.linux.Server;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.TableMultiResultNodeImpl;
import com.aoindustries.noc.monitor.common.TimeResult;
import com.aoindustries.noc.monitor.net.HostNode;
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

	private final Server _linuxServer;

	public TimeNode(HostNode hostNode, Server linuxServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			hostNode.hostsNode.rootNode,
			hostNode,
			TimeNodeWorker.getWorker(
				hostNode.getPersistenceDirectory(),
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
		return accessor.getMessage(rootNode.locale, "TimeNode.label");
	}

	@Override
	public List<?> getColumnHeaders() {
		return Collections.singletonList(
			accessor.getMessage(rootNode.locale, "TimeNode.columnHeader.clockSkew")
		);
	}
}
