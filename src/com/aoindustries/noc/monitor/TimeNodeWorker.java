/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.MonitoringPoint;
import com.aoindustries.noc.monitor.common.TimeResult;
import com.aoindustries.noc.monitor.common.TimeSpan;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The clock skew for a single sample in milliseconds is calculated as follows:
 *      st: remote system time (in milliseconds from Epoch)
 *      rt: request time (in milliseconds from Epoch)
 *      l:  request latency (in nanoseconds)
 *
 *      skew = st - (rt + round(l/2000000))
 *
 * Alert levels are:
 *          &gt;=1 minute  Critical
 *          &gt;=4 seconds High
 *          &gt;=2 seconds Medium
 *          &gt;=1 second  Low
 *          &lt;1  second  None
 *
 * @author  AO Industries, Inc.
 */
class TimeNodeWorker extends TableMultiResultNodeWorker<TimeSpan,TimeResult> {

    /**
     * One unique worker is made per persistence directory (and should match aoServer exactly)
     */
    private static final Map<String, TimeNodeWorker> workerCache = new HashMap<String,TimeNodeWorker>();
    static TimeNodeWorker getWorker(MonitoringPoint monitoringPoint, File persistenceDirectory, AOServer aoServer) throws IOException {
        String path = persistenceDirectory.getCanonicalPath();
        synchronized(workerCache) {
            TimeNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new TimeNodeWorker(monitoringPoint, persistenceDirectory, aoServer);
                workerCache.put(path, worker);
            } else {
                if(!worker._aoServer.equals(aoServer)) throw new AssertionError("worker.aoServer!=aoServer: "+worker._aoServer+"!="+aoServer);
            }
            return worker;
        }
    }

    final private AOServer _aoServer;
    private AOServer currentAOServer;

    private TimeNodeWorker(MonitoringPoint monitoringPoint, File persistenceDirectory, AOServer aoServer) throws IOException {
        super(monitoringPoint, new File(persistenceDirectory, "time"), new TimeResultSerializer(monitoringPoint));
        this._aoServer = currentAOServer = aoServer;
    }

    @Override
    protected int getHistorySize() {
        return 2000;
    }

    @Override
    protected TimeSpan getSample(Locale locale) throws Exception {
        // Get the latest limits
        currentAOServer = _aoServer.getTable().get(_aoServer.getKey());

        long requestTime = System.currentTimeMillis();
        long startNanos = System.nanoTime();
        long systemTime = currentAOServer.getSystemTimeMillis();
        long latency = System.nanoTime() - startNanos;
        long lRemainder = latency % 2000000;
        long skew = systemTime - (requestTime + latency/2000000);
        if(lRemainder >= 1000000) skew--;

        return new TimeSpan(skew);
    }

    private static AlertLevel getAlertLevel(long skew) {
        if(skew >= 60000 || skew <= -60000) return AlertLevel.CRITICAL;
        if(skew >=  4000 || skew <=  -4000) return AlertLevel.HIGH;
        if(skew >=  2000 || skew <=  -2000) return AlertLevel.MEDIUM;
        if(skew >=  1000 || skew <=  -1000) return AlertLevel.MEDIUM;
        return AlertLevel.NONE;
    }

    @Override
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, TimeSpan sample, Iterable<? extends TimeResult> previousResults) throws Exception {
        final long currentSkew = sample.getTimeSpan();

        return new AlertLevelAndMessage(
            getAlertLevel(currentSkew),
            accessor.getMessage(
                //locale,
                "TimeNodeWorker.alertMessage",
                currentSkew
            )
        );
    }

    @Override
    protected TimeResult newErrorResult(long time, long latency, AlertLevel alertLevel, String error) {
        return new TimeResult(monitoringPoint, time, latency, alertLevel, error);
    }

    @Override
    protected TimeResult newSampleResult(long time, long latency, AlertLevel alertLevel, TimeSpan sample) {
        return new TimeResult(monitoringPoint, time, latency, alertLevel, sample.getTimeSpan());
    }
}
