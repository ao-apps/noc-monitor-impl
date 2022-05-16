/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2009-2012, 2016, 2018, 2019, 2020, 2022  AO Industries, Inc.
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

package com.aoindustries.noc.monitor.mysql;

import com.aoapps.lang.i18n.Resources;
import com.aoindustries.noc.monitor.TableMultiResultNodeImpl;
import com.aoindustries.noc.monitor.common.MysqlReplicationResult;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

/**
 * The replication status per MysqlReplication.
 *
 * @author  AO Industries, Inc.
 */
public class SlaveStatusNode extends TableMultiResultNodeImpl<MysqlReplicationResult> {

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, SlaveStatusNode.class);

  private static final long serialVersionUID = 1L;

  SlaveStatusNode(SlaveNode slaveNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
    super(
        slaveNode.slavesNode.serverNode.serversNode.hostNode.hostsNode.rootNode,
        slaveNode,
        SlaveStatusWorker.getWorker(
            slaveNode.getPersistenceDirectory(),
            slaveNode.getMysqlReplication()
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

  @Override
  public List<String> getColumnHeaders() {
    return Arrays.asList(RESOURCES.getMessage(rootNode.locale, "columnHeader.secondsBehindMaster"),
        RESOURCES.getMessage(rootNode.locale, "columnHeader.masterLogFile"),
        RESOURCES.getMessage(rootNode.locale, "columnHeader.masterLogPosition"),
        RESOURCES.getMessage(rootNode.locale, "columnHeader.slaveIoState"),
        RESOURCES.getMessage(rootNode.locale, "columnHeader.slaveLogFile"),
        RESOURCES.getMessage(rootNode.locale, "columnHeader.slaveLogPosition"),
        RESOURCES.getMessage(rootNode.locale, "columnHeader.slaveIoRunning"),
        RESOURCES.getMessage(rootNode.locale, "columnHeader.slaveSqlRunning"),
        RESOURCES.getMessage(rootNode.locale, "columnHeader.lastErrorNumber"),
        RESOURCES.getMessage(rootNode.locale, "columnHeader.lastErrorDetails"),
        RESOURCES.getMessage(rootNode.locale, "columnHeader.alertThresholds")
    );
  }
}
