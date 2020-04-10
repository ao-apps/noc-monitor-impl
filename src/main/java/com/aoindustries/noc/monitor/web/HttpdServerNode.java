/*
 * Copyright 2018, 2019 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
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
