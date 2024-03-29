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

import com.aoapps.lang.i18n.Resources;
import com.aoindustries.aoserv.client.backup.BackupPartition;
import com.aoindustries.aoserv.client.backup.FileReplication;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import com.aoindustries.noc.monitor.TableResultNodeImpl;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TableResult;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.ResourceBundle;

/**
 * The node for the backup monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class BackupNode extends TableResultNodeImpl {

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, BackupNode.class);

  private static final long serialVersionUID = 1L;

  private final FileReplication fileReplication;
  private final String label;

  BackupNode(BackupsNode backupsNode, FileReplication fileReplication, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException, SQLException {
    super(
        backupsNode.hostNode.hostsNode.rootNode,
        backupsNode,
        BackupWorker.getWorker(
            new File(backupsNode.getPersistenceDirectory(), Integer.toString(fileReplication.getPkey())),
            fileReplication
        ),
        port,
        csf,
        ssf
    );
    this.fileReplication = fileReplication;
    BackupPartition backupPartition = fileReplication.getBackupPartition();
    this.label = RESOURCES.getMessage(
        rootNode.locale,
        "label",
        backupPartition == null ? "null" : backupPartition.getLinuxServer().getHostname(),
        backupPartition == null ? "null" : backupPartition.getPath()
    );
  }

  FileReplication getFileReplication() {
    return fileReplication;
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
