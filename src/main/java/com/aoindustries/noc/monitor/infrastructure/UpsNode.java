/*
 * Copyright 2012, 2016, 2018, 2019, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.infrastructure;

import com.aoindustries.aoserv.client.linux.Server;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.TableMultiResultNodeImpl;
import com.aoindustries.noc.monitor.common.UpsResult;
import com.aoindustries.noc.monitor.net.HostNode;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Arrays;
import java.util.List;

/**
 * Monitors UPS status for an Server.
 *
 * @author  AO Industries, Inc.
 */
public class UpsNode extends TableMultiResultNodeImpl<UpsResult> {

	private static final long serialVersionUID = 1L;

	private final Server _linuxServer;

	public UpsNode(HostNode hostNode, Server linuxServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			hostNode.hostsNode.rootNode,
			hostNode,
			UpsNodeWorker.getWorker(
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
		return accessor.getMessage(rootNode.locale, "UpsNode.label");
	}

	@Override
	public List<?> getColumnHeaders() {
		return Arrays.asList(
			accessor.getMessage(rootNode.locale, "UpsNode.columnHeader.upsname"),
			accessor.getMessage(rootNode.locale, "UpsNode.columnHeader.status"),
			accessor.getMessage(rootNode.locale, "UpsNode.columnHeader.linev"),
			accessor.getMessage(rootNode.locale, "UpsNode.columnHeader.outputv"),
			accessor.getMessage(rootNode.locale, "UpsNode.columnHeader.loadpct"),
			accessor.getMessage(rootNode.locale, "UpsNode.columnHeader.bcharge"),
			accessor.getMessage(rootNode.locale, "UpsNode.columnHeader.battv"),
			accessor.getMessage(rootNode.locale, "UpsNode.columnHeader.badbatts"),
			accessor.getMessage(rootNode.locale, "UpsNode.columnHeader.tonbatt"),
			accessor.getMessage(rootNode.locale, "UpsNode.columnHeader.cumonbatt"),
			accessor.getMessage(rootNode.locale, "UpsNode.columnHeader.timeleft"),
			accessor.getMessage(rootNode.locale, "UpsNode.columnHeader.itemp")
		);
	}
}
