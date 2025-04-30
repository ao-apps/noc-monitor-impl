/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2015, 2016, 2018, 2019, 2020, 2021, 2022, 2025  AO Industries, Inc.
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

package com.aoindustries.noc.monitor.signup;

import com.aoapps.lang.function.SerializableFunction;
import com.aoapps.lang.i18n.Resources;
import com.aoindustries.aoserv.client.AoservConnector;
import com.aoindustries.aoserv.client.account.Administrator;
import com.aoindustries.aoserv.client.signup.Request;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * The workers for 3ware RAID.
 *
 * @author  AO Industries, Inc.
 */
class SignupsWorker extends TableResultWorker<List<Object>, Object> {

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, SignupsWorker.class);

  /**
   * One unique worker is made per persistence file.
   */
  private static final Map<String, SignupsWorker> workerCache = new HashMap<>();

  static SignupsWorker getWorker(File persistenceFile, AoservConnector conn) throws IOException {
    String path = persistenceFile.getCanonicalPath();
    synchronized (workerCache) {
      SignupsWorker worker = workerCache.get(path);
      if (worker == null) {
        worker = new SignupsWorker(persistenceFile, conn);
        workerCache.put(path, worker);
      }
      return worker;
    }
  }

  private final AoservConnector conn;

  SignupsWorker(File persistenceFile, AoservConnector conn) {
    super(persistenceFile);
    this.conn = conn;
  }

  /**
   * React quickly to signups, but still ramp-up for errors.
   */
  @Override
  protected boolean isIncrementalRampUp(boolean isError) {
    return isError;
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
      // Count the number of incompleted signups
      int incompleteCount;
      {
        int i = 0;
        for (int index = 0, len = tableData.size(); index < len; index += 6) {
          String completedBy = (String) tableData.get(index + 4);
          if (completedBy == null) {
            i++;
          }
        }
        incompleteCount = i;
      }
      if (incompleteCount == 0) {
        return AlertLevelAndMessage.NONE;
      } else {
        return new AlertLevelAndMessage(
            AlertLevel.CRITICAL,
            locale -> incompleteCount == 1
                ? RESOURCES.getMessage(locale, "incompleteCount.singular", incompleteCount)
                : RESOURCES.getMessage(locale, "incompleteCount.plural", incompleteCount)
        );
      }
    }
  }

  @Override
  protected int getColumns() {
    return 6;
  }

  @Override
  protected SerializableFunction<Locale, List<String>> getColumnHeaders() {
    return locale -> Arrays.asList(RESOURCES.getMessage(locale, "columnHeader.source"),
        RESOURCES.getMessage(locale, "columnHeader.pkey"),
        RESOURCES.getMessage(locale, "columnHeader.time"),
        RESOURCES.getMessage(locale, "columnHeader.ip_address"),
        RESOURCES.getMessage(locale, "columnHeader.completed_by"),
        RESOURCES.getMessage(locale, "columnHeader.completed_time")
    );
  }

  @Override
  protected List<Object> getQueryResult() throws Exception {
    List<Object> tableData = new ArrayList<>();
    // Add the old signup forms
    WebSiteDatabase.getDatabase().queryRun(
        results -> {
          while (results.next()) {
            tableData.add("aoweb");
            tableData.add(results.getInt("pkey"));
            tableData.add(new TimeWithTimeZone(results.getTimestamp("time").getTime()));
            tableData.add(results.getString("ip_address"));
            tableData.add(results.getString("completed_by"));
            Timestamp completedTime = results.getTimestamp("completed_time");
            tableData.add(completedTime == null ? null : new TimeWithTimeZone(completedTime.getTime()));
          }
        },
        "select * from signup_requests order by time"
    );

    // Add the aoserv signups
    for (Request request : conn.getSignup().getRequest()) {
      tableData.add(request.getPackageDefinition().getAccount().getName());
      tableData.add(request.getPkey());
      tableData.add(new TimeWithTimeZone(request.getTime().getTime()));
      tableData.add(request.getIpAddress());
      Administrator completedBy = request.getCompletedBy();
      tableData.add(completedBy == null ? null : completedBy.getUsername().getUsername());
      Timestamp completedTime = request.getCompletedTime();
      tableData.add(completedTime == null ? null : new TimeWithTimeZone(completedTime.getTime()));
    }
    return tableData;
  }

  @Override
  protected SerializableFunction<Locale, List<Object>> getTableData(List<Object> tableData) throws Exception {
    return locale -> tableData;
  }

  @Override
  protected List<AlertLevel> getAlertLevels(List<Object> tableData) {
    List<AlertLevel> alertLevels = new ArrayList<>(tableData.size() / 6);
    for (int index = 0, len = tableData.size(); index < len; index += 6) {
      String completedBy = (String) tableData.get(index + 4);
      alertLevels.add(completedBy == null ? AlertLevel.CRITICAL : AlertLevel.NONE);
    }
    return alertLevels;
  }
}
