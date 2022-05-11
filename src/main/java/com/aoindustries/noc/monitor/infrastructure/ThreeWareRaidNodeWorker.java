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

import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;

import com.aoapps.lang.EnumUtils;
import com.aoapps.lang.Strings;
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import com.aoindustries.noc.monitor.SingleResultNodeWorker;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.SingleResult;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The workers for 3ware RAID.
 *
 * @author  AO Industries, Inc.
 */
class ThreeWareRaidNodeWorker extends SingleResultNodeWorker {

  /**
   * One unique worker is made per persistence file (and should match the linuxServer exactly).
   */
  private static final Map<String, ThreeWareRaidNodeWorker> workerCache = new HashMap<>();

  static ThreeWareRaidNodeWorker getWorker(File persistenceFile, Server linuxServer) throws IOException {
    String path = persistenceFile.getCanonicalPath();
    synchronized (workerCache) {
      ThreeWareRaidNodeWorker worker = workerCache.get(path);
      if (worker == null) {
        worker = new ThreeWareRaidNodeWorker(persistenceFile, linuxServer);
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

  ThreeWareRaidNodeWorker(File persistenceFile, Server linuxServer) {
    super(persistenceFile);
    this.linuxServer = linuxServer;
  }

  @Override
  protected String getReport() throws IOException, SQLException {
    return linuxServer.get3wareRaidReport();
  }

  /**
   * Determines the alert message for the provided result.
   */
  @Override
  protected AlertLevelAndMessage getAlertLevelAndMessage(AlertLevel curAlertLevel, SingleResult result) {
    Function<Locale, String> error = result.getError();
    if (error != null) {
      return new AlertLevelAndMessage(
          // Don't downgrade UNKNOWN to CRITICAL on error
          EnumUtils.max(AlertLevel.CRITICAL, curAlertLevel),
          locale -> PACKAGE_RESOURCES.getMessage(
              locale,
              "ThreeWareRaidNode.alertMessage.error",
              error.apply(locale)
          )
      );
    }
    String report = result.getReport();
    AlertLevel highestAlertLevel = AlertLevel.NONE;
    Function<Locale, String> highestAlertMessage = null;
    if (!"\nNo controller found.\nMake sure appropriate AMCC/3ware device driver(s) are loaded.\n\n".equals(report)) {
      List<String> lines = Strings.splitLines(report);
      // Should have at least four lines
      if (lines.size() < 4) {
        return new AlertLevelAndMessage(
            AlertLevel.CRITICAL,
            locale -> PACKAGE_RESOURCES.getMessage(
                locale,
                "ThreeWareRaidNode.alertMessage.fourLinesOrMore",
                lines.size()
            )
        );
      }
      if (lines.get(0).length() > 0) {
        return new AlertLevelAndMessage(
            AlertLevel.CRITICAL,
            locale -> PACKAGE_RESOURCES.getMessage(
                locale,
                "ThreeWareRaidNode.alertMessage.firstLineShouldBeBlank",
                lines.get(0)
            )
        );
      }
      if (
          !"Ctl   Model        Ports   Drives   Units   NotOpt   RRate   VRate   BBU".equals(lines.get(1))
              && !"Ctl   Model        (V)Ports  Drives   Units   NotOpt  RRate   VRate  BBU".equals(lines.get(1))
      ) {
        return new AlertLevelAndMessage(
            AlertLevel.CRITICAL,
            locale -> PACKAGE_RESOURCES.getMessage(
                locale,
                "ThreeWareRaidNode.alertMessage.secondLineNotColumns",
                lines.get(1)
            )
        );
      }
      if (!"------------------------------------------------------------------------".equals(lines.get(2))) {
        return new AlertLevelAndMessage(
            AlertLevel.CRITICAL,
            locale -> PACKAGE_RESOURCES.getMessage(
                locale,
                "ThreeWareRaidNode.alertMessage.thirdLineSeparator",
                lines.get(2)
            )
        );
      }
      for (int c = 3; c < lines.size(); c++) {
        String line = lines.get(c);
        if (line.length() > 0) {
          List<String> values = Strings.splitCommaSpace(line);
          if (values.size() != 9) {
            return new AlertLevelAndMessage(
                AlertLevel.CRITICAL,
                locale -> PACKAGE_RESOURCES.getMessage(
                    locale,
                    "ThreeWareRaidNode.alertMessage.notNineValues",
                    values.size(),
                    line
                )
            );
          }
          String notOptString = values.get(5);
          try {
            int notOpt = Integer.parseInt(notOptString);
            if (notOpt > 0) {
              if (AlertLevel.HIGH.compareTo(highestAlertLevel) > 0) {
                highestAlertLevel = AlertLevel.HIGH;
                highestAlertMessage = locale -> PACKAGE_RESOURCES.getMessage(
                    locale,
                    notOpt == 1 ? "ThreeWareRaidNode.alertMessage.notOpt.singular" : "ThreeWareRaidNode.alertMessage.notOpt.plural",
                    values.get(0),
                    notOpt
                );
              }
            }
          } catch (NumberFormatException err) {
            return new AlertLevelAndMessage(
                AlertLevel.CRITICAL,
                locale -> PACKAGE_RESOURCES.getMessage(
                    locale,
                    "ThreeWareRaidNode.alertMessage.badNotOpt",
                    notOptString
                )
            );
          }
          String bbu = values.get(8);
          if (
              !"OK".equals(bbu)   // Not OK BBU
                  && !"-".equals(bbu) // No BBU
          ) {
            if (AlertLevel.MEDIUM.compareTo(highestAlertLevel) > 0) {
              highestAlertLevel = AlertLevel.MEDIUM;
              highestAlertMessage = locale -> PACKAGE_RESOURCES.getMessage(
                  locale,
                  "ThreeWareRaidNode.alertMessage.bbuNotOk",
                  values.get(0),
                  bbu
              );
            }
          }
        }
      }
    }
    return new AlertLevelAndMessage(highestAlertLevel, highestAlertMessage);
  }
}
