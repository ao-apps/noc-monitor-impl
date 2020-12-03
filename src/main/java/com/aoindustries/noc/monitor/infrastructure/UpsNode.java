/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2012, 2016, 2018, 2019, 2020  AO Industries, Inc.
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
 * along with noc-monitor-impl.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.noc.monitor.infrastructure;

import com.aoindustries.aoserv.client.linux.Server;
import static com.aoindustries.noc.monitor.Resources.RESOURCES;
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
		return RESOURCES.getMessage(rootNode.locale, "UpsNode.label");
	}

	@Override
	public List<?> getColumnHeaders() {
		return Arrays.asList(
			RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.upsname"),
			RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.status"),
			RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.linev"),
			RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.outputv"),
			RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.loadpct"),
			RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.bcharge"),
			RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.battv"),
			RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.badbatts"),
			RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.tonbatt"),
			RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.cumonbatt"),
			RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.timeleft"),
			RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.itemp")
		);
	}
}
