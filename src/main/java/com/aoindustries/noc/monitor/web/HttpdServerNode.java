/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2018, 2019, 2020  AO Industries, Inc.
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
package com.aoindustries.noc.monitor.web;

import com.aoindustries.aoserv.client.web.HttpdServer;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.TableMultiResultNodeImpl;
import com.aoindustries.noc.monitor.common.HttpdServerResult;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Arrays;
import java.util.List;

/**
 * @author  AO Industries, Inc.
 */
public class HttpdServerNode extends TableMultiResultNodeImpl<HttpdServerResult> {

	private static final long serialVersionUID = 1L;

	private final HttpdServer _httpdServer;

	HttpdServerNode(HttpdServersNode httpdServersNode, HttpdServer httpdServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
		super(
			httpdServersNode.hostNode.hostsNode.rootNode,
			httpdServersNode,
			HttpdServerNodeWorker.getWorker(
				new File(httpdServersNode.getPersistenceDirectory(), Integer.toString(httpdServer.getPkey())),
				httpdServer
			),
			port,
			csf,
			ssf
		);
		this._httpdServer = httpdServer;
	}

	public HttpdServer getHttpdServer() {
		return _httpdServer;
	}

	@Override
	public String getLabel() {
		String name = _httpdServer.getName();
		if(name == null) {
			return accessor.getMessage(rootNode.locale, "HttpdServerNode.label.noName");
		} else {
			return accessor.getMessage(rootNode.locale, "HttpdServerNode.label.named", name);
		}
	}

	@Override
	public List<String> getColumnHeaders() {
		return Arrays.asList(
			accessor.getMessage(rootNode.locale, "HttpdServerNode.columnHeader.concurrency"),
			accessor.getMessage(rootNode.locale, "HttpdServerNode.columnHeader.maxConcurrency"),
			accessor.getMessage(rootNode.locale, "HttpdServerNode.columnHeader.alertThresholds")
		);
	}
}
