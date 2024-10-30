/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008-2012, 2016, 2017, 2018, 2020, 2021, 2022, 2024  AO Industries, Inc.
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

package com.aoindustries.noc.monitor.net;

import com.aoapps.lang.concurrent.LocalizedTimeoutException;
import com.aoapps.lang.i18n.Resources;
import com.aoindustries.aoserv.client.net.IpAddress;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import com.aoindustries.noc.monitor.TableMultiResultWorker;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.PingResult;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Each worker may be shared by any number of <code>PingNodeImpl</code>s.
 * A worker performs the background pinging and reports its results to
 * the ping nodes.  All of the cached data is stored in a Locale-neutral
 * way and converted to Locale-specific representations as needed.
 *
 * @author  AO Industries, Inc.
 */
class PingWorker extends TableMultiResultWorker<Object, PingResult> {

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, PingWorker.class);

  /**
   * The ping timeout.
   */
  private static final int TIMEOUT = 10000;

  /**
   * One unique worker is made per persistence directory (and should match the IP address exactly).
   */
  private static final Map<String, PingWorker> workerCache = new HashMap<>();

  static PingWorker getWorker(File persistenceDirectory, IpAddress ipAddress) throws IOException {
    String path = persistenceDirectory.getCanonicalPath();
    com.aoapps.net.InetAddress ip = ipAddress.getInetAddress();
    com.aoapps.net.InetAddress externalIp = ipAddress.getExternalInetAddress();
    com.aoapps.net.InetAddress pingAddress = externalIp == null ? ip : externalIp;
    synchronized (workerCache) {
      PingWorker worker = workerCache.get(path);
      if (worker == null) {
        worker = new PingWorker(persistenceDirectory, pingAddress);
        workerCache.put(path, worker);
      } else {
        if (!worker.ipAddress.equals(pingAddress)) {
          throw new AssertionError("worker.ipAddress != pingAddress: " + worker.ipAddress + " != " + pingAddress);
        }
      }
      return worker;
    }
  }

  /**
   * The most recent timer task.
   */
  private final com.aoapps.net.InetAddress ipAddress;

  private PingWorker(File persistenceDirectory, com.aoapps.net.InetAddress ipAddress) throws IOException {
    super(new File(persistenceDirectory, "pings"), new PingResultSerializer());
    this.ipAddress = ipAddress;
  }

  @Override
  protected int getHistorySize() {
    return 10000;
  }

  /**
   * Uses a single sample object because no data is contained in the sample, only the timing information is maintained.
   */
  private static final Object SAMPLE = new Object();

  @Override
  protected Object getSample() throws Exception {
    final InetAddress inetAddress = InetAddress.getByName(ipAddress.toString());
    boolean timeout = !inetAddress.isReachable(TIMEOUT);
    if (timeout) {
      throw new LocalizedTimeoutException(RESOURCES, "error.timeout");
    }
    return SAMPLE;
  }

  /**
   * Figures out the alert level.  It considers only the last 10 pings.  The number of timeouts follow:
   *
   * <pre>&gt;= 4  CRITICAL
   * &gt;= 3  HIGH
   * &gt;= 2  MEDIUM
   * &gt;= 1  LOW
   * == 0   NONE</pre>
   */
  private static AlertLevel getAlertLevel(int packetLossPercent) {
    if (packetLossPercent < 0) {
      return AlertLevel.UNKNOWN;
    }
    if (packetLossPercent >= 40) {
      return AlertLevel.CRITICAL;
    }
    if (packetLossPercent >= 30) {
      return AlertLevel.HIGH;
    }
    if (packetLossPercent >= 20) {
      return AlertLevel.MEDIUM;
    }
    if (packetLossPercent >= 10) {
      return AlertLevel.LOW;
    }
    return AlertLevel.NONE;
  }

  /**
   * Gets the packet loss percent.
   */
  private static int getPacketLossPercent(Iterable<? extends PingResult> previousResults) {
    int timeouts = 0;
    // The current value is never a timeout to get this far
    int checked = 1;
    // The history
    for (PingResult previousResult : previousResults) {
      if (previousResult.getError() != null) {
        timeouts++;
      }
      checked++;
      if (checked >= 10) {
        break;
      }
    }
    return timeouts * 10;
  }

  @Override
  protected AlertLevelAndMessage getAlertLevelAndMessage(Object sample, Iterable<? extends PingResult> previousResults) throws Exception {
    int packetLossPercent = getPacketLossPercent(previousResults);
    return new AlertLevelAndMessage(
        getAlertLevel(packetLossPercent),
        locale -> RESOURCES.getMessage(
            locale,
            "alertMessage",
            packetLossPercent
        )
    );
  }

  /**
   * Since pings support timeout, no need to provide timeout through
   * Future objects and ExecutorService.
   */
  @Override
  protected boolean useFutureTimeout() {
    return false;
  }

  /**
   * Sleeps one minute between checks.
   */
  @Override
  protected long getSleepDelay(boolean lastSuccessful, AlertLevel alertLevel) {
    return 60000;
  }

  @Override
  protected PingResult newErrorResult(long time, long latency, AlertLevel alertLevel, String error) {
    return new PingResult(time, latency, alertLevel, error);
  }

  @Override
  protected PingResult newSampleResult(long time, long latency, AlertLevel alertLevel, Object sample) {
    return new PingResult(time, latency, alertLevel);
  }
}
