/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2009-2012, 2016, 2018, 2020, 2021, 2022  AO Industries, Inc.
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
import com.aoapps.lang.sql.LocalizedSQLException;
import com.aoindustries.aoserv.client.backup.MysqlReplication;
import com.aoindustries.aoserv.client.mysql.Server;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import com.aoindustries.noc.monitor.TableMultiResultWorker;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.MysqlReplicationResult;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * @author  AO Industries, Inc.
 */
class SlaveStatusWorker extends TableMultiResultWorker<List<String>, MysqlReplicationResult> {

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, SlaveStatusWorker.class);

  /**
   * One unique worker is made per persistence directory (and should match mysqlReplication exactly).
   */
  private static final Map<String, SlaveStatusWorker> workerCache = new HashMap<>();

  static SlaveStatusWorker getWorker(File persistenceDirectory, MysqlReplication mysqlReplication) throws IOException {
    File persistenceFile = new File(persistenceDirectory, "slave_status");
    String path = persistenceFile.getCanonicalPath();
    synchronized (workerCache) {
      SlaveStatusWorker worker = workerCache.get(path);
      if (worker == null) {
        worker = new SlaveStatusWorker(persistenceFile, mysqlReplication);
        workerCache.put(path, worker);
      } else {
        if (!worker.originalMysqlReplication.equals(mysqlReplication)) {
          throw new AssertionError("worker.mysqlReplication != mysqlReplication: " + worker.originalMysqlReplication + " != " + mysqlReplication);
        }
      }
      return worker;
    }
  }

  private final MysqlReplication originalMysqlReplication;
  private MysqlReplication currentMysqlReplication;

  private SlaveStatusWorker(File persistenceFile, MysqlReplication mysqlReplication) throws IOException {
    super(persistenceFile, new ReplicationResultSerializer());
    this.originalMysqlReplication = currentMysqlReplication = mysqlReplication;
  }

  @Override
  protected int getHistorySize() {
    return 2000;
  }

  @Override
  protected List<String> getSample() throws Exception {
    // Get the latest values
    currentMysqlReplication = originalMysqlReplication.getTable().getConnector().getBackup().getMysqlReplication().get(originalMysqlReplication.getPkey());
    MysqlReplication.SlaveStatus slaveStatus = currentMysqlReplication.getSlaveStatus();
    if (slaveStatus == null) {
      throw new LocalizedSQLException("08006", RESOURCES, "slaveNotRunning");
    }
    Server.MasterStatus masterStatus = originalMysqlReplication.getMysqlServer().getMasterStatus();
    if (masterStatus == null) {
      throw new LocalizedSQLException("08006", RESOURCES, "masterNotRunning");
    }
    // Display the alert thresholds
    int secondsBehindLow = currentMysqlReplication.getMonitoringSecondsBehindLow();
    int secondsBehindMedium = currentMysqlReplication.getMonitoringSecondsBehindMedium();
    int secondsBehindHigh = currentMysqlReplication.getMonitoringSecondsBehindHigh();
    int secondsBehindCritical = currentMysqlReplication.getMonitoringSecondsBehindCritical();
    String alertThresholds =
        (secondsBehindLow == -1 ? "-" : Integer.toString(secondsBehindLow))
            + " / "
            + (secondsBehindMedium == -1 ? "-" : Integer.toString(secondsBehindMedium))
            + " / "
            + (secondsBehindHigh == -1 ? "-" : Integer.toString(secondsBehindHigh))
            + " / "
            + (secondsBehindCritical == -1 ? "-" : Integer.toString(secondsBehindCritical));

    return Arrays.asList(
        slaveStatus.getSecondsBehindMaster(),
        masterStatus.getFile(),
        masterStatus.getPosition(),
        slaveStatus.getSlaveIoState(),
        slaveStatus.getMasterLogFile(),
        slaveStatus.getReadMasterLogPos(),
        slaveStatus.getSlaveIoRunning(),
        slaveStatus.getSlaveSqlRunning(),
        slaveStatus.getLastErrno(),
        slaveStatus.getLastError(),
        alertThresholds
    );
  }

  @Override
  protected AlertLevelAndMessage getAlertLevelAndMessage(List<String> sample, Iterable<? extends MysqlReplicationResult> previousResults) throws Exception {
    String secondsBehindMaster = sample.get(0);
    if (secondsBehindMaster == null) {
      // Use the highest alert level that may be returned for this replication
      AlertLevel alertLevel;
      if (currentMysqlReplication.getMonitoringSecondsBehindCritical() != -1) {
        alertLevel = AlertLevel.CRITICAL;
      } else if (currentMysqlReplication.getMonitoringSecondsBehindHigh() != -1) {
        alertLevel = AlertLevel.HIGH;
      } else if (currentMysqlReplication.getMonitoringSecondsBehindMedium() != -1) {
        alertLevel = AlertLevel.MEDIUM;
      } else if (currentMysqlReplication.getMonitoringSecondsBehindLow() != -1) {
        alertLevel = AlertLevel.LOW;
      } else {
        alertLevel = AlertLevel.NONE;
      }

      return new AlertLevelAndMessage(
          alertLevel,
          locale -> RESOURCES.getMessage(
              locale,
              "alertMessage.secondsBehindMaster.null"
          )
      );
    }
    try {
      int secondsBehind = Integer.parseInt(secondsBehindMaster);
      int secondsBehindCritical = currentMysqlReplication.getMonitoringSecondsBehindCritical();
      if (secondsBehindCritical != -1 && secondsBehind >= secondsBehindCritical) {
        return new AlertLevelAndMessage(
            AlertLevel.CRITICAL,
            locale -> RESOURCES.getMessage(
                locale,
                "alertMessage.critical",
                secondsBehindCritical,
                secondsBehind
            )
        );
      }
      int secondsBehindHigh = currentMysqlReplication.getMonitoringSecondsBehindHigh();
      if (secondsBehindHigh != -1 && secondsBehind >= secondsBehindHigh) {
        return new AlertLevelAndMessage(
            AlertLevel.HIGH,
            locale -> RESOURCES.getMessage(
                locale,
                "alertMessage.high",
                secondsBehindHigh,
                secondsBehind
            )
        );
      }
      int secondsBehindMedium = currentMysqlReplication.getMonitoringSecondsBehindMedium();
      if (secondsBehindMedium != -1 && secondsBehind >= secondsBehindMedium) {
        return new AlertLevelAndMessage(
            AlertLevel.MEDIUM,
            locale -> RESOURCES.getMessage(
                locale,
                "alertMessage.medium",
                secondsBehindMedium,
                secondsBehind
            )
        );
      }
      int secondsBehindLow = currentMysqlReplication.getMonitoringSecondsBehindLow();
      if (secondsBehindLow != -1 && secondsBehind >= secondsBehindLow) {
        return new AlertLevelAndMessage(
            AlertLevel.LOW,
            locale -> RESOURCES.getMessage(
                locale,
                "alertMessage.low",
                secondsBehindLow,
                secondsBehind
            )
        );
      }
      if (secondsBehindLow == -1) {
        return new AlertLevelAndMessage(
            AlertLevel.NONE,
            locale -> RESOURCES.getMessage(
                locale,
                "alertMessage.notAny",
                secondsBehind
            )
        );
      } else {
        return new AlertLevelAndMessage(
            AlertLevel.NONE,
            locale -> RESOURCES.getMessage(
                locale,
                "alertMessage.none",
                secondsBehindLow,
                secondsBehind
            )
        );
      }
    } catch (NumberFormatException err) {
      return new AlertLevelAndMessage(
          AlertLevel.CRITICAL,
          locale -> RESOURCES.getMessage(
              locale,
              "alertMessage.secondsBehindMaster.invalid",
              secondsBehindMaster
          )
      );
    }
  }

  @Override
  protected MysqlReplicationResult newErrorResult(long time, long latency, AlertLevel alertLevel, String error) {
    return new MysqlReplicationResult(time, latency, alertLevel, error);
  }

  @Override
  protected MysqlReplicationResult newSampleResult(long time, long latency, AlertLevel alertLevel, List<String> sample) {
    return new MysqlReplicationResult(
        time,
        latency,
        alertLevel,
        sample.get(0),
        sample.get(1),
        sample.get(2),
        sample.get(3),
        sample.get(4),
        sample.get(5),
        sample.get(6),
        sample.get(7),
        sample.get(8),
        sample.get(9),
        sample.get(10)
    );
  }
}
