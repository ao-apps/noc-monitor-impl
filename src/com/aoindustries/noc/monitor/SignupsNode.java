/*
 * Copyright 2008-2009, 2014, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertCategory;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;

/**
 * The node for the signups monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class SignupsNode extends TableResultNodeImpl {

	private static final long serialVersionUID = 1L;

	SignupsNode(RootNodeImpl rootNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			rootNode,
			rootNode,
			SignupsNodeWorker.getWorker(
				new File(rootNode.getPersistenceDirectory(), "signups"),
				rootNode.conn
			),
			port,
			csf,
			ssf
		);
	}

	@Override
	public AlertCategory getAlertCategory() {
		return AlertCategory.SIGNUP;
	}

	@Override
	public String getLabel() {
		return accessor.getMessage(rootNode.locale, "SignupsNode.label");
	}
}
