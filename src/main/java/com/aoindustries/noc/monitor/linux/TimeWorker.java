/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008-2013, 2016, 2018, 2019, 2020, 2021, 2022, 2024  AO Industries, Inc.
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

import com.aoapps.lang.i18n.Resources;
import com.aoapps.sql.MilliInterval;
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import com.aoindustries.noc.monitor.TableMultiResultWorker;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TimeResult;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * The clock skew for a single sample in milliseconds.  Calculated as follows:
 *
 * <pre>     st: remote system time (in milliseconds from Epoch)
 *      rt: request time (in milliseconds from Epoch)
 *      l:  request latency (in nanoseconds)
 *
 *      skew = st - (rt + round(l/2000000))</pre>
 *
 * <p>Alert levels are:</p>
 *
 * <pre>         &gt;=1 minute  Critical
 *          &gt;=4 seconds High
 *          &gt;=2 seconds Medium
 *          &gt;=1 second  Low
 *          &lt;1  second  None</pre>
 *
 * @author  AO Industries, Inc.
 */
class TimeWorker extends TableMultiResultWorker<MilliInterval, TimeResult> {

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, TimeWorker.class);

  /**
   * One unique worker is made per persistence directory (and should match linuxServer exactly).
   */
  private static final Map<String, TimeWorker> workerCache = new HashMap<>();

  static TimeWorker getWorker(File persistenceDirectory, Server linuxServer) throws IOException {
    String path = persistenceDirectory.getCanonicalPath();
    synchronized (workerCache) {
      TimeWorker worker = workerCache.get(path);
      if (worker == null) {
        worker = new TimeWorker(persistenceDirectory, linuxServer);
        workerCache.put(path, worker);
      } else {
        if (!worker.originalLinuxServer.equals(linuxServer)) {
          throw new AssertionError("worker.linuxServer != linuxServer: " + worker.originalLinuxServer + " != " + linuxServer);
        }
      }
      return worker;
    }
  }

  private final Server originalLinuxServer;
  private Server currentLinuxServer;

  private TimeWorker(File persistenceDirectory, Server linuxServer) throws IOException {
    super(new File(persistenceDirectory, "time"), new TimeResultSerializer());
    this.originalLinuxServer = currentLinuxServer = linuxServer;
  }

  @Override
  protected int getHistorySize() {
    return 2000;
  }

  @Override
  protected MilliInterval getSample() throws Exception {
    // Get the latest limits
    currentLinuxServer = originalLinuxServer.getTable().getConnector().getLinux().getServer().get(originalLinuxServer.getPkey());

    long requestTime = System.currentTimeMillis();
    long startNanos = System.nanoTime();
    long systemTime = currentLinuxServer.getSystemTimeMillis();
    long latency = System.nanoTime() - startNanos;
    long latencyRemainder = latency % 2000000;
    long skew = systemTime - (requestTime + latency / 2000000);
    if (latencyRemainder >= 1000000) {
      skew--;
    }

    return new MilliInterval(skew);
  }

  private static AlertLevel getAlertLevel(long skew) {
    if (skew >= 60000 || skew <= -60000) {
      return AlertLevel.CRITICAL;
    }
    if (skew >=  4000 || skew <=  -4000) {
      return AlertLevel.HIGH;
    }
    if (skew >=  2000 || skew <=  -2000) {
      return AlertLevel.MEDIUM;
    }
    if (skew >=  1000 || skew <=  -1000) {
      return AlertLevel.MEDIUM;
    }
    return AlertLevel.NONE;
  }

  @Override
  protected AlertLevelAndMessage getAlertLevelAndMessage(MilliInterval sample, Iterable<? extends TimeResult> previousResults) throws Exception {
    final long currentSkew = sample.getIntervalMillis();

    return new AlertLevelAndMessage(
        getAlertLevel(currentSkew),
        locale -> RESOURCES.getMessage(
            locale,
            "alertMessage",
            currentSkew
        )
    );
  }

  @Override
  protected TimeResult newErrorResult(long time, long latency, AlertLevel alertLevel, String error) {
    return new TimeResult(time, latency, alertLevel, error);
  }

  @Override
  protected TimeResult newSampleResult(long time, long latency, AlertLevel alertLevel, MilliInterval sample) {
    return new TimeResult(time, latency, alertLevel, sample.getIntervalMillis());
  }
}
