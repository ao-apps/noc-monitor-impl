/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.aoserv.client.IPAddress;
import com.aoindustries.aoserv.client.NetBind;
import com.aoindustries.aoserv.client.Server;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.TableMultiResult;
import com.aoindustries.noc.monitor.portmon.PortMonitor;
import com.aoindustries.util.ErrorPrinter;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @see NetBindNode
 *
 * @author  AO Industries, Inc.
 */
class NetBindNodeWorker extends TableMultiResultNodeWorker {

    /**
     * One unique worker is made per persistence file (and should match the NetMonitorSetting)
     */
    private static final Map<String, NetBindNodeWorker> workerCache = new HashMap<String,NetBindNodeWorker>();
    static NetBindNodeWorker getWorker(File persistenceFile, NetBindsNode.NetMonitorSetting netMonitorSetting) throws IOException {
        try {
            String path = persistenceFile.getCanonicalPath();
            synchronized(workerCache) {
                NetBindNodeWorker worker = workerCache.get(path);
                if(worker==null) {
                    worker = new NetBindNodeWorker(persistenceFile, new File(path+".new"), netMonitorSetting);
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

    private NetBindNodeWorker(File persistenceFile, File newPersistenceFile, NetBindsNode.NetMonitorSetting netMonitorSetting) {
        super(persistenceFile, newPersistenceFile, false);
        this.netMonitorSetting = netMonitorSetting;
    }

    @Override
    protected int getHistorySize() {
        return 1000;
    }

    @Override
    protected List<?> getRowData(Locale locale) throws Exception {
        // Get the latest netBind for the appProtocol and monitoring parameters
        NetBind netBind = netMonitorSetting.getNetBind();
        NetBind currentNetBind = netBind.getTable().get(netBind.getKey());
        // If loopback or private IP, make the monitoring request through the master->daemon channel
        String ipAddress = netMonitorSetting.getIpAddress();
        if(IPAddress.isPrivate(ipAddress) || IPAddress.LOOPBACK_IP.equals(ipAddress)) {
            Server server = netMonitorSetting.getServer();
            AOServer aoServer = server.getAOServer();
            if(aoServer==null) throw new IllegalArgumentException(ApplicationResourcesAccessor.getMessage(locale, "NetBindNodeWorker.server.notAOServer", server));
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
        return Collections.singletonList(portMonitor.checkPort());
    }

    @Override
    protected void cancel() {
        PortMonitor myPortMonitor = portMonitor;
        if(myPortMonitor!=null) myPortMonitor.cancel();
    }

    @Override
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, List<?> rowData, LinkedList<TableMultiResult> previousResults) throws Exception {
        return new AlertLevelAndMessage(
            AlertLevel.NONE,
            (String)rowData.get(0)
        );
    }
}
