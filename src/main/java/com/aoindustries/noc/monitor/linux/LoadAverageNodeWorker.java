/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008-2012, 2016, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;
import com.aoindustries.noc.monitor.TableMultiResultNodeWorker;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.LoadAverageResult;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author  AO Industries, Inc.
 */
class LoadAverageNodeWorker extends TableMultiResultNodeWorker<List<Number>, LoadAverageResult> {

  /**
   * One unique worker is made per persistence directory (and should match linuxServer exactly)
   */
  private static final Map<String, LoadAverageNodeWorker> workerCache = new HashMap<>();

  static LoadAverageNodeWorker getWorker(File persistenceDirectory, Server linuxServer) throws IOException {
    String path = persistenceDirectory.getCanonicalPath();
    synchronized (workerCache) {
      LoadAverageNodeWorker worker = workerCache.get(path);
      if (worker == null) {
        worker = new LoadAverageNodeWorker(persistenceDirectory, linuxServer);
        workerCache.put(path, worker);
      } else {
        if (!worker._linuxServer.equals(linuxServer)) {
          throw new AssertionError("worker.linuxServer != linuxServer: " + worker._linuxServer + " != " + linuxServer);
        }
      }
      return worker;
    }
  }

  private final Server _linuxServer;
  private Server currentAOServer;

  private LoadAverageNodeWorker(File persistenceDirectory, Server linuxServer) throws IOException {
    super(new File(persistenceDirectory, "loadavg"), new LoadAverageResultSerializer());
    this._linuxServer = currentAOServer = linuxServer;
  }

  @Override
  protected int getHistorySize() {
    return 2000;
  }

  @Override
  protected List<Number> getSample() throws Exception {
    // Get the latest limits
    currentAOServer = _linuxServer.getTable().getConnector().getLinux().getServer().get(_linuxServer.getPkey());
    String loadavg = currentAOServer.getLoadAvgReport();
    int pos1 = loadavg.indexOf(' ');
    if (pos1 == -1) {
      throw new ParseException("Unable to find first space in loadavg", 0);
    }
    int pos2 = loadavg.indexOf(' ', pos1 + 1);
    if (pos2 == -1) {
      throw new ParseException("Unable to find second space in loadavg", pos1 + 1);
    }
    int pos3 = loadavg.indexOf(' ', pos2 + 1);
    if (pos3 == -1) {
      throw new ParseException("Unable to find third space in loadavg", pos2 + 1);
    }
    int pos4 = loadavg.indexOf('/', pos3 + 1);
    if (pos4 == -1) {
      throw new ParseException("Unable to find slash in loadavg", pos3 + 1);
    }
    int pos5 = loadavg.indexOf(' ', pos4 + 1);
    if (pos5 == -1) {
      throw new ParseException("Unable to find fourth space in loadavg", pos4 + 1);
    }
    return Arrays.asList(
        Float.parseFloat(loadavg.substring(0, pos1)),
        Float.parseFloat(loadavg.substring(pos1 + 1, pos2)),
        Float.parseFloat(loadavg.substring(pos2 + 1, pos3)),
        Integer.parseInt(loadavg.substring(pos3 + 1, pos4)),
        Integer.parseInt(loadavg.substring(pos4 + 1, pos5)),
        Integer.parseInt(loadavg.substring(pos5 + 1).trim()),
        currentAOServer.getMonitoringLoadLow(),
        currentAOServer.getMonitoringLoadMedium(),
        currentAOServer.getMonitoringLoadHigh(),
        currentAOServer.getMonitoringLoadCritical()
    );
  }

  @Override
  protected AlertLevelAndMessage getAlertLevelAndMessage(List<Number> sample, Iterable<? extends LoadAverageResult> previousResults) throws Exception {
    float fiveMinuteAverage = (Float) sample.get(1);
    float loadCritical = currentAOServer.getMonitoringLoadCritical();
    if (!Float.isNaN(loadCritical) && fiveMinuteAverage >= loadCritical) {
      return new AlertLevelAndMessage(
          AlertLevel.CRITICAL,
          locale -> PACKAGE_RESOURCES.getMessage(
              locale,
              "LoadAverageNodeWorker.alertMessage.critical",
              loadCritical,
              fiveMinuteAverage
          )
      );
    }
    float loadHigh = currentAOServer.getMonitoringLoadHigh();
    if (!Float.isNaN(loadHigh) && fiveMinuteAverage >= loadHigh) {
      return new AlertLevelAndMessage(
          AlertLevel.HIGH,
          locale -> PACKAGE_RESOURCES.getMessage(
              locale,
              "LoadAverageNodeWorker.alertMessage.high",
              loadHigh,
              fiveMinuteAverage
          )
      );
    }
    float loadMedium = currentAOServer.getMonitoringLoadMedium();
    if (!Float.isNaN(loadMedium) && fiveMinuteAverage >= loadMedium) {
      return new AlertLevelAndMessage(
          AlertLevel.MEDIUM,
          locale -> PACKAGE_RESOURCES.getMessage(
              locale,
              "LoadAverageNodeWorker.alertMessage.medium",
              loadMedium,
              fiveMinuteAverage
          )
      );
    }
    float loadLow = currentAOServer.getMonitoringLoadLow();
    if (!Float.isNaN(loadLow) && fiveMinuteAverage >= loadLow) {
      return new AlertLevelAndMessage(
          AlertLevel.LOW,
          locale -> PACKAGE_RESOURCES.getMessage(
              locale,
              "LoadAverageNodeWorker.alertMessage.low",
              loadLow,
              fiveMinuteAverage
          )
      );
    }
    if (Float.isNaN(loadLow)) {
      return new AlertLevelAndMessage(
          AlertLevel.NONE,
          locale -> PACKAGE_RESOURCES.getMessage(
              locale,
              "LoadAverageNodeWorker.alertMessage.notAny",
              fiveMinuteAverage
          )
      );
    } else {
      return new AlertLevelAndMessage(
          AlertLevel.NONE,
          locale -> PACKAGE_RESOURCES.getMessage(
              locale,
              "LoadAverageNodeWorker.alertMessage.none",
              loadLow,
              fiveMinuteAverage
          )
      );
    }
  }

  @Override
  protected LoadAverageResult newErrorResult(long time, long latency, AlertLevel alertLevel, String error) {
    return new LoadAverageResult(time, latency, alertLevel, error);
  }

  @Override
  protected LoadAverageResult newSampleResult(long time, long latency, AlertLevel alertLevel, List<Number> sample) {
    return new LoadAverageResult(
        time,
        latency,
        alertLevel,
        (Float) sample.get(0),
        (Float) sample.get(1),
        (Float) sample.get(2),
        (Integer) sample.get(3),
        (Integer) sample.get(4),
        (Integer) sample.get(5),
        (Float) sample.get(6),
        (Float) sample.get(7),
        (Float) sample.get(8),
        (Float) sample.get(9)
    );
  }
}
