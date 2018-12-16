/*
 * Copyright 2008-2009, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.infrastructure;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.SingleResultNodeImpl;
import com.aoindustries.noc.monitor.linux.RaidNode;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;

/**
 * The node for the 3ware monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class ThreeWareRaidNode extends SingleResultNodeImpl {

	private static final long serialVersionUID = 1L;

	public ThreeWareRaidNode(RaidNode raidNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			raidNode.serverNode.hostsNode.rootNode,
			raidNode,
			ThreeWareRaidNodeWorker.getWorker(
				new File(raidNode.getPersistenceDirectory(), "3ware"),
				raidNode.getAOServer()
			),
			port,
			csf,
			ssf
		);
	}

	@Override
	public String getLabel() {
		return accessor.getMessage(rootNode.locale, "ThreeWareRaidNode.label");
	}
}
