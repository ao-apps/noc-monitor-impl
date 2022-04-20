/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2014, 2016, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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

package com.aoindustries.noc.monitor.linux;

import com.aoapps.lang.function.SerializableFunction;
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.aoserv.client.linux.Server.MdMismatchReport;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;
import com.aoindustries.noc.monitor.TableResultNodeWorker;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TableResult;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The workers for MD mismatch monitoring.
 *
 * @author  AO Industries, Inc.
 */
class MdMismatchWorker extends TableResultNodeWorker<List<MdMismatchReport>, String> {

  private static final int RAID1_HIGH_THRESHOLD = 2048;
  private static final int RAID1_MEDIUM_THRESHOLD = 1024;
  private static final int RAID1_LOW_THRESHOLD = 1;

  /**
   * One unique worker is made per persistence file (and should match the linuxServer exactly)
   */
  private static final Map<String, MdMismatchWorker> workerCache = new HashMap<>();
  static MdMismatchWorker getWorker(File persistenceFile, Server linuxServer) throws IOException {
    String path = persistenceFile.getCanonicalPath();
    synchronized (workerCache) {
      MdMismatchWorker worker = workerCache.get(path);
      if (worker == null) {
        worker = new MdMismatchWorker(persistenceFile, linuxServer);
        workerCache.put(path, worker);
      } else {
        if (!worker.linuxServer.equals(linuxServer)) {
          throw new AssertionError("worker.linuxServer != linuxServer: "+worker.linuxServer+" != "+linuxServer);
        }
      }
      return worker;
    }
  }

  // Will use whichever connector first created this worker, even if other accounts connect later.
  private final Server linuxServer;

  MdMismatchWorker(File persistenceFile, Server linuxServer) {
    super(persistenceFile);
    this.linuxServer = linuxServer;
  }

  /**
   * Determines the alert message for the provided result.
   */
  @Override
  public AlertLevelAndMessage getAlertLevelAndMessage(AlertLevel curAlertLevel, TableResult result) {
    AlertLevel highestAlertLevel = AlertLevel.NONE;
    Function<Locale, String> highestAlertMessage = null;
    if (result.isError()) {
      highestAlertLevel = result.getAlertLevels().get(0);
      highestAlertMessage = locale -> result.getTableData(locale).get(0).toString();
    } else {
      List<?> tableData = result.getTableData(Locale.getDefault());
      List<AlertLevel> alertLevels = result.getAlertLevels();
      for (
        int index=0, len=tableData.size();
        index < len;
        index += 3
      ) {
        AlertLevel alertLevel = alertLevels.get(index / 3);
        if (alertLevel.compareTo(highestAlertLevel)>0) {
          highestAlertLevel = alertLevel;
          Object device = tableData.get(index);
          Object level = tableData.get(index+1);
          Object count = tableData.get(index+2);
          highestAlertMessage = locale -> device + " " + level + " " + count;
        }
      }
    }
    return new AlertLevelAndMessage(highestAlertLevel, highestAlertMessage);
  }

  @Override
  protected int getColumns() {
    return 3;
  }

  @Override
  protected SerializableFunction<Locale, List<String>> getColumnHeaders() {
    return locale -> Arrays.asList(PACKAGE_RESOURCES.getMessage(locale, "MdMismatchWorker.columnHeader.device"),
      PACKAGE_RESOURCES.getMessage(locale, "MdMismatchWorker.columnHeader.level"),
      PACKAGE_RESOURCES.getMessage(locale, "MdMismatchWorker.columnHeader.count")
    );
  }

  @Override
  protected List<MdMismatchReport> getQueryResult() throws Exception {
    return linuxServer.getMdMismatchReport();
  }

  @Override
  protected SerializableFunction<Locale, List<String>> getTableData(List<MdMismatchReport> reports) throws Exception {
    List<String> tableData = new ArrayList<>(reports.size() * 3);
    for (MdMismatchReport report : reports) {
      tableData.add(report.getDevice());
      tableData.add(report.getLevel().name());
      tableData.add(Long.toString(report.getCount()));
    }
    return locale -> tableData;
  }

  @Override
  protected List<AlertLevel> getAlertLevels(List<MdMismatchReport> reports) {
    List<AlertLevel> alertLevels = new ArrayList<>(reports.size());
    for (MdMismatchReport report : reports) {
      long count = report.getCount();
      final AlertLevel alertLevel;
      if (count == 0) {
        alertLevel = AlertLevel.NONE;
      } else {
        if (report.getLevel() == Server.RaidLevel.raid1) {
          // Allow small amount of mismatch for RAID1 only
          alertLevel =
            count >= RAID1_HIGH_THRESHOLD ? AlertLevel.HIGH
            : count >= RAID1_MEDIUM_THRESHOLD ? AlertLevel.MEDIUM
            : count >= RAID1_LOW_THRESHOLD ? AlertLevel.LOW
            : AlertLevel.NONE
          ;
        } else {
          // All other types allow no mismatch
          alertLevel = AlertLevel.HIGH;
        }
      }
      alertLevels.add(alertLevel);
    }
    return alertLevels;
  }
}
