/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2012, 2016, 2018, 2019, 2020, 2022  AO Industries, Inc.
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
package com.aoindustries.noc.monitor.infrastructure;

import com.aoindustries.aoserv.client.linux.Server;
import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;
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
	}

	@Override
	public String getLabel() {
		return PACKAGE_RESOURCES.getMessage(rootNode.locale, "UpsNode.label");
	}

	@Override
	public List<?> getColumnHeaders() {
		return Arrays.asList(PACKAGE_RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.upsname"),
			PACKAGE_RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.status"),
			PACKAGE_RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.linev"),
			PACKAGE_RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.outputv"),
			PACKAGE_RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.loadpct"),
			PACKAGE_RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.bcharge"),
			PACKAGE_RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.battv"),
			PACKAGE_RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.badbatts"),
			PACKAGE_RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.tonbatt"),
			PACKAGE_RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.cumonbatt"),
			PACKAGE_RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.timeleft"),
			PACKAGE_RESOURCES.getMessage(rootNode.locale, "UpsNode.columnHeader.itemp")
		);
	}
}
