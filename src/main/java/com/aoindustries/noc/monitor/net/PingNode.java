/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008-2012, 2014, 2016, 2018, 2020  AO Industries, Inc.
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
import com.aoindustries.noc.monitor.common.PingResult;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Collections;
import java.util.List;

/**
 * The ping node per server.
 *
 * @author  AO Industries, Inc.
 */
public class PingNode extends TableMultiResultNodeImpl<PingResult> {

	private static final long serialVersionUID = 1L;

	//private final IpAddressNode ipAddressNode;

	PingNode(IpAddressNode ipAddressNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			ipAddressNode.ipAddressesNode.rootNode,
			ipAddressNode,
			PingNodeWorker.getWorker(
				ipAddressNode.getPersistenceDirectory(),
				ipAddressNode.getIpAddress()
			),
			port,
			csf,
			ssf
		);
		//this.ipAddressNode = ipAddressNode;
	}

	@Override
	public String getLabel() {
		return PACKAGE_RESOURCES.getMessage(rootNode.locale, "PingNode.label");
	}

	@Override
	public List<?> getColumnHeaders() {
		return Collections.emptyList();
	}
}
