/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008-2012, 2016, 2018, 2019, 2020, 2022  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of noc-monitor-impl.
 *
 * noc-monitor-impl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * noc-monitor-impl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with noc-monitor-impl.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoindustries.noc.monitor.net;

import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;
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
		return PACKAGE_RESOURCES.getMessage(rootNode.locale, "NetDeviceBitRateNode.label");
	}

	@Override
	public List<String> getColumnHeaders() {
		return Arrays.asList(PACKAGE_RESOURCES.getMessage(rootNode.locale, "NetDeviceBitRateNode.columnHeader.txBitRate"),
			PACKAGE_RESOURCES.getMessage(rootNode.locale, "NetDeviceBitRateNode.columnHeader.rxBitRate"),
			PACKAGE_RESOURCES.getMessage(rootNode.locale, "NetDeviceBitRateNode.columnHeader.txPacketRate"),
			PACKAGE_RESOURCES.getMessage(rootNode.locale, "NetDeviceBitRateNode.columnHeader.rxPacketRate"),
			PACKAGE_RESOURCES.getMessage(rootNode.locale, "NetDeviceBitRateNode.columnHeader.alertThresholds")
		);
	}
}
