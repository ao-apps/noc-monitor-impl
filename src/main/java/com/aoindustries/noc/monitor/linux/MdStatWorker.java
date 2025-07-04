/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008-2013, 2014, 2016, 2018, 2019, 2020, 2021, 2022, 2024, 2025  AO Industries, Inc.
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

import com.aoapps.lang.EnumUtils;
import com.aoapps.lang.Strings;
import com.aoapps.lang.i18n.Resources;
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import com.aoindustries.noc.monitor.SingleResultWorker;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.SingleResult;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Function;

/**
 * The workers for watching /proc/mdstat.
 *
 * @author  AO Industries, Inc.
 */
class MdStatWorker extends SingleResultWorker {

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, MdStatWorker.class);

  private enum RaidLevel {
    LINEAR,
    RAID1,
    RAID5,
    RAID6
  }

  /**
   * One unique worker is made per persistence file (and should match the server exactly).
   */
  private static final Map<String, MdStatWorker> workerCache = new HashMap<>();

  static MdStatWorker getWorker(File persistenceFile, Server server) throws IOException {
    String path = persistenceFile.getCanonicalPath();
    synchronized (workerCache) {
      MdStatWorker worker = workerCache.get(path);
      if (worker == null) {
        worker = new MdStatWorker(persistenceFile, server);
        workerCache.put(path, worker);
      } else {
        if (!worker.server.equals(server)) {
          throw new AssertionError("worker.server != server: " + worker.server + " != " + server);
        }
      }
      return worker;
    }
  }

  // Will use whichever connector first created this worker, even if other accounts connect later.
  private final Server server;

  MdStatWorker(File persistenceFile, Server server) {
    super(persistenceFile);
    this.server = server;
  }

  @Override
  protected String getReport() throws IOException, SQLException {
    return server.getMdStatReport();
  }

  /**
   * Determines the alert level and message for the provided result.
   *
   * <pre>raid1:
   *      one up + zero down: medium
   *      zero down: none
   *      three+ up: low
   *      two up...: medium
   *      one up...: high
   *      zero up..: critical
   * raid5:
   *      one down.: high
   *      two+ down: critical
   * raid6:
   *      one down.: medium
   *      two down.: high
   *      two+ down: critical</pre>
   */
  @Override
  protected AlertLevelAndMessage getAlertLevelAndMessage(AlertLevel curAlertLevel, SingleResult result) {
    Function<Locale, String> error = result.getError();
    if (error != null) {
      return new AlertLevelAndMessage(
          // Don't downgrade UNKNOWN to CRITICAL on error
          EnumUtils.max(AlertLevel.CRITICAL, curAlertLevel),
          locale -> RESOURCES.getMessage(
              locale,
              "alertMessage.error",
              error.apply(locale)
          )
      );
    }
    String report = result.getReport();
    List<String> lines = Strings.splitLines(report);
    RaidLevel lastRaidLevel = null;
    AlertLevel highestAlertLevel = AlertLevel.NONE;
    Function<Locale, String> highestAlertMessage = null;
    for (String line : lines) {
      if (
          !line.startsWith("Personalities :")
              && !line.startsWith("unused devices:")
              && !(
              line.startsWith("      bitmap: ")
                  && line.endsWith(" chunk")
            )
              // Skip routine RAID check progress line:
              && !(
              line.startsWith("      [")
                  && line.contains("]  check = ")
            )
      ) {
        if (line.indexOf(':') != -1) {
          // Must contain raid type
          if (line.contains(" linear ")) {
            lastRaidLevel = RaidLevel.LINEAR;
          } else if (line.contains(" raid1 ")) {
            lastRaidLevel = RaidLevel.RAID1;
          } else if (line.contains(" raid5 ")) {
            lastRaidLevel = RaidLevel.RAID5;
          } else if (line.contains(" raid6 ")) {
            lastRaidLevel = RaidLevel.RAID6;
          } else {
            return new AlertLevelAndMessage(
                AlertLevel.CRITICAL,
                locale -> RESOURCES.getMessage(
                    locale,
                    "alertMessage.noRaidType",
                    line
                )
            );
          }
        } else {
          // Resync is low priority
          if (
              (
                  line.contains("resync")
                      || line.contains("recovery")
                )
                  && line.contains("finish")
                  && line.contains("speed")
          ) {
            if (AlertLevel.LOW.compareTo(highestAlertLevel) > 0) {
              highestAlertLevel = AlertLevel.LOW;
              highestAlertMessage = locale -> RESOURCES.getMessage(
                  locale,
                  "alertMessage.resync",
                  line.trim()
              );
            }
          } else {
            int pos1 = line.indexOf('[');
            if (pos1 != -1) {
              int pos2 = line.indexOf(']', pos1 + 1);
              if (pos2 != -1) {
                pos1 = line.indexOf('[', pos2 + 1);
                if (pos1 != -1) {
                  pos2 = line.indexOf(']', pos1 + 1);
                  if (pos2 != -1) {
                    // Count the down and up between the brackets
                    final int upCount;
                    final int downCount;
                    {
                      int up = 0;
                      int down = 0;
                      for (int pos = pos1 + 1; pos < pos2; pos++) {
                        char ch = line.charAt(pos);
                        if (ch == 'U') {
                          up++;
                        } else if (ch == '_') {
                          down++;
                        } else {
                          return new AlertLevelAndMessage(
                              AlertLevel.CRITICAL,
                              locale -> RESOURCES.getMessage(
                                  locale,
                                  "alertMessage.invalidCharacter",
                                  ch
                              )
                          );
                        }
                      }
                      upCount = up;
                      downCount = down;
                    }
                    // Get the current alert level
                    final AlertLevel alertLevel;
                    final Function<Locale, String> alertMessage;
                    if (lastRaidLevel == RaidLevel.RAID1) {
                      if (upCount == 1 && downCount == 0) {
                        alertLevel = AlertLevel.MEDIUM;
                      } else if (downCount == 0) {
                        alertLevel = AlertLevel.NONE;
                      } else if (upCount >= 3) {
                        alertLevel = AlertLevel.LOW;
                      } else if (upCount == 2) {
                        alertLevel = AlertLevel.MEDIUM;
                      } else if (upCount == 1) {
                        alertLevel = AlertLevel.HIGH;
                      } else if (upCount == 0) {
                        alertLevel = AlertLevel.CRITICAL;
                      } else {
                        throw new AssertionError("upCount should have already matched");
                      }
                      alertMessage = locale -> RESOURCES.getMessage(
                          locale,
                          "alertMessage.raid1",
                          upCount,
                          downCount
                      );
                    } else if (lastRaidLevel == RaidLevel.RAID5) {
                      if (downCount == 0) {
                        alertLevel = AlertLevel.NONE;
                      } else if (downCount == 1) {
                        alertLevel = AlertLevel.HIGH;
                      } else if (downCount >= 2) {
                        alertLevel = AlertLevel.CRITICAL;
                      } else {
                        throw new AssertionError("downCount should have already matched");
                      }
                      alertMessage = locale -> RESOURCES.getMessage(
                          locale,
                          "alertMessage.raid5",
                          upCount,
                          downCount
                      );
                    } else if (lastRaidLevel == RaidLevel.RAID6) {
                      if (downCount == 0) {
                        alertLevel = AlertLevel.NONE;
                      } else if (downCount == 1) {
                        alertLevel = AlertLevel.MEDIUM;
                      } else if (downCount == 2) {
                        alertLevel = AlertLevel.HIGH;
                      } else if (downCount >= 3) {
                        alertLevel = AlertLevel.CRITICAL;
                      } else {
                        throw new AssertionError("downCount should have already matched");
                      }
                      alertMessage = locale -> RESOURCES.getMessage(
                          locale,
                          "alertMessage.raid6",
                          upCount,
                          downCount
                      );
                    } else {
                      final RaidLevel raidLevel = lastRaidLevel;
                      return new AlertLevelAndMessage(
                          AlertLevel.CRITICAL,
                          locale -> RESOURCES.getMessage(
                              locale,
                              "alertMessage.unexpectedRaidLevel",
                              raidLevel
                          )
                      );
                    }
                    if (alertLevel.compareTo(highestAlertLevel) > 0) {
                      highestAlertLevel = alertLevel;
                      highestAlertMessage = alertMessage;
                    }
                  } else {
                    return new AlertLevelAndMessage(
                        AlertLevel.CRITICAL,
                        locale -> RESOURCES.getMessage(
                            locale,
                            "alertMessage.unableToFindCharacter",
                            ']',
                            line
                        )
                    );
                  }
                } else {
                  return new AlertLevelAndMessage(
                      AlertLevel.CRITICAL,
                      locale -> RESOURCES.getMessage(
                          locale,
                          "alertMessage.unableToFindCharacter",
                          '[',
                          line
                      )
                  );
                }
              } else {
                return new AlertLevelAndMessage(
                    AlertLevel.CRITICAL,
                    locale -> RESOURCES.getMessage(
                        locale,
                        "alertMessage.unableToFindCharacter",
                        ']',
                        line
                    )
                );
              }
            }
          }
        }
      }
    }
    return new AlertLevelAndMessage(highestAlertLevel, highestAlertMessage);
  }
}
