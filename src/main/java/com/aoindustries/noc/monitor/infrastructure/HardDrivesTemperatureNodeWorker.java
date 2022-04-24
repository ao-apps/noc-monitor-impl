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

package com.aoindustries.noc.monitor.infrastructure;

import com.aoapps.lang.Strings;
import com.aoapps.lang.function.SerializableFunction;
import com.aoapps.lang.text.LocalizedParseException;
import com.aoindustries.aoserv.client.linux.Server;
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
 * The workers for hard drive temperature monitoring.
 *
 * TODO: Keep historical data and warn if temp increases more than 20C/hour
 *
 * @author  AO Industries, Inc.
 */
class HardDrivesTemperatureNodeWorker extends TableResultNodeWorker<List<String>, String> {

  /**
   * The normal alert thresholds.
   */
  private static final int
      COLD_CRITICAL = 5,
      COLD_HIGH = 6,
      COLD_MEDIUM = 8,
      COLD_LOW = 10,
      HOT_LOW = 48,
      HOT_MEDIUM = 51,
      HOT_HIGH = 54,
      HOT_CRITICAL = 60
  ;

  /**
   * One unique worker is made per persistence file (and should match the linuxServer exactly)
   */
  private static final Map<String, HardDrivesTemperatureNodeWorker> workerCache = new HashMap<>();

  static HardDrivesTemperatureNodeWorker getWorker(File persistenceFile, Server linuxServer) throws IOException {
    String path = persistenceFile.getCanonicalPath();
    synchronized (workerCache) {
      HardDrivesTemperatureNodeWorker worker = workerCache.get(path);
      if (worker == null) {
        worker = new HardDrivesTemperatureNodeWorker(persistenceFile, linuxServer);
        workerCache.put(path, worker);
      } else {
        if (!worker.linuxServer.equals(linuxServer)) {
          throw new AssertionError("worker.linuxServer != linuxServer: " + worker.linuxServer + " != " + linuxServer);
        }
      }
      return worker;
    }
  }

  // Will use whichever connector first created this worker, even if other accounts connect later.
  private final Server linuxServer;

  HardDrivesTemperatureNodeWorker(File persistenceFile, Server linuxServer) {
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
      for (int index = 0, len = tableData.size(); index < len; index += 3) {
        AlertLevel alertLevel = alertLevels.get(index / 3);
        if (alertLevel.compareTo(highestAlertLevel) > 0) {
          highestAlertLevel = alertLevel;
          Object device = tableData.get(index);
          Object model = tableData.get(index + 1);
          Object temperature = tableData.get(index + 2);
          highestAlertMessage = locale -> device + " " + model + " " + temperature;
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
    return locale -> Arrays.asList(PACKAGE_RESOURCES.getMessage(locale, "HardDrivesTemperatureNodeWorker.columnHeader.device"),
        PACKAGE_RESOURCES.getMessage(locale, "HardDrivesTemperatureNodeWorker.columnHeader.model"),
        PACKAGE_RESOURCES.getMessage(locale, "HardDrivesTemperatureNodeWorker.columnHeader.temperature")
    );
  }

  @Override
  protected List<String> getQueryResult() throws Exception {
    String report = linuxServer.getHddTempReport();
    List<String> lines = Strings.splitLines(report);
    List<String> tableData = new ArrayList<>(lines.size() * 3);
    int lineNum = 0;
    for (String line : lines) {
      lineNum++;
      List<String> values = Strings.split(line, ':');
      if (values.size() != 3) {
        throw new LocalizedParseException(
            lineNum,
            PACKAGE_RESOURCES,
            "HardDrivesTemperatureNodeWorker.alertMessage.badColumnCount",
            line
        );
      }
      for (int c = 0, len = values.size(); c < len; c++) {
        tableData.add(values.get(c).trim());
      }
    }
    return tableData;
  }

  @Override
  protected SerializableFunction<Locale, List<String>> getTableData(List<String> tableData) throws Exception {
    return locale -> tableData;
  }

  @Override
  protected List<AlertLevel> getAlertLevels(List<String> tableData) {
    List<AlertLevel> alertLevels = new ArrayList<>(tableData.size() / 3);
    for (int index = 0, len = tableData.size(); index < len; index += 3) {
      String value = tableData.get(index + 2);
      AlertLevel alertLevel = AlertLevel.NONE;
      if (
          // These values all mean not monitored, keep at alert=NONE
          !"S.M.A.R.T. not available".equals(value)
              && !"drive supported, but it doesn't have a temperature sensor.".equals(value)
              && !"drive is sleeping".equals(value)
              && !"no sensor".equals(value)
      ) {
        // Parse the temperature value and compare
        boolean parsed;
        if (value.endsWith(" C")) {
          // A few hard drives read much differently than other drives, offset the thresholds here
//          String hostname = linuxServer.getHostname().toString();
//          String device = tableData.get(index);
          int offset;
//                    if (
//                        hostname.equals("xen1.mob.aoindustries.com")
//                        && device.equals("/dev/sda")
//                    ) {
//                        offset = -7;
//                    } else if (
//                        hostname.equals("xen907-4.fc.aoindustries.com")
//                        && (
//                            device.equals("/dev/sda")
//                            || device.equals("/dev/sdb")
//                        )
//                    ) {
//                        offset = 12;
//                    } else {
          offset = 0;
//                    }
          String numString = value.substring(0, value.length() - 2);
          try {
            int num = Integer.parseInt(numString);
            if (num <= (COLD_CRITICAL + offset) || num >= (HOT_CRITICAL + offset)) {
              alertLevel = AlertLevel.CRITICAL;
            } else if (num <= (COLD_HIGH + offset) || num >= (HOT_HIGH + offset)) {
              alertLevel = AlertLevel.HIGH;
            } else if (num <= (COLD_MEDIUM + offset) || num >= (HOT_MEDIUM + offset)) {
              alertLevel = AlertLevel.MEDIUM;
            } else if (num <= (COLD_LOW + offset) || num >= (HOT_LOW + offset)) {
              alertLevel = AlertLevel.LOW;
            }
            parsed = true;
          } catch (NumberFormatException err) {
            parsed = false;
          }
        } else {
          parsed = false;
        }
        if (!parsed) {
          alertLevel = AlertLevel.CRITICAL;
        }
      }
      alertLevels.add(alertLevel);
    }
    return alertLevels;
  }
}
