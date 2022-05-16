/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2009, 2016, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoapps.lang.function.SerializableFunction;
import com.aoapps.lang.i18n.Resources;
import com.aoindustries.aoserv.client.backup.MysqlReplication;
import com.aoindustries.aoserv.client.mysql.Database;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import com.aoindustries.noc.monitor.TableResultWorker;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TableResult;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * The workers for MySQLDatabaseNode.
 *
 * @author  AO Industries, Inc.
 */
class DatabaseWorker extends TableResultWorker<List<Database.TableStatus>, Object> {

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, DatabaseWorker.class);

  /**
   * One unique worker is made per persistence file (and should match the database exactly).
   */
  private static final Map<String, DatabaseWorker> workerCache = new HashMap<>();

  static DatabaseWorker getWorker(File persistenceFile, Database database, MysqlReplication slave) throws IOException, SQLException {
    String path = persistenceFile.getCanonicalPath();
    synchronized (workerCache) {
      DatabaseWorker worker = workerCache.get(path);
      if (worker == null) {
        worker = new DatabaseWorker(persistenceFile, database, slave);
        workerCache.put(path, worker);
      } else {
        if (!worker.database.equals(database)) {
          throw new AssertionError("worker.database != database: " + worker.database + " != " + database);
        }
      }
      return worker;
    }
  }

  // Will use whichever connector first created this worker, even if other accounts connect later.
  private final Database database;
  private final MysqlReplication slave;
  final boolean isSlowServer;
  private final Object lastTableStatusesLock = new Object();
  private List<Database.TableStatus> lastTableStatuses;

  DatabaseWorker(File persistenceFile, Database database, MysqlReplication slave) throws IOException, SQLException {
    super(persistenceFile);
    this.database = database;
    this.slave = slave;
    String hostname = database.getMysqlServer().getLinuxServer().getHostname().toString();
    this.isSlowServer =
        "www.swimconnection.com".equals(hostname);
    // || hostname.equals("www1.leagle.com")
  }

  @Override
  protected int getColumns() {
    return 18;
  }

  @Override
  protected SerializableFunction<Locale, List<String>> getColumnHeaders() {
    return locale -> Arrays.asList(RESOURCES.getMessage(locale, "columnHeader.name"),
        RESOURCES.getMessage(locale, "columnHeader.engine"),
        RESOURCES.getMessage(locale, "columnHeader.version"),
        RESOURCES.getMessage(locale, "columnHeader.rowFormat"),
        RESOURCES.getMessage(locale, "columnHeader.rows"),
        RESOURCES.getMessage(locale, "columnHeader.avgRowLength"),
        RESOURCES.getMessage(locale, "columnHeader.dataLength"),
        RESOURCES.getMessage(locale, "columnHeader.maxDataLength"),
        RESOURCES.getMessage(locale, "columnHeader.indexLength"),
        RESOURCES.getMessage(locale, "columnHeader.dataFree"),
        RESOURCES.getMessage(locale, "columnHeader.autoIncrement"),
        RESOURCES.getMessage(locale, "columnHeader.createTime"),
        RESOURCES.getMessage(locale, "columnHeader.updateTime"),
        RESOURCES.getMessage(locale, "columnHeader.checkTime"),
        RESOURCES.getMessage(locale, "columnHeader.collation"),
        RESOURCES.getMessage(locale, "columnHeader.checksum"),
        RESOURCES.getMessage(locale, "columnHeader.createOptions"),
        RESOURCES.getMessage(locale, "columnHeader.comment")
    );
  }

  @Override
  protected List<Database.TableStatus> getQueryResult() throws Exception {
    List<Database.TableStatus> tableStatuses = database.getTableStatus(slave);
    setLastTableStatuses(tableStatuses);
    return tableStatuses;
  }

  @Override
  protected SerializableFunction<Locale, List<Object>> getTableData(List<Database.TableStatus> tableStatuses) throws Exception {
    List<Object> tableData = new ArrayList<>(tableStatuses.size() * 18);
    for (Database.TableStatus tableStatus : tableStatuses) {
      tableData.add(tableStatus.getName());
      tableData.add(tableStatus.getEngine());
      tableData.add(tableStatus.getVersion());
      tableData.add(tableStatus.getRowFormat());
      tableData.add(tableStatus.getRows());
      tableData.add(tableStatus.getAvgRowLength());
      tableData.add(tableStatus.getDataLength());
      tableData.add(tableStatus.getMaxDataLength());
      tableData.add(tableStatus.getIndexLength());
      tableData.add(tableStatus.getDataFree());
      tableData.add(tableStatus.getAutoIncrement());
      tableData.add(tableStatus.getCreateTime());
      tableData.add(tableStatus.getUpdateTime());
      tableData.add(tableStatus.getCheckTime());
      tableData.add(tableStatus.getCollation());
      tableData.add(tableStatus.getChecksum());
      tableData.add(tableStatus.getCreateOptions());
      tableData.add(tableStatus.getComment());
    }
    return locale -> tableData;
  }

  @SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter") // Passed unmodifiable
  private void setLastTableStatuses(List<Database.TableStatus> tableStatuses) {
    synchronized (lastTableStatusesLock) {
      this.lastTableStatuses = tableStatuses;
      lastTableStatusesLock.notifyAll();
    }
  }

  /**
   * Gets the last table statuses.  May wait for the data to become available,
   * will not return null.  May wait for a very long time in some cases.
   */
  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  List<Database.TableStatus> getLastTableStatuses() throws InterruptedException {
    synchronized (lastTableStatusesLock) {
      while (lastTableStatuses == null) {
        lastTableStatusesLock.wait();
      }
      return lastTableStatuses;
    }
  }

  /**
   * If is a slowServer (many tables), only updates once every 12 hours.
   * Otherwise updates once every five minutes.
   */
  @Override
  protected long getSleepDelay(boolean lastSuccessful, AlertLevel alertLevel) {
    if (isSlowServer) {
      return 12L * 60 * 60 * 1000; // Only update once every 12 hours
    }
    return 5L * 60 * 1000;
  }

  @Override
  protected long getTimeout() {
    return isSlowServer ? 30 : 5;
  }

  @Override
  protected List<AlertLevel> getAlertLevels(List<Database.TableStatus> tableStatuses) {
    List<AlertLevel> alertLevels = new ArrayList<>(tableStatuses.size());
    for (Database.TableStatus tableStatus : tableStatuses) {
      AlertLevel alertLevel = AlertLevel.NONE;
      // Could compare data length to max data length and warn, but max data length is incredibly high in MySQL 5.0+
      alertLevels.add(alertLevel);
    }
    return alertLevels;
  }

  /**
   * Determines the alert message for the provided result.
   */
  @Override
  public AlertLevelAndMessage getAlertLevelAndMessage(AlertLevel curAlertLevel, TableResult result) {
    if (result.isError()) {
      return new AlertLevelAndMessage(
          result.getAlertLevels().get(0),
          locale -> result.getTableData(locale).get(0).toString()
      );
    } else {
      return AlertLevelAndMessage.NONE;
    }
  }
}
