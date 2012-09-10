/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.NetDevice;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.MonitoringPoint;
import com.aoindustries.noc.monitor.common.SingleResult;
import com.aoindustries.util.StringUtility;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The workers for bonding monitoring.
 *
 * @author  AO Industries, Inc.
 */
class NetDeviceBondingNodeWorker extends SingleResultNodeWorker {

    /**
     * One unique worker is made per persistence file (and should match the net device exactly)
     */
    private static final Map<String, NetDeviceBondingNodeWorker> workerCache = new HashMap<String,NetDeviceBondingNodeWorker>();
    static NetDeviceBondingNodeWorker getWorker(MonitoringPoint monitoringPoint, File persistenceFile, NetDevice netDevice) throws IOException {
        String path = persistenceFile.getCanonicalPath();
        synchronized(workerCache) {
            NetDeviceBondingNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new NetDeviceBondingNodeWorker(monitoringPoint, persistenceFile, netDevice);
                workerCache.put(path, worker);
            } else {
                if(!worker.netDevice.equals(netDevice)) throw new AssertionError("worker.netDevice!=netDevice: "+worker.netDevice+"!="+netDevice);
            }
            return worker;
        }
    }

    // Will use whichever connector first created this worker, even if other accounts connect later.
    final private NetDevice netDevice;

    private NetDeviceBondingNodeWorker(MonitoringPoint monitoringPoint, File persistenceFile, NetDevice netDevice) {
        super(monitoringPoint, persistenceFile);
        this.netDevice = netDevice;
    }

    @Override
    protected String getReport() throws IOException, SQLException {
        return netDevice.getBondingReport();
    }

    /**
     * Determines the alert level for the provided result.
     */
    @Override
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, SingleResult result) {
        if(result.getError()!=null) {
            return new AlertLevelAndMessage(
                AlertLevel.CRITICAL,
                accessor.getMessage(

                    //locale,
                    "NetDeviceBondingNode.alertMessage.error",
                    result.getError()
                )
            );
        }
        String report = result.getReport();
        List<String> lines = StringUtility.splitLines(report);
        int upCount = 0;
        int downCount = 0;
        boolean skippedFirst = false;
        for(String line : lines) {
            if(line.startsWith("MII Status: ")) {
                if(!skippedFirst) skippedFirst = true;
                else {
                    if(line.equals("MII Status: up")) upCount++;
                    else downCount++;
                }
            }
        }
        AlertLevel alertLevel;
        if(upCount==0) alertLevel = AlertLevel.CRITICAL;
        else if(downCount!=0) alertLevel = AlertLevel.HIGH;
        else alertLevel = AlertLevel.NONE;
        String alertMessage = accessor.getMessage(
            //locale,
            "NetDeviceBondingNode.alertMessage.counts",
            upCount,
            downCount
        );

        return new AlertLevelAndMessage(alertLevel, alertMessage);
    }
}
