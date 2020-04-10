/*
 * Copyright 2008-2013, 2014, 2018, 2019 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.infrastructure;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.TableResultNodeImpl;
import com.aoindustries.noc.monitor.linux.RaidNode;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;

/**
 * The node for the DRBD monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class DrbdNode extends TableResultNodeImpl {

	private static final long serialVersionUID = 1L;

	public DrbdNode(RaidNode raidNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException, SQLException {
		super(
			raidNode.hostNode.hostsNode.rootNode,
			raidNode,
			DrbdNodeWorker.getWorker(
				new File(raidNode.getPersistenceDirectory(), "drbdstatus"),
				raidNode.getAOServer()
			),
			port,
			csf,
			ssf
		);
	}

	@Override
	public String getLabel() {
		return accessor.getMessage(rootNode.locale, "DrbdNode.label");
	}
}
