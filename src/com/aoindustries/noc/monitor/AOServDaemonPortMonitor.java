package com.aoindustries.noc.monitor;

/*
 * Copyright 2001-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.noc.monitor.portmon.PortMonitor;
import java.util.Map;

/**
 * Connects to the AOServDaemon through the AOServMaster in order to perform
 * the monitoring from the daemon's point of view.  This is required for
 * monitoring of private and loopback IP addresses.
 *
 * @author  AO Industries, Inc.
 */
public class AOServDaemonPortMonitor extends PortMonitor {

    private final AOServer aoServer;
    private final String netProtocol;
    private final String appProtocol;
    private final Map<String,String> monitoringParameters;

    public AOServDaemonPortMonitor(AOServer aoServer, String ipAddress, int port, String netProtocol, String appProtocol, Map<String,String> monitoringParameters) {
        super(ipAddress, port);
        this.aoServer = aoServer;
        this.netProtocol = netProtocol;
        this.appProtocol = appProtocol;
        this.monitoringParameters = monitoringParameters;
    }

    @Override
    public String checkPort() throws Exception {
        return aoServer.checkPort(ipAddress, port, netProtocol, appProtocol, monitoringParameters);
    }
}
