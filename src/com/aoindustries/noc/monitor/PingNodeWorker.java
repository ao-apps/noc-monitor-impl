/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.IPAddress;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.TableMultiResult;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Each worker may be shared by any number of <code>PingNodeImpl</code>s.
 * A worker performs the background pinging and reports its results to
 * the ping nodes.  All of the cached data is stored in a Locale-neutral
 * way and converted to Locale-specific representations as needed.
 *
 * @author  AO Industries, Inc.
 */
class PingNodeWorker extends TableMultiResultNodeWorker {

    /**
     * The ping timeout.
     */
    private static final int TIMEOUT = 10000;

    /**
     * One unique worker is made per persistence directory (and should match the IP address exactly)
     */
    private static final Map<String, PingNodeWorker> workerCache = new HashMap<String,PingNodeWorker>();
    static PingNodeWorker getWorker(File persistenceDirectory, IPAddress ipAddress) throws IOException {
        String path = persistenceDirectory.getCanonicalPath();
        String ip = ipAddress.getIPAddress();
        String externalIp = ipAddress.getExternalIpAddress();
        String pingAddress = externalIp==null ? ip : externalIp;
        synchronized(workerCache) {
            PingNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new PingNodeWorker(persistenceDirectory, pingAddress);
                workerCache.put(path, worker);
            } else {
                if(!worker.ipAddress.equals(pingAddress)) throw new AssertionError("worker.ipAddress!=pingAddress: "+worker.ipAddress+"!="+pingAddress);
            }
            return worker;
        }
    }

    /**
     * The most recent timer task
     */
    final private String ipAddress;

    private PingNodeWorker(File persistenceDirectory, String ipAddress) {
        super(new File(persistenceDirectory, "pings"), new File(persistenceDirectory, "pings.new"), false);
        this.ipAddress = ipAddress;
    }

    @Override
    protected int getHistorySize() {
        return 1000;
    }

    @Override
    protected List<?> getRowData(Locale locale) throws Exception {
        final InetAddress inetAddress = InetAddress.getByName(ipAddress);
        boolean timeout = !inetAddress.isReachable(TIMEOUT);
        if(timeout) throw new TimeoutException(ApplicationResourcesAccessor.getMessage(locale, "PingNodeWorker.error.timeout"));
        return Collections.emptyList();
    }

    /**
     * Figures out the alert level.  It considers only the last 10 pings.  The number of timeouts follow:
     *
     * >=4  CRITICAL
     * >=3  HIGH
     * >=2  MEDIUM
     * >=1  LOW
     * =0   NONE
     */
    private static AlertLevel getAlertLevel(int packetLossPercent) {
        if(packetLossPercent<0) return AlertLevel.UNKNOWN;
        if(packetLossPercent>=40) return AlertLevel.CRITICAL;
        if(packetLossPercent>=30) return AlertLevel.HIGH;
        if(packetLossPercent>=20) return AlertLevel.MEDIUM;
        if(packetLossPercent>=10) return AlertLevel.LOW;
        return AlertLevel.NONE;
    }

    /**
     * Gets the packet loss percent.
     */
    private static int getPacketLossPercent(List<TableMultiResult> previousResults) {
        int timeouts = 0;
        // The current value is never a timeout to get this far
        int checked = 1;
        // The history
        for(TableMultiResult previousResult : previousResults) {
            if(previousResult.getError()!=null) timeouts++;
            checked++;
            if(checked>=10) break;
        }
        return timeouts * 10;
    }

    @Override
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, List<?> rowData, LinkedList<TableMultiResult> previousResults) throws Exception {
        int packetLossPercent = getPacketLossPercent(previousResults);
        return new AlertLevelAndMessage(
            getAlertLevel(packetLossPercent),
            ApplicationResourcesAccessor.getMessage(
                locale,
                "PingNodeWorker.alertMessage",
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
}
