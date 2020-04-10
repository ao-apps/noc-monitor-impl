/*
 * Copyright 2008-2012, 2016, 2018, 2019, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.net;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.TableMultiResultNodeImpl;
import com.aoindustries.noc.monitor.common.NetDeviceBitRateResult;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Arrays;
import java.util.List;

/**
 * @author  AO Industries, Inc.
 */
public class DeviceBitRateNode extends TableMultiResultNodeImpl<NetDeviceBitRateResult> {

	private static final long serialVersionUID = 1L;

	DeviceBitRateNode(DeviceNode netDeviceNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			netDeviceNode._networkDevicesNode.hostNode.hostsNode.rootNode,
			netDeviceNode,
			DeviceBitRateNodeWorker.getWorker(
				netDeviceNode.getPersistenceDirectory(),
				netDeviceNode.getNetDevice()
			),
			port,
			csf,
			ssf
		);
	}

	@Override
	public String getLabel() {
		return accessor.getMessage(rootNode.locale, "NetDeviceBitRateNode.label");
	}

	@Override
	public List<String> getColumnHeaders() {
		return Arrays.asList(
			accessor.getMessage(rootNode.locale, "NetDeviceBitRateNode.columnHeader.txBitRate"),
			accessor.getMessage(rootNode.locale, "NetDeviceBitRateNode.columnHeader.rxBitRate"),
			accessor.getMessage(rootNode.locale, "NetDeviceBitRateNode.columnHeader.txPacketRate"),
			accessor.getMessage(rootNode.locale, "NetDeviceBitRateNode.columnHeader.rxPacketRate"),
			accessor.getMessage(rootNode.locale, "NetDeviceBitRateNode.columnHeader.alertThresholds")
		);
	}
}
