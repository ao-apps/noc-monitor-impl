/*
 * Copyright 2009-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.aoserv.client.IPAddress;
import com.aoindustries.aoserv.client.NetBind;
import com.aoindustries.aoserv.client.Server;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.MonitoringPoint;
import com.aoindustries.noc.monitor.common.NetBindResult;
import com.aoindustries.noc.monitor.portmon.PortMonitor;
import com.aoindustries.util.ErrorPrinter;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * @see NetBindNode
 *
 * @author  AO Industries, Inc.
 */
class NetBindNodeWorker extends TableMultiResultNodeWorker<String,NetBindResult> {

    /**
     * One unique worker is made per persistence file (and should match the NetMonitorSetting)
     */
    private static final Map<String, NetBindNodeWorker> workerCache = new HashMap<String,NetBindNodeWorker>();
    static NetBindNodeWorker getWorker(MonitoringPoint monitoringPoint, File persistenceFile, NetBindsNode.NetMonitorSetting netMonitorSetting) throws IOException {
        try {
            String path = persistenceFile.getCanonicalPath();
            synchronized(workerCache) {
                NetBindNodeWorker worker = workerCache.get(path);
                if(worker==null) {
                    worker = new NetBindNodeWorker(monitoringPoint, persistenceFile, netMonitorSetting);
                    workerCache.put(path, worker);
                } else {
                    if(!worker.netMonitorSetting.equals(netMonitorSetting)) throw new AssertionError("worker.netMonitorSetting!=netMonitorSetting: "+worker.netMonitorSetting+"!="+netMonitorSetting);
                }
                return worker;
            }
        } catch(IOException err) {
            ErrorPrinter.printStackTraces(err);
            throw err;
        } catch(RuntimeException err) {
            ErrorPrinter.printStackTraces(err);
            throw err;
        }
    }

    final private NetBindsNode.NetMonitorSetting netMonitorSetting;
    private volatile PortMonitor portMonitor;

    private NetBindNodeWorker(MonitoringPoint monitoringPoint, File persistenceFile, NetBindsNode.NetMonitorSetting netMonitorSetting) throws IOException {
        super(monitoringPoint, persistenceFile, new NetBindResultSerializer(monitoringPoint));
        this.netMonitorSetting = netMonitorSetting;
    }

    @Override
    protected int getHistorySize() {
        return 2000;
    }

    @Override
    protected String getSample(Locale locale) throws Exception {
        // Get the latest netBind for the appProtocol and monitoring parameters
        NetBind netBind = netMonitorSetting.getNetBind();
        NetBind currentNetBind = netBind.getTable().get(netBind.getKey());
        // If loopback or private IP, make the monitoring request through the master->daemon channel
        String ipAddress = netMonitorSetting.getIpAddress();
        if(IPAddress.isPrivate(ipAddress) || IPAddress.LOOPBACK_IP.equals(ipAddress)) {
            Server server = netMonitorSetting.getServer();
            AOServer aoServer = server.getAOServer();
            if(aoServer==null) throw new IllegalArgumentException(accessor.getMessage(/*locale,*/ "NetBindNodeWorker.server.notAOServer", server));
            portMonitor = new AOServDaemonPortMonitor(
                aoServer,
                netMonitorSetting.getIpAddress(),
                netMonitorSetting.getPort(),
                netMonitorSetting.getNetProtocol(),
                currentNetBind.getAppProtocol().getProtocol(),
                currentNetBind.getMonitoringParameters()
            );
        } else {
            portMonitor = PortMonitor.getPortMonitor(
                netMonitorSetting.getIpAddress(),
                netMonitorSetting.getPort(),
                netMonitorSetting.getNetProtocol(),
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
        if(myPortMonitor!=null) myPortMonitor.cancel();
    }

    @Override
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, String sample, Iterable<? extends NetBindResult> previousResults) throws Exception {
        return new AlertLevelAndMessage(
            AlertLevel.NONE,
            sample
        );
    }

    @Override
    protected NetBindResult newErrorResult(long time, long latency, AlertLevel alertLevel, String error) {
        return new NetBindResult(monitoringPoint, time, latency, alertLevel, error, null);
    }

    @Override
    protected NetBindResult newSampleResult(long time, long latency, AlertLevel alertLevel, String sample) {
        return new NetBindResult(monitoringPoint, time, latency, alertLevel, null, sample);
    }
}
