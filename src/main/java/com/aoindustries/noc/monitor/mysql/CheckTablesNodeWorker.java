/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2009-2013, 2016, 2017, 2018, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoapps.collections.AoCollections;
import com.aoapps.lang.function.SerializableFunction;
import com.aoapps.sql.MilliInterval;
import com.aoindustries.aoserv.client.backup.MysqlReplication;
import com.aoindustries.aoserv.client.mysql.Database;
import com.aoindustries.aoserv.client.mysql.Server;
import com.aoindustries.aoserv.client.mysql.Table_Name;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;
import com.aoindustries.noc.monitor.TableResultNodeWorker;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TableResult;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The workers for MySQLCheckTablesNode.
 *
 * @author  AO Industries, Inc.
 */
class CheckTablesNodeWorker extends TableResultNodeWorker<List<Object>, Object> {

  /**
   * One unique worker is made per persistence file (and should match the mysqlDatabase exactly)
   */
  private static final Map<String, CheckTablesNodeWorker> workerCache = new HashMap<>();
  static CheckTablesNodeWorker getWorker(DatabaseNode databaseNode, File persistenceFile) throws IOException {
    String path = persistenceFile.getCanonicalPath();
    synchronized (workerCache) {
      CheckTablesNodeWorker worker = workerCache.get(path);
      if (worker == null) {
        worker = new CheckTablesNodeWorker(databaseNode, persistenceFile);
        workerCache.put(path, worker);
      } else {
        if (!worker.databaseNode.getMySQLDatabase().equals(databaseNode.getMySQLDatabase())) {
          throw new AssertionError("worker.mysqlDatabase != mysqlDatabase: "+worker.databaseNode.getMySQLDatabase()+" != "+databaseNode.getMySQLDatabase());
        }
      }
      return worker;
    }
  }

  // Will use whichever connector first created this worker, even if other accounts connect later.
  private final DatabaseNode databaseNode;

  CheckTablesNodeWorker(DatabaseNode databaseNode, File persistenceFile) {
    super(persistenceFile);
    this.databaseNode = databaseNode;
  }

  @Override
  protected int getColumns() {
    return 5;
  }

  @Override
  protected SerializableFunction<Locale, List<String>> getColumnHeaders() {
    return locale -> Arrays.asList(PACKAGE_RESOURCES.getMessage(locale, "MySQLCheckTablesNodeWorker.columnHeader.name"),
      PACKAGE_RESOURCES.getMessage(locale, "MySQLCheckTablesNodeWorker.columnHeader.engine"),
      PACKAGE_RESOURCES.getMessage(locale, "MySQLCheckTablesNodeWorker.columnHeader.duration"),
      PACKAGE_RESOURCES.getMessage(locale, "MySQLCheckTablesNodeWorker.columnHeader.msgType"),
      PACKAGE_RESOURCES.getMessage(locale, "MySQLCheckTablesNodeWorker.columnHeader.msgText")
    );
  }

