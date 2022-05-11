/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2016, 2018, 2019, 2020, 2022  AO Industries, Inc.
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

import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;

import com.aoindustries.noc.monitor.SingleResultNodeImpl;
import com.aoindustries.noc.monitor.linux.RaidNode;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;

/**
 * The node for the 3ware monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class ThreeWareRaidNode extends SingleResultNodeImpl {

  private static final long serialVersionUID = 1L;

  public ThreeWareRaidNode(RaidNode raidNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
    super(
        raidNode.hostNode.hostsNode.rootNode,
        raidNode,
        ThreeWareRaidNodeWorker.getWorker(
            new File(raidNode.getPersistenceDirectory(), "3ware"),
            raidNode.getServer()
        ),
        port,
        csf,
        ssf
    );
  }

  @Override
  public String getLabel() {
    return PACKAGE_RESOURCES.getMessage(rootNode.locale, "ThreeWareRaidNode.label");
  }
}
