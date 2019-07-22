/*
 * Copyright 2008-2012, 2014, 2016, 2018, 2019 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.linux;

import com.aoindustries.aoserv.client.linux.Server;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.TableMultiResultNodeImpl;
import com.aoindustries.noc.monitor.common.LoadAverageResult;
import com.aoindustries.noc.monitor.net.HostNode;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Arrays;
import java.util.List;

/**
 * The load average per ao_server is watched on a minutely basis.  The five-minute
 * load average is compared against the limits in the ao_servers table and the
 * alert level is set accordingly.
 *
 * @author  AO Industries, Inc.
 */
public class LoadAverageNode extends TableMultiResultNodeImpl<LoadAverageResult> {

	private static final long serialVersionUID = 1L;

	private final Server _linuxServer;

	public LoadAverageNode(HostNode hostNode, Server linuxServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			hostNode.hostsNode.rootNode,
			hostNode,
			LoadAverageNodeWorker.getWorker(
				hostNode.getPersistenceDirectory(),
				linuxServer
			),
			port,
			csf,
			ssf
		);
		this._linuxServer = linuxServer;
	}

	@Override
	public String getLabel() {
		return accessor.getMessage(rootNode.locale, "LoadAverageNode.label");
	}

	@Override
	public List<String> getColumnHeaders() {
		return Arrays.asList(
			accessor.getMessage(rootNode.locale, "LoadAverageNode.columnHeader.oneMinute"),
			accessor.getMessage(rootNode.locale, "LoadAverageNode.columnHeader.fiveMinute"),
			accessor.getMessage(rootNode.locale, "LoadAverageNode.columnHeader.tenMinute"),
			accessor.getMessage(rootNode.locale, "LoadAverageNode.columnHeader.runningProcesses"),
			accessor.getMessage(rootNode.locale, "LoadAverageNode.columnHeader.totalProcesses"),
			accessor.getMessage(rootNode.locale, "LoadAverageNode.columnHeader.lastPID"),
			accessor.getMessage(rootNode.locale, "LoadAverageNode.columnHeader.alertThresholds")
		);
	}
}
