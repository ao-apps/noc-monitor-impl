/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2009-2013, 2016, 2017, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoapps.lang.LocalizedIllegalArgumentException;
import com.aoapps.lang.Throwables;
import com.aoapps.net.InetAddress;
import com.aoapps.net.Port;
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.aoserv.client.net.Bind;
import com.aoindustries.aoserv.client.net.Host;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;
import com.aoindustries.noc.monitor.TableMultiResultNodeWorker;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.NetBindResult;
import com.aoindustries.noc.monitor.portmon.PortMonitor;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @see NetBindNode
 *
 * @author  AO Industries, Inc.
 */
class BindNodeWorker extends TableMultiResultNodeWorker<String, NetBindResult> {

  private static final Logger logger = Logger.getLogger(BindNodeWorker.class.getName());

  /**
   * One unique worker is made per persistence file (and should match the NetMonitorSetting)
   */
  private static final Map<String, BindNodeWorker> workerCache = new HashMap<>();

  @SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
  static BindNodeWorker getWorker(File persistenceFile, BindsNode.NetMonitorSetting netMonitorSetting) throws IOException {
    try {
      String path = persistenceFile.getCanonicalPath();
      synchronized (workerCache) {
        BindNodeWorker worker = workerCache.get(path);
        if (worker == null) {
          worker = new BindNodeWorker(persistenceFile, netMonitorSetting);
          workerCache.put(path, worker);
        } else if (!worker.netMonitorSetting.equals(netMonitorSetting)) {
          throw new AssertionError("worker.netMonitorSetting != netMonitorSetting: " + worker.netMonitorSetting + " != " + netMonitorSetting);
        }
        return worker;
      }
    } catch (ThreadDeath td) {
      throw td;
    } catch (Throwable t) {
      logger.log(Level.SEVERE, null, t);
      throw Throwables.wrap(t, IOException.class, IOException::new);
    }
  }

  private final BindsNode.NetMonitorSetting netMonitorSetting;
  private volatile PortMonitor portMonitor;

  private BindNodeWorker(File persistenceFile, BindsNode.NetMonitorSetting netMonitorSetting) throws IOException {
    super(persistenceFile, new BindResultSerializer());
    this.netMonitorSetting = netMonitorSetting;
  }

  @Override
  protected int getHistorySize() {
    return 2000;
  }

  @Override
  protected String getSample() throws Exception {
    // Get the latest netBind for the appProtocol and monitoring parameters
    Bind netBind = netMonitorSetting.getNetBind();
    Bind currentNetBind = netBind.getTable().getConnector().getNet().getBind().get(netBind.getPkey());
    Port netPort = netMonitorSetting.getPort();
    // If loopback or private IP, make the monitoring request through the master->daemon channel
    InetAddress ipAddress = netMonitorSetting.getIpAddress();
    if (
        ipAddress.isUniqueLocal()
            || ipAddress.isLoopback()
            || netPort.getPort() == 25 // Port 25 cannot be monitored directly from several networks
    ) {
      Host host = netMonitorSetting.getServer();
      Server linuxServer = host.getLinuxServer();
      if (linuxServer == null) {
        throw new LocalizedIllegalArgumentException(PACKAGE_RESOURCES, "NetBindNodeWorker.host.notLinuxServer", host.toString());
      }
      portMonitor = new AOServDaemonPortMonitor(
          linuxServer,
          ipAddress,
          netPort,
          currentNetBind.getAppProtocol().getProtocol(),
          currentNetBind.getMonitoringParameters()
      );
    } else {
      portMonitor = PortMonitor.getPortMonitor(
          ipAddress,
          netMonitorSetting.getPort(),
          currentNetBind.getAppProtocol().getProtocol(),
          currentNetBind.getMonitoringParameters()
      );
    }
    return portMonitor.checkPort();
  }

  @Override
  protected void cancel(Future<String> future) {
    super.cancel(future);
    PortMonitor myPortMonitor = portMonitor;
    if (myPortMonitor != null) {
      myPortMonitor.cancel();
    }
  }

  @Override
  protected AlertLevelAndMessage getAlertLevelAndMessage(String sample, Iterable<? extends NetBindResult> previousResults) throws Exception {
    return new AlertLevelAndMessage(
        AlertLevel.NONE,
        locale -> sample
    );
  }

  @Override
  protected NetBindResult newErrorResult(long time, long latency, AlertLevel alertLevel, String error) {
    return new NetBindResult(time, latency, alertLevel, error, null);
  }

  @Override
  protected NetBindResult newSampleResult(long time, long latency, AlertLevel alertLevel, String sample) {
    return new NetBindResult(time, latency, alertLevel, null, sample);
  }
}