  @Override
  protected List<Object> getQueryResult() throws Exception {
    Database mysqlDatabase = databaseNode.getMySQLDatabase();
    MysqlReplication mysqlSlave = databaseNode.getMySQLSlave();

    // Don't check any table on MySQL 5.1+ information_schema database
    if (mysqlDatabase.getName().equals(Database.INFORMATION_SCHEMA)) {
      String version = mysqlDatabase.getMySQLServer().getVersion().getVersion();
      if (
        version.startsWith(Server.VERSION_5_1_PREFIX)
        || version.startsWith(Server.VERSION_5_6_PREFIX)
        || version.startsWith(Server.VERSION_5_7_PREFIX)
      ) {
        return Collections.emptyList();
      }
    }

    // Don't check any table on MySQL 5.6+ performance_schema database
    if (mysqlDatabase.getName().equals(Database.PERFORMANCE_SCHEMA)) {
      String version = mysqlDatabase.getMySQLServer().getVersion().getVersion();
      if (
        version.startsWith(Server.VERSION_5_6_PREFIX)
        || version.startsWith(Server.VERSION_5_7_PREFIX)
      ) {
        return Collections.emptyList();
      }
    }

    // Don't check any table on MySQL 5.7+ sys database
    if (mysqlDatabase.getName().equals(Database.SYS)) {
      String version = mysqlDatabase.getMySQLServer().getVersion().getVersion();
      if (
        version.startsWith(Server.VERSION_5_7_PREFIX)
      ) {
        return Collections.emptyList();
      }
    }

    List<Database.TableStatus> lastTableStatuses = databaseNode.databaseWorker.getLastTableStatuses();
    if (lastTableStatuses.isEmpty()) {
      return Collections.emptyList();
    }
    // Build the set of table names and types
    List<Table_Name> tableNames = new ArrayList<>(lastTableStatuses.size());
    Map<Table_Name, Database.Engine> tables = AoCollections.newHashMap(lastTableStatuses.size());
    for (Database.TableStatus lastTableStatus : lastTableStatuses) {
      Database.Engine engine = lastTableStatus.getEngine();
      if (
        engine != Database.Engine.CSV
        && engine != Database.Engine.HEAP
        && engine != Database.Engine.InnoDB
        && engine != Database.Engine.MEMORY
        && engine != Database.Engine.PERFORMANCE_SCHEMA
        && !(engine == null && "VIEW".equals(lastTableStatus.getComment()))
      ) {
        Table_Name name = lastTableStatus.getName();
        if (
          // Skip the four expected non-checkable tables in information_schema
          !mysqlDatabase.getName().equals(Database.INFORMATION_SCHEMA)
          || (
            !name.toString().equals("COLUMNS")
            && !name.toString().equals("ROUTINES")
            && !name.toString().equals("TRIGGERS")
            && !name.toString().equals("VIEWS")
          )
        ) {
          tableNames.add(name);
          tables.put(name, engine);
        }
      }
    }
    List<Database.CheckTableResult> checkTableResults = mysqlDatabase.checkTables(mysqlSlave, tableNames);
    List<Object> tableData = new ArrayList<>(checkTableResults.size()*5);

    for (Database.CheckTableResult checkTableResult : checkTableResults) {
      Table_Name table = checkTableResult.getTable();
      tableData.add(table);
      tableData.add(tables.get(table));
      tableData.add(new MilliInterval(checkTableResult.getDuration()));
      tableData.add(checkTableResult.getMsgType());
      tableData.add(checkTableResult.getMsgText());
    }
    return tableData;
  }

  @Override
  protected SerializableFunction<Locale, List<Object>> getTableData(List<Object> tableData) throws Exception {
    return locale -> tableData;
  }

  /**
   * If is a slowServer (many tables), only checks once every 12 hours.
   * Otherwise checks once every five minutes.
   */
  @Override
  protected long getSleepDelay(boolean lastSuccessful, AlertLevel alertLevel) {
    if (databaseNode.databaseWorker.isSlowServer) {
      return 12L * 60 * 60 * 1000; // Only check tables once every 12 hours
    }
    return 5L * 60 * 1000;
  }

  @Override
  protected long getTimeout() {
    return databaseNode.databaseWorker.isSlowServer ? 30 : 5;
  }

  @Override
  protected List<AlertLevel> getAlertLevels(List<Object> tableData) {
    List<AlertLevel> alertLevels = new ArrayList<>(tableData.size()/5);
    for (int index=0, len=tableData.size();index<len;index+=5) {
      String msgText = (String)tableData.get(index+4);
      alertLevels.add(
        msgText != null && (msgText.equals("OK") || msgText.equals("Table is already up to date"))
        ? AlertLevel.NONE
        : AlertLevel.CRITICAL
      );
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
      List<?> tableData = result.getTableData(Locale.getDefault());
      for (int index=0, len=tableData.size();index<len;index+=5) {
        String msgText = (String)tableData.get(index+4);
        if (msgText == null || (!msgText.equals("OK") && !msgText.equals("Table is already up to date"))) {
          Object name = tableData.get(index);
          return new AlertLevelAndMessage(
            AlertLevel.CRITICAL,
            locale -> name + " - " + msgText
          );
        }
      }
    }
    return AlertLevelAndMessage.NONE;
  }
}
