/*
 * Copyright 2008-2009, 2016, 2018, 2019, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.net;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.SingleResultNodeImpl;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;

/**
 * The node for the bonding monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class DeviceBondingNode extends SingleResultNodeImpl {

	private static final long serialVersionUID = 1L;

	DeviceBondingNode(DeviceNode netDeviceNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			netDeviceNode._networkDevicesNode.hostNode.hostsNode.rootNode,
			netDeviceNode,
			DeviceBondingNodeWorker.getWorker(
				new File(netDeviceNode.getPersistenceDirectory(), "bonding"),
				netDeviceNode.getNetDevice()
			),
			port,
			csf,
			ssf
		);
	}

	@Override
	public String getLabel() {
		return accessor.getMessage(rootNode.locale, "NetDeviceBondingNode.label");
	}
}
