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

package com.aoindustries.noc.monitor.net;

import com.aoapps.lang.i18n.Resources;
import com.aoindustries.noc.monitor.SingleResultNodeImpl;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.ResourceBundle;

/**
 * The node for the bonding monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class DeviceBondingNode extends SingleResultNodeImpl {

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, DeviceBondingNode.class);

  private static final long serialVersionUID = 1L;

  DeviceBondingNode(DeviceNode deviceNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
    super(
        deviceNode.devicesNode.hostNode.hostsNode.rootNode,
        deviceNode,
        DeviceBondingWorker.getWorker(
            new File(deviceNode.getPersistenceDirectory(), "bonding"),
            deviceNode.getDevice()
        ),
        port,
        csf,
        ssf
    );
  }

  @Override
  public String getLabel() {
    return RESOURCES.getMessage(rootNode.locale, "label");
  }
}
