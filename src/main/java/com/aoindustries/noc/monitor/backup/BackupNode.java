/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2016, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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

package com.aoindustries.noc.monitor.backup;

import com.aoindustries.aoserv.client.backup.BackupPartition;
import com.aoindustries.aoserv.client.backup.FileReplication;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;
import com.aoindustries.noc.monitor.TableResultNodeImpl;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TableResult;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;

/**
 * The node for the backup monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class BackupNode extends TableResultNodeImpl {

  private static final long serialVersionUID = 1L;

  private final FileReplication failoverFileReplication;
  private final String label;

  BackupNode(BackupsNode backupsNode, FileReplication failoverFileReplication, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException, SQLException {
    super(
        backupsNode.hostNode.hostsNode.rootNode,
        backupsNode,
        BackupNodeWorker.getWorker(
            new File(backupsNode.getPersistenceDirectory(), Integer.toString(failoverFileReplication.getPkey())),
            failoverFileReplication
        ),
        port,
        csf,
        ssf
    );
    this.failoverFileReplication = failoverFileReplication;
    BackupPartition backupPartition = failoverFileReplication.getBackupPartition();
    this.label = PACKAGE_RESOURCES.getMessage(
        rootNode.locale,
        "BackupNode.label",
        backupPartition == null ? "null" : backupPartition.getLinuxServer().getHostname(),
        backupPartition == null ? "null" : backupPartition.getPath()
    );
  }

  FileReplication getFailoverFileReplication() {
    return failoverFileReplication;
  }

  @Override
  public String getLabel() {
    return label;
  }

  AlertLevelAndMessage getAlertLevelAndMessage(TableResult result) {
    AlertLevel curAlertLevel = worker.getAlertLevel();
    if (curAlertLevel == null) {
      curAlertLevel = AlertLevel.NONE;
    }
    return worker.getAlertLevelAndMessage(
        curAlertLevel,
        result
    );
  }
}
