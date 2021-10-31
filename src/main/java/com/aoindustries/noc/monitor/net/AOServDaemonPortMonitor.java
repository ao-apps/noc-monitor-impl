/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2001-2009, 2016, 2017, 2018, 2019, 2020, 2021  AO Industries, Inc.
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

import com.aoapps.net.InetAddress;
import com.aoapps.net.Port;
import com.aoapps.net.URIParameters;
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.noc.monitor.portmon.PortMonitor;

/**
 * Connects to the AOServDaemon through the AOServMaster in order to perform
 * the monitoring from the daemon's point of view.  This is required for
 * monitoring of private and loopback IP addresses.
 *
 * @author  AO Industries, Inc.
 */
public class AOServDaemonPortMonitor extends PortMonitor {

	private final Server linuxServer;
	private final String appProtocol;
	private final URIParameters monitoringParameters;

	public AOServDaemonPortMonitor(Server linuxServer, InetAddress ipAddress, Port port, String appProtocol, URIParameters monitoringParameters) {
		super(ipAddress, port);
		this.linuxServer = linuxServer;
		this.appProtocol = appProtocol;
		this.monitoringParameters = monitoringParameters;
	}

	@Override
	public String checkPort() throws Exception {
		return linuxServer.checkPort(ipAddress, port, appProtocol, monitoringParameters);
	}
}
