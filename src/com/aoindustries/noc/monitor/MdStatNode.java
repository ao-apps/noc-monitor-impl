/*
 * Copyright 2008-2013, 2014 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;

/**
 * The node for the 3ware monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class MdStatNode extends SingleResultNodeImpl {

	private static final long serialVersionUID = 1L;

	MdStatNode(RaidNode raidNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			raidNode.serverNode.serversNode.rootNode,
			raidNode,
			MdStatNodeWorker.getWorker(
				new File(raidNode.getPersistenceDirectory(), "mdstat"),
				raidNode.getAOServer()
			),
			port,
			csf,
			ssf
		);
	}

	@Override
	public String getLabel() {
		return accessor.getMessage(/*rootNode.locale,*/ "MdStatNode.label");
	}
}
