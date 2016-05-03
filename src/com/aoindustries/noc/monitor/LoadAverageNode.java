/*
 * Copyright 2008-2012, 2014, 2016 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.LoadAverageResult;
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

	private final AOServer _aoServer;

	LoadAverageNode(ServerNode serverNode, AOServer aoServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			serverNode.serversNode.rootNode,
			serverNode,
			LoadAverageNodeWorker.getWorker(
				serverNode.getPersistenceDirectory(),
				aoServer
			),
			port,
			csf,
			ssf
		);
		this._aoServer = aoServer;
	}

	@Override
	public String getLabel() {
		return accessor.getMessage(/*rootNode.locale,*/ "LoadAverageNode.label");
	}

	@Override
	public List<String> getColumnHeaders(/*Locale locale*/) {
		return Arrays.asList(
			accessor.getMessage(/*locale,*/ "LoadAverageNode.columnHeader.oneMinute"),
			accessor.getMessage(/*locale,*/ "LoadAverageNode.columnHeader.fiveMinute"),
			accessor.getMessage(/*locale,*/ "LoadAverageNode.columnHeader.tenMinute"),
			accessor.getMessage(/*locale,*/ "LoadAverageNode.columnHeader.runningProcesses"),
			accessor.getMessage(/*locale,*/ "LoadAverageNode.columnHeader.totalProcesses"),
			accessor.getMessage(/*locale,*/ "LoadAverageNode.columnHeader.lastPID"),
			accessor.getMessage(/*locale,*/ "LoadAverageNode.columnHeader.alertThresholds")
		);
	}
}
