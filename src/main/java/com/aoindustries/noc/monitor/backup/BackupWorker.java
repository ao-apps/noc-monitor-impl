/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2016, 2018, 2019, 2020, 2021, 2022, 2024  AO Industries, Inc.
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

import com.aoapps.lang.Strings;
import com.aoapps.lang.function.SerializableFunction;
import com.aoapps.lang.i18n.Resources;
import com.aoindustries.aoserv.client.backup.FileReplication;
import com.aoindustries.aoserv.client.backup.FileReplicationLog;
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.aoserv.client.net.Host;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import com.aoindustries.noc.monitor.TableResultWorker;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TableResult;
import com.aoindustries.noc.monitor.common.TimeWithTimeZone;
import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TimeZone;
import java.util.function.Function;

/**
 * The workers for 3ware RAID.
 *
 * @author  AO Industries, Inc.
 */
class BackupWorker extends TableResultWorker<List<FileReplicationLog>, Object> {

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, BackupWorker.class);

  private static final int HISTORY_SIZE = 100;

  /**
   * One unique worker is made per persistence file (and should match the fileReplication exactly).
   */
  private static final Map<String, BackupWorker> workerCache = new HashMap<>();

  static BackupWorker getWorker(File persistenceFile, FileReplication fileReplication) throws IOException {
    String path = persistenceFile.getCanonicalPath();
    synchronized (workerCache) {
      BackupWorker worker = workerCache.get(path);
      if (worker == null) {
        worker = new BackupWorker(persistenceFile, fileReplication);
        workerCache.put(path, worker);
      } else {
        if (!worker.fileReplication.equals(fileReplication)) {
          throw new AssertionError("worker.fileReplication != fileReplication: " + worker.fileReplication + " != " + fileReplication);
        }
      }
      return worker;
    }
  }

  // Will use whichever connector first created this worker, even if other accounts connect later.
  private final FileReplication fileReplication;

  BackupWorker(File persistenceFile, FileReplication fileReplication) {
    super(persistenceFile);
    this.fileReplication = fileReplication;
  }

  /**
   * Determines the alert message for the provided result.
   *
   * <p>If there is not any data (no backups logged, make high level)</p>
   */
  @Override
  public AlertLevelAndMessage getAlertLevelAndMessage(AlertLevel curAlertLevel, TableResult result) {
    AlertLevel highestAlertLevel;
    Function<Locale, String> highestAlertMessage;
    if (result.isError()) {
      highestAlertLevel = result.getAlertLevels().get(0);
      highestAlertMessage = locale -> result.getTableData(locale).get(0).toString();
    } else {
      List<?> tableData = result.getTableData(Locale.getDefault());
      if (tableData.isEmpty()) {
        highestAlertLevel = AlertLevel.MEDIUM;
        highestAlertMessage = locale -> RESOURCES.getMessage(locale, "noBackupPassesLogged");
      } else {
        // We try to find the most recent successful pass
        // If <30 hours NONE
        // if <48 hours LOW
        // otherwise MEDIUM
        long lastSuccessfulTime = -1;
        for (int index = 0, len = tableData.size(); index < len; index += 6) {
          boolean successful = (Boolean) tableData.get(index + 5);
          if (successful) {
            lastSuccessfulTime = ((TimeWithTimeZone) tableData.get(index)).getTime();
            break;
          }
        }
        if (lastSuccessfulTime == -1) {
          // No success found, is MEDIUM
          highestAlertLevel = AlertLevel.MEDIUM;
          highestAlertMessage = locale -> RESOURCES.getMessage(locale, "noSuccessfulPassesFound", result.getRows());
        } else {
          long hoursSince = (System.currentTimeMillis() - lastSuccessfulTime) / (60L * 60 * 1000);
          if (hoursSince < 0) {
            highestAlertLevel = AlertLevel.CRITICAL;
            highestAlertMessage = locale -> RESOURCES.getMessage(locale, "lastSuccessfulPassInFuture");
          } else {
            if (hoursSince < 30) {
              highestAlertLevel = AlertLevel.NONE;
            } else if (hoursSince < 48) {
              highestAlertLevel = AlertLevel.LOW;
            } else {
              highestAlertLevel = AlertLevel.MEDIUM;
            }
            if (hoursSince <= 48) {
              highestAlertMessage = locale -> RESOURCES.getMessage(locale, "lastSuccessfulPass", hoursSince);
            } else {
              long days = hoursSince / 24;
              long hours = hoursSince % 24;
              highestAlertMessage = locale -> RESOURCES.getMessage(locale, "lastSuccessfulPassDays", days, hours);
            }
          }
        }
        // We next see if the last pass failed - if so this will be considered low priority (higher priority is time-based above)
        boolean lastSuccessful = (Boolean) tableData.get(5);
        if (!lastSuccessful) {
          if (AlertLevel.LOW.compareTo(highestAlertLevel) > 0) {
            highestAlertLevel = AlertLevel.LOW;
            highestAlertMessage = locale -> RESOURCES.getMessage(locale, "lastPassNotSuccessful");
          }
        }
      }
    }
    return new AlertLevelAndMessage(highestAlertLevel, highestAlertMessage);
  }

  @Override
  protected int getColumns() {
    return 6;
  }

  @Override
  protected SerializableFunction<Locale, List<String>> getColumnHeaders() {
    return locale -> Arrays.asList(RESOURCES.getMessage(locale, "columnHeader.startTime"),
        RESOURCES.getMessage(locale, "columnHeader.duration"),
        RESOURCES.getMessage(locale, "columnHeader.scanned"),
        RESOURCES.getMessage(locale, "columnHeader.updated"),
        RESOURCES.getMessage(locale, "columnHeader.bytes"),
        RESOURCES.getMessage(locale, "columnHeader.successful")
    );
  }

  @Override
  protected List<FileReplicationLog> getQueryResult() throws Exception {
    return fileReplication.getFailoverFileLogs(HISTORY_SIZE);
  }

  @Override
  protected SerializableFunction<Locale, List<Object>> getTableData(List<FileReplicationLog> failoverFileLogs) throws Exception {
    if (failoverFileLogs.isEmpty()) {
      return locale -> Collections.emptyList();
    } else {
      Host host = fileReplication.getHost();
      Server linuxServer = host.getLinuxServer();
      TimeZone timeZone = linuxServer == null ? null : linuxServer.getTimeZone().getTimeZone();
      List<Object> tableData = new ArrayList<>(failoverFileLogs.size() * 6);
      //int lineNum = 0;
      for (FileReplicationLog failoverFileLog : failoverFileLogs) {
        //lineNum++;
        Timestamp startTime = failoverFileLog.getStartTime();
        tableData.add(new TimeWithTimeZone(startTime.getTime(), timeZone));
        tableData.add(Strings.getTimeLengthString(failoverFileLog.getEndTime().getTime() - startTime.getTime()));
        tableData.add(failoverFileLog.getScanned());
        tableData.add(failoverFileLog.getUpdated());
        tableData.add(Strings.getApproximateSize(failoverFileLog.getBytes()));
        tableData.add(failoverFileLog.isSuccessful());
      }
      return locale -> tableData;
    }
  }

  @Override
  protected List<AlertLevel> getAlertLevels(List<FileReplicationLog> queryResult) {
    List<AlertLevel> alertLevels = new ArrayList<>(queryResult.size());
    for (FileReplicationLog failoverFileLog : queryResult) {
      // If pass failed then it is HIGH, otherwise it is NONE
      alertLevels.add(failoverFileLog.isSuccessful() ? AlertLevel.NONE : AlertLevel.MEDIUM);
    }
    return alertLevels;
  }
}
