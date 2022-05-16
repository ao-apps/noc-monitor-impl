/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2018, 2020, 2021, 2022  AO Industries, Inc.
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

package com.aoindustries.noc.monitor.web;

import com.aoapps.lang.i18n.Resources;
import com.aoindustries.aoserv.client.web.HttpdServer;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import com.aoindustries.noc.monitor.TableMultiResultWorker;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.HttpdServerResult;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author  AO Industries, Inc.
 */
class HttpdServerWorker extends TableMultiResultWorker<List<Integer>, HttpdServerResult> {

  private static final Logger logger = Logger.getLogger(HttpdServerWorker.class.getName());

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, HttpdServerWorker.class);

  /**
   * One unique worker is made per persistence file (and should match httpdServer exactly).
   */
  private static final Map<String, HttpdServerWorker> workerCache = new HashMap<>();

  static HttpdServerWorker getWorker(File persistenceFile, HttpdServer httpdServer) throws IOException {
    String path = persistenceFile.getCanonicalPath();
    synchronized (workerCache) {
      HttpdServerWorker worker = workerCache.get(path);
      if (worker == null) {
        if (logger.isLoggable(Level.FINE)) {
          logger.fine("Creating new worker for " + httpdServer.getName());
        }
        worker = new HttpdServerWorker(persistenceFile, httpdServer);
        workerCache.put(path, worker);
      } else {
        if (logger.isLoggable(Level.FINER)) {
          logger.finer("Found existing worker for " + httpdServer.getName());
        }
        if (!worker.originalHttpdServer.equals(httpdServer)) {
          throw new AssertionError("worker.httpdServer != httpdServer: " + worker.originalHttpdServer + " != " + httpdServer);
        }
      }
      return worker;
    }
  }

  private final HttpdServer originalHttpdServer;
  private HttpdServer currentHttpdServer;

  private HttpdServerWorker(File persistenceFile, HttpdServer httpdServer) throws IOException {
    super(persistenceFile, new HttpdServerResultSerializer());
    this.originalHttpdServer = currentHttpdServer = httpdServer;
  }

  @Override
  protected int getHistorySize() {
    return 2000;
  }

  @Override
  protected List<Integer> getSample() throws Exception {
    // Get the latest limits
    currentHttpdServer = originalHttpdServer.getTable().getConnector().getWeb().getHttpdServer().get(originalHttpdServer.getPkey());
    int concurrency = currentHttpdServer.getConcurrency();
    return Arrays.asList(
        concurrency,
        currentHttpdServer.getMaxConcurrency(),
        currentHttpdServer.getMonitoringConcurrencyLow(),
        currentHttpdServer.getMonitoringConcurrencyMedium(),
        currentHttpdServer.getMonitoringConcurrencyHigh(),
        currentHttpdServer.getMonitoringConcurrencyCritical()
    );
  }

  @Override
  protected AlertLevelAndMessage getAlertLevelAndMessage(List<Integer> sample, Iterable<? extends HttpdServerResult> previousResults) throws Exception {
    int concurrency = sample.get(0);
    int concurrencyCritical = currentHttpdServer.getMonitoringConcurrencyCritical();
    if (concurrencyCritical != -1 && concurrency >= concurrencyCritical) {
      return new AlertLevelAndMessage(
          AlertLevel.CRITICAL,
          locale -> RESOURCES.getMessage(
              locale,
              "alertMessage.critical",
              concurrencyCritical,
              concurrency
          )
      );
    }
    int concurrencyHigh = currentHttpdServer.getMonitoringConcurrencyHigh();
    if (concurrencyHigh != -1 && concurrency >= concurrencyHigh) {
      return new AlertLevelAndMessage(
          AlertLevel.HIGH,
          locale -> RESOURCES.getMessage(
              locale,
              "alertMessage.high",
              concurrencyHigh,
              concurrency
          )
      );
    }
    int concurrencyMedium = currentHttpdServer.getMonitoringConcurrencyMedium();
    if (concurrencyMedium != -1 && concurrency >= concurrencyMedium) {
      return new AlertLevelAndMessage(
          AlertLevel.MEDIUM,
          locale -> RESOURCES.getMessage(
              locale,
              "alertMessage.medium",
              concurrencyMedium,
              concurrency
          )
      );
    }
    int concurrencyLow = currentHttpdServer.getMonitoringConcurrencyLow();
    if (concurrencyLow != -1 && concurrency >= concurrencyLow) {
      return new AlertLevelAndMessage(
          AlertLevel.LOW,
          locale -> RESOURCES.getMessage(
              locale,
              "alertMessage.low",
              concurrencyLow,
              concurrency
          )
      );
    }
    if (concurrencyLow == -1) {
      return new AlertLevelAndMessage(
          AlertLevel.NONE,
          locale -> RESOURCES.getMessage(
              locale,
              "alertMessage.notAny",
              concurrency
          )
      );
    } else {
      return new AlertLevelAndMessage(
          AlertLevel.NONE,
          locale -> RESOURCES.getMessage(
              locale,
              "alertMessage.none",
              concurrencyLow,
              concurrency
          )
      );
    }
  }

  @Override
  protected HttpdServerResult newErrorResult(long time, long latency, AlertLevel alertLevel, String error) {
    return new HttpdServerResult(time, latency, alertLevel, error);
  }

  @Override
  protected HttpdServerResult newSampleResult(long time, long latency, AlertLevel alertLevel, List<Integer> sample) {
    return new HttpdServerResult(
        time,
        latency,
        alertLevel,
        sample.get(0),
        sample.get(1),
        sample.get(2),
        sample.get(3),
        sample.get(4),
        sample.get(5)
    );
  }
}
