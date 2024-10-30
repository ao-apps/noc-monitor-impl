/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008-2013, 2016, 2018, 2020, 2021, 2022, 2024  AO Industries, Inc.
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

import com.aoapps.lang.Strings;
import com.aoapps.lang.i18n.Resources;
import com.aoindustries.aoserv.client.net.Device;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import com.aoindustries.noc.monitor.TableMultiResultWorker;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.NetDeviceBitRateResult;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * network traffic (but no alerts on loopback by default).
 * configurable limits per alert level per net_device
 * based on 5-minute averages, sampled every five minutes, will take up to 20 minutes to buzz
 *
 * @author  AO Industries, Inc.
 */
class DeviceBitRateWorker extends TableMultiResultWorker<List<Object>, NetDeviceBitRateResult> {

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, DeviceBitRateWorker.class);

  /**
   * The number of bytes overhead for each Ethernet frame, including interframe gap, assuming no VLAN tag.
   *
   * <p>Preamble + Start of frame + CRC + Interframe gap</p>
   */
  private static final int FRAME_ADDITIONAL_BYTES = 7 + 1 + 4 + 12;

  /**
   * One unique worker is made per persistence directory (and should match the net device exactly).
   */
  private static final Map<String, DeviceBitRateWorker> workerCache = new HashMap<>();

  static DeviceBitRateWorker getWorker(File persistenceDirectory, Device device) throws IOException {
    String path = persistenceDirectory.getCanonicalPath();
    synchronized (workerCache) {
      DeviceBitRateWorker worker = workerCache.get(path);
      if (worker == null) {
        worker = new DeviceBitRateWorker(persistenceDirectory, device);
        workerCache.put(path, worker);
      } else if (!worker.originalDevice.equals(device)) {
        throw new AssertionError("worker.device != device: " + worker.originalDevice + " != " + device);
      }
      return worker;
    }
  }

  // Will use whichever connector first created this worker, even if other accounts connect later.
  private final Device originalDevice;
  private Device currentDevice;

  private DeviceBitRateWorker(File persistenceDirectory, Device device) throws IOException {
    super(new File(persistenceDirectory, "bit_rate"), new DeviceBitRateResultSerializer());
    this.originalDevice = currentDevice = device;
  }

  @Override
  protected int getHistorySize() {
    return 2000;
  }

  private long lastStatsTime = -1;
  private long lastTxBytes = -1;
  private long lastRxBytes = -1;
  private long lastTxPackets = -1;
  private long lastRxPackets = -1;

  @Override
  protected long getSleepDelay(boolean lastSuccessful, AlertLevel alertLevel) {
    return 5L * 60000L;
  }

  @Override
  protected List<Object> getSample() throws Exception {
    // Get the latest object
    currentDevice = originalDevice.getTable().getConnector().getNet().getDevice().get(originalDevice.getPkey());

    // Get the current state
    String stats = currentDevice.getStatisticsReport();
    List<String> lines = Strings.splitLines(stats);
    if (lines.size() != 5) {
      throw new ParseException("Should have five lines in the stats, have " + lines.size(), 0);
    }
    long thisStatsTime = Long.parseLong(lines.get(0));

    // values of -1 indicate a server-side detected reset
    long thisTxBytes = Long.parseLong(lines.get(1));
    long thisRxBytes = Long.parseLong(lines.get(2));
    long thisTxPackets = Long.parseLong(lines.get(3));
    long thisRxPackets = Long.parseLong(lines.get(4));

    try {
      // Calculate rates from previous state
      long txBitsPerSecond;
      long rxBitsPerSecond;
      long txPacketsPerSecond;
      long rxPacketsPerSecond;
      if (lastStatsTime == -1) {
        // First report
        txBitsPerSecond = -1;
        rxBitsPerSecond = -1;
        txPacketsPerSecond = -1;
        rxPacketsPerSecond = -1;
      } else if (lastStatsTime >= thisStatsTime) {
        // Time reset to the past
        throw new Exception("Host time reset to the past");
      } else if (
          // values of -1 indicate a server-side detected reset
          thisTxBytes == -1 || thisTxBytes < lastTxBytes
              || thisRxBytes == -1 || thisRxBytes < lastRxBytes
              || thisTxPackets == -1 || thisTxPackets < lastTxPackets
              || thisRxPackets == -1 || thisRxPackets < lastRxPackets
      ) { // device counters reset
        throw new Exception("Device counters reset");
      } else {
        long timeDiff = thisStatsTime - lastStatsTime;
        long txNumPackets = thisTxPackets - lastTxPackets;
        long rxNumPackets = thisRxPackets - lastRxPackets;
        txPacketsPerSecond = txNumPackets * 1000 / timeDiff;
        rxPacketsPerSecond = rxNumPackets * 1000 / timeDiff;
        txBitsPerSecond = (thisTxBytes - lastTxBytes + FRAME_ADDITIONAL_BYTES * txNumPackets) * Byte.SIZE * 1000 / timeDiff;
        rxBitsPerSecond = (thisRxBytes - lastRxBytes + FRAME_ADDITIONAL_BYTES * rxNumPackets) * Byte.SIZE * 1000 / timeDiff;
      }
      // Display the alert thresholds
      List<Object> sample = new ArrayList<>(8);
      sample.add(txBitsPerSecond);
      sample.add(rxBitsPerSecond);
      sample.add(txPacketsPerSecond);
      sample.add(rxPacketsPerSecond);
      sample.add(currentDevice.getMonitoringBitRateLow());
      sample.add(currentDevice.getMonitoringBitRateMedium());
      sample.add(currentDevice.getMonitoringBitRateHigh());
      sample.add(currentDevice.getMonitoringBitRateCritical());
      return sample;
    } finally {
      // Store for the next report
      lastStatsTime = thisStatsTime;
      // values of -1 indicate a server-side detected reset
      lastTxBytes = thisTxBytes == -1 ? 0 : thisTxBytes;
      lastRxBytes = thisRxBytes == -1 ? 0 : thisRxBytes;
      lastTxPackets = thisTxPackets == -1 ? 0 : thisTxPackets;
      lastRxPackets = thisRxPackets == -1 ? 0 : thisRxPackets;
    }
  }

  @Override
  protected AlertLevelAndMessage getAlertLevelAndMessage(List<Object> sample, Iterable<? extends NetDeviceBitRateResult> previousResults) throws Exception {
    long txBitsPerSecond = (Long) sample.get(0);
    long rxBitsPerSecond = (Long) sample.get(1);
    if (txBitsPerSecond == -1 || rxBitsPerSecond == -1) {
      return new AlertLevelAndMessage(AlertLevel.UNKNOWN, null);
    }
    long bps;
    String direction;
    if (txBitsPerSecond > rxBitsPerSecond) {
      // Base result on tx
      bps = txBitsPerSecond;
      direction = "tx";
    } else {
      // Base result on rx
      bps = rxBitsPerSecond;
      direction = "rx";
    }

    // Get the alert limits
    long bitRateCritical = currentDevice.getMonitoringBitRateCritical();
    if (bitRateCritical != -1 && bps >= bitRateCritical) {
      return new AlertLevelAndMessage(
          AlertLevel.CRITICAL,
          locale -> RESOURCES.getMessage(
              locale,
              "alertMessage." + direction + ".critical",
              bitRateCritical,
              bps
          )
      );
    }
    long bitRateHigh = currentDevice.getMonitoringBitRateHigh();
    if (bitRateHigh != -1 && bps >= bitRateHigh) {
      return new AlertLevelAndMessage(
          AlertLevel.HIGH,
          locale -> RESOURCES.getMessage(
              locale,
              "alertMessage." + direction + ".high",
              bitRateHigh,
              bps
          )
      );
    }
    long bitRateMedium = currentDevice.getMonitoringBitRateMedium();
    if (bitRateMedium != -1 && bps >= bitRateMedium) {
      return new AlertLevelAndMessage(
          AlertLevel.MEDIUM,
          locale -> RESOURCES.getMessage(
              locale,
              "alertMessage." + direction + ".medium",
              bitRateMedium,
              bps
          )
      );
    }
    long bitRateLow = currentDevice.getMonitoringBitRateLow();
    if (bitRateLow != -1 && bps >= bitRateLow) {
      return new AlertLevelAndMessage(
          AlertLevel.LOW,
          locale -> RESOURCES.getMessage(
              locale,
              "alertMessage." + direction + ".low",
              bitRateLow,
              bps
          )
      );
    }
    return new AlertLevelAndMessage(
        AlertLevel.NONE,
        locale -> RESOURCES.getMessage(
            locale,
            "alertMessage." + direction + ".none",
            bps
        )
    );
  }

  @Override
  protected NetDeviceBitRateResult newErrorResult(long time, long latency, AlertLevel alertLevel, String error) {
    return new NetDeviceBitRateResult(time, latency, alertLevel, error);
  }

  @Override
  protected NetDeviceBitRateResult newSampleResult(long time, long latency, AlertLevel alertLevel, List<Object> sample) {
    return new NetDeviceBitRateResult(
        time,
        latency,
        alertLevel,
        (Long) sample.get(0),
        (Long) sample.get(1),
        (Long) sample.get(2),
        (Long) sample.get(3),
        (Long) sample.get(4),
        (Long) sample.get(5),
        (Long) sample.get(6),
        (Long) sample.get(7)
    );
  }
}
