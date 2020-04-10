/*
 * Copyright 2001-2009, 2016, 2017, 2018, 2019, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.net;

import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.net.InetAddress;
import com.aoindustries.net.Port;
import com.aoindustries.net.URIParameters;
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
