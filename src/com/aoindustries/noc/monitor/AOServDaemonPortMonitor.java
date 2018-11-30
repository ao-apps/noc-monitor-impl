/*
 * Copyright 2001-2009, 2016, 2017, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.net.HttpParameters;
import com.aoindustries.net.InetAddress;
import com.aoindustries.net.Port;
import com.aoindustries.noc.monitor.portmon.PortMonitor;

/**
 * Connects to the AOServDaemon through the AOServMaster in order to perform
 * the monitoring from the daemon's point of view.  This is required for
 * monitoring of private and loopback IP addresses.
 *
 * @author  AO Industries, Inc.
 */
public class AOServDaemonPortMonitor extends PortMonitor {

	private final Server aoServer;
	private final String appProtocol;
	private final HttpParameters monitoringParameters;

	public AOServDaemonPortMonitor(Server aoServer, InetAddress ipAddress, Port port, String appProtocol, HttpParameters monitoringParameters) {
		super(ipAddress, port);
		this.aoServer = aoServer;
		this.appProtocol = appProtocol;
		this.monitoringParameters = monitoringParameters;
	}

	@Override
	public String checkPort() throws Exception {
		return aoServer.checkPort(ipAddress, port, appProtocol, monitoringParameters);
	}
}
