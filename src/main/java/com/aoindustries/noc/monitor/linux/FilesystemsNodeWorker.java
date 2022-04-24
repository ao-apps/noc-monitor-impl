/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2016, 2017, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoapps.lang.Strings;
import com.aoapps.lang.function.SerializableFunction;
import com.aoapps.lang.i18n.ThreadLocale;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The workers for filesystem monitoring.
 *
 * @author  AO Industries, Inc.
 */
class FilesystemsNodeWorker extends TableResultNodeWorker<List<String>, String> {

  private static final Logger logger = Logger.getLogger(FilesystemsNodeWorker.class.getName());

  /**
   * One unique worker is made per persistence file (and should match the linuxServer exactly)
   */
  private static final Map<String, FilesystemsNodeWorker> workerCache = new HashMap<>();

  static FilesystemsNodeWorker getWorker(File persistenceFile, Server linuxServer) throws IOException {
    String path = persistenceFile.getCanonicalPath();
    synchronized (workerCache) {
      FilesystemsNodeWorker worker = workerCache.get(path);
      if (worker == null) {
        worker = new FilesystemsNodeWorker(persistenceFile, linuxServer);
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

  FilesystemsNodeWorker(File persistenceFile, Server linuxServer) {
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
      for (int index = 0, len = tableData.size(); index < len; index += 12) {
        AlertLevelAndMessage alam;
        try {
          alam = getAlertLevelAndMessage(tableData, index);
        } catch (Exception err) {
          logger.log(Level.SEVERE, null, err);
          alam = new AlertLevelAndMessage(
              AlertLevel.CRITICAL,
              locale -> ThreadLocale.call(
                  locale,
                  () -> {
                    String msg = err.getLocalizedMessage();
                    if (msg == null || msg.isEmpty()) {
                      msg = err.toString();
                    }
                    return msg;
                  }
              )
          );
        }
        AlertLevel alertLevel = alam.getAlertLevel();
        if (alertLevel.compareTo(highestAlertLevel) > 0) {
          highestAlertLevel = alertLevel;
          highestAlertMessage = alam.getAlertMessage();
        }
      }
    }
    return new AlertLevelAndMessage(highestAlertLevel, highestAlertMessage);
  }

  private static final int COLUMNS = 12;

  @Override
  protected int getColumns() {
    return COLUMNS;
  }

  @Override
  protected SerializableFunction<Locale, List<String>> getColumnHeaders() {
    return locale -> Arrays.asList(PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.columnHeader.mountpoint"),
        PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.columnHeader.device"),
        PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.columnHeader.bytes"),
        PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.columnHeader.used"),
        PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.columnHeader.free"),
        PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.columnHeader.use"),
        //RESOURCES.getMessage(locale, "FilesystemsNodeWorker.columnHeader.inodes"),
        //RESOURCES.getMessage(locale, "FilesystemsNodeWorker.columnHeader.iused"),
        //RESOURCES.getMessage(locale, "FilesystemsNodeWorker.columnHeader.ifree"),
        PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.columnHeader.iuse"),
        PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.columnHeader.fstype"),
        PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.columnHeader.mountoptions"),
        PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.columnHeader.extstate"),
        PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.columnHeader.extmaxmount"),
        PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.columnHeader.extchkint")
    );
  }

  private static String toPercentString(Byte b) {
    return (b == null) ? "" : (b + "%");
  }

  @Override
  protected List<String> getQueryResult() throws Exception {
    Collection<Server.FilesystemReport> report = linuxServer.getFilesystemsReport().values();

    // Read the report, line-by-line
    List<String> tableData = new ArrayList<>(report.size() * COLUMNS);
    for (Server.FilesystemReport fs : report) {
      tableData.add(fs.getMountPoint()); // mountpoint
      tableData.add(fs.getDevice()); // device
      tableData.add(Strings.getApproximateSize(fs.getBytes())); // bytes
      tableData.add(Strings.getApproximateSize(fs.getUsed())); // used
      tableData.add(Strings.getApproximateSize(fs.getFree())); // free
      tableData.add(toPercentString(fs.getUse())); // use
      //tableData.add(line[6]); // inodes
      //tableData.add(line[7]); // iused
      //tableData.add(line[8]); // ifree
      tableData.add(toPercentString(fs.getInodeUse())); // iuse
      tableData.add(fs.getFsType()); // fstype
      tableData.add(fs.getMountOptions()); // mountoptions
      tableData.add(fs.getExtState()); // extstate
      tableData.add(fs.getExtMaxMount()); // extmaxmount
      tableData.add(fs.getExtCheckInterval()); // extchkint
    }
    return tableData;
  }

  @Override
  protected SerializableFunction<Locale, List<String>> getTableData(List<String> tableData) throws Exception {
    return locale -> tableData;
  }

  @Override
  protected List<AlertLevel> getAlertLevels(List<String> tableData) {
    List<AlertLevel> alertLevels = new ArrayList<>(tableData.size() / 12);
    for (int index = 0, len = tableData.size(); index < len; index += 12) {
      try {
        AlertLevelAndMessage alam = getAlertLevelAndMessage(tableData, index);
        alertLevels.add(alam.getAlertLevel());
      } catch (Exception err) {
        logger.log(Level.SEVERE, null, err);
        alertLevels.add(AlertLevel.CRITICAL);
      }
    }
    return alertLevels;
  }

  /**
   * Determines one line of alert level and message.
   */
  private AlertLevelAndMessage getAlertLevelAndMessage(List<?> tableData, int index) throws Exception {
    AlertLevel highestAlertLevel = AlertLevel.NONE;
    Function<Locale, String> highestAlertMessage = locale -> PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.alertMessage.allOk");

    // Check extstate
    String fstype = tableData.get(index + 7).toString();
    {
      if (
          "ext2".equals(fstype)
              || "ext3".equals(fstype)
      ) {
        String extstate = tableData.get(index + 9).toString();
        if (
            (
                "ext3".equals(fstype)
                    && !"clean".equals(extstate)
            ) || (
                "ext2".equals(fstype)
                    && !"not clean".equals(extstate)
                    && !"clean".equals(extstate)
            )
        ) {
          AlertLevel newAlertLevel = AlertLevel.CRITICAL;
          if (newAlertLevel.compareTo(highestAlertLevel) > 0) {
            highestAlertLevel = newAlertLevel;
            highestAlertMessage = locale -> PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.alertMessage.extstate.unexpectedState", extstate);
          }
        }
      }
    }

    // Check for inode percent
    {
      String iuse = tableData.get(index + 6).toString();
      if (iuse.length() != 0) {
        if (!iuse.endsWith("%")) {
          throw new IOException("iuse doesn't end with '%': " + iuse);
        }
        int iuseNum = Integer.parseInt(iuse.substring(0, iuse.length() - 1));
        final AlertLevel newAlertLevel;
        if (iuseNum < 0 || iuseNum >= 95) {
          newAlertLevel = AlertLevel.CRITICAL;
        } else if (iuseNum >= 90) {
          newAlertLevel = AlertLevel.HIGH;
        } else if (iuseNum >= 85) {
          newAlertLevel = AlertLevel.MEDIUM;
        } else if (iuseNum >= 80) {
          newAlertLevel = AlertLevel.LOW;
        } else {
          newAlertLevel = AlertLevel.NONE;
        }
        if (newAlertLevel.compareTo(highestAlertLevel) > 0) {
          highestAlertLevel = newAlertLevel;
          highestAlertMessage = locale -> PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.alertMessage.iuse", iuse);
        }
      }
    }

    // Check for disk space percent
    {
      String mountpoint = tableData.get(index).toString();
      String use = tableData.get(index + 5).toString();
      if (!use.endsWith("%")) {
        throw new IOException("use doesn't end with '%': " + use);
      }
      int useNum = Integer.parseInt(use.substring(0, use.length() - 1));
      final AlertLevel newAlertLevel;
      if (mountpoint.startsWith("/var/backup")) {
        // Backup partitions allow a higher percentage and never go critical
        if (useNum >= 98) {
          newAlertLevel = AlertLevel.HIGH;
        } else if (useNum >= 97) {
          newAlertLevel = AlertLevel.MEDIUM;
        } else if (useNum >= 96) {
          newAlertLevel = AlertLevel.LOW;
        } else {
          newAlertLevel = AlertLevel.NONE;
        }
      } else {
        // Other partitions notify at lower percentages and can go critical
        if (useNum >= 97) {
          newAlertLevel = AlertLevel.CRITICAL;
        } else if (useNum >= 94) {
          newAlertLevel = AlertLevel.HIGH;
        } else if (useNum >= 91) {
          newAlertLevel = AlertLevel.MEDIUM;
        } else if (useNum >= 88) {
          newAlertLevel = AlertLevel.LOW;
        } else {
          newAlertLevel = AlertLevel.NONE;
        }
      }
      if (newAlertLevel.compareTo(highestAlertLevel) > 0) {
        highestAlertLevel = newAlertLevel;
        highestAlertMessage = locale -> PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.alertMessage.use", use);
      }
    }

    // Make sure extmaxmount is -1
    if (highestAlertLevel.compareTo(AlertLevel.LOW) < 0) {
      String extmaxmount = tableData.get(index + 10).toString();
      switch (fstype) {
        case "ext3":
          if (!"-1".equals(extmaxmount)) {
            highestAlertLevel = AlertLevel.LOW;
            highestAlertMessage = locale -> PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.alertMessage.extmaxmount.ext3", extmaxmount);
          }
          break;
        case "ext2":
          if ("-1".equals(extmaxmount)) {
            highestAlertLevel = AlertLevel.LOW;
            highestAlertMessage = locale -> PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.alertMessage.extmaxmount.ext2", extmaxmount);
          }
          break;
      }
    }

    // Make sure extchkint is 0
    if (highestAlertLevel.compareTo(AlertLevel.LOW) < 0) {
      String extchkint = tableData.get(index + 11).toString();
      switch (fstype) {
        case "ext3":
          if (!"0 (<none>)".equals(extchkint)) {
            highestAlertLevel = AlertLevel.LOW;
            highestAlertMessage = locale -> PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.alertMessage.extchkint.ext3", extchkint);
          }
          break;
        case "ext2":
          if ("0 (<none>)".equals(extchkint)) {
            highestAlertLevel = AlertLevel.LOW;
            highestAlertMessage = locale -> PACKAGE_RESOURCES.getMessage(locale, "FilesystemsNodeWorker.alertMessage.extchkint.ext2", extchkint);
          }
          break;
      }
    }

    return new AlertLevelAndMessage(highestAlertLevel, highestAlertMessage);
  }
}
