/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2009-2012, 2014, 2016, 2017, 2018, 2020  AO Industries, Inc.
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
import com.aoindustries.noc.monitor.common.NetBindResult;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * The net bind monitor.
 *
 * @author  AO Industries, Inc.
 */
public class BindNode extends TableMultiResultNodeImpl<NetBindResult> {

	private static final long serialVersionUID = 1L;

	private final BindsNode.NetMonitorSetting netMonitorSetting;
	private final String label;

	BindNode(BindsNode netBindsNode, BindsNode.NetMonitorSetting netMonitorSetting, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException, IOException, SQLException {
		super(
			netBindsNode.ipAddressNode.ipAddressesNode.rootNode,
			netBindsNode,
			BindNodeWorker.getWorker(
				new File(
					netBindsNode.getPersistenceDirectory(),
					netMonitorSetting.getPort().getPort()+"_"+netMonitorSetting.getPort().getProtocol().name()
				),
				netMonitorSetting
			),
			port,
			csf,
			ssf
		);
		this.netMonitorSetting = netMonitorSetting;
		this.label = netMonitorSetting.getPort()+" ("+netMonitorSetting.getNetBind().getAppProtocol().getProtocol()+')';
	}

	BindsNode.NetMonitorSetting getNetMonitorSetting() {
		return netMonitorSetting;
	}

	@Override
	public String getLabel() {
		return label;
	}

	@Override
	public List<?> getColumnHeaders() {
		return Collections.singletonList(PACKAGE_RESOURCES.getMessage(rootNode.locale, "NetBindNode.columnHeader.result")
		);
	}
}
