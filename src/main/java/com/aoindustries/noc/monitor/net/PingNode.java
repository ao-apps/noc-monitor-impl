/*
 * Copyright 2008-2012, 2014, 2016, 2018, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.net;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.TableMultiResultNodeImpl;
import com.aoindustries.noc.monitor.common.PingResult;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Collections;
import java.util.List;

/**
 * The ping node per server.
 *
 * @author  AO Industries, Inc.
 */
public class PingNode extends TableMultiResultNodeImpl<PingResult> {

	private static final long serialVersionUID = 1L;

	//private final IpAddressNode ipAddressNode;

	PingNode(IpAddressNode ipAddressNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			ipAddressNode.ipAddressesNode.rootNode,
			ipAddressNode,
			PingNodeWorker.getWorker(
				ipAddressNode.getPersistenceDirectory(),
				ipAddressNode.getIpAddress()
			),
			port,
			csf,
			ssf
		);
		//this.ipAddressNode = ipAddressNode;
	}

	@Override
	public String getLabel() {
		return accessor.getMessage(rootNode.locale, "PingNode.label");
	}

	@Override
	public List<?> getColumnHeaders() {
		return Collections.emptyList();
	}
}
