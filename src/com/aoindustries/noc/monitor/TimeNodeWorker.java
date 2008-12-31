/*
 * Copyright 2008 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.TableMultiResult;
import com.aoindustries.noc.common.TimeSpan;
import com.aoindustries.util.ErrorHandler;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
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
 * When determining the effective alert level, the skew is averaged over the
 * last <code>NUM_SAMPLES_IN_AVERAGE</code> samples.
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
class TimeNodeWorker extends TableMultiResultNodeWorker {

    private static final int NUM_SAMPLES_IN_AVERAGE = 10;

    /**
     * One unique worker is made per persistence directory (and should match aoServer exactly)
     */
    private static final Map<String, TimeNodeWorker> workerCache = new HashMap<String,TimeNodeWorker>();
    static TimeNodeWorker getWorker(ErrorHandler errorHandler, File persistenceDirectory, AOServer aoServer) throws IOException {
        String path = persistenceDirectory.getCanonicalPath();
        synchronized(workerCache) {
            TimeNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new TimeNodeWorker(errorHandler, persistenceDirectory, aoServer);
                workerCache.put(path, worker);
            } else {
                if(!worker._aoServer.equals(aoServer)) throw new AssertionError("worker.aoServer!=aoServer: "+worker._aoServer+"!="+aoServer);
            }
            return worker;
        }
    }

    final private AOServer _aoServer;
    private AOServer currentAOServer;

    private TimeNodeWorker(ErrorHandler errorHandler, File persistenceDirectory, AOServer aoServer) {
        super(errorHandler, new File(persistenceDirectory, "time"), new File(persistenceDirectory, "time.new"), false);
        this._aoServer = aoServer;
    }

    @Override
    protected int getHistorySize() {
        return 1000;
    }

    @Override
    protected List<?> getRowData(Locale locale) throws Exception {
        // Get the latest limits
        currentAOServer = _aoServer.getTable().get(_aoServer.getKey());


        long requestTime = System.currentTimeMillis();
        long startNanos = System.nanoTime();
        long systemTime = currentAOServer.getSystemTimeMillis();
        long latency = System.nanoTime() - startNanos;
        long lRemainder = latency % 2000000;
        long skew = systemTime - (requestTime + latency/2000000);
        if(lRemainder >= 1000000) skew--;

        return Collections.singletonList(new TimeSpan(skew));
    }

    private static AlertLevel getAlertLevel(long skew) {
        if(skew >= 60000 || skew <= -60000) return AlertLevel.CRITICAL;
        if(skew >=  4000 || skew <=  -4000) return AlertLevel.HIGH;
        if(skew >=  2000 || skew <=  -2000) return AlertLevel.MEDIUM;
        if(skew >=  1000 || skew <=  -1000) return AlertLevel.MEDIUM;
        return AlertLevel.NONE;
    }

    @Override
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, List<?> rowData, LinkedList<TableMultiResult> previousResults) throws Exception {
        final long currentSkew = ((TimeSpan)rowData.get(0)).getTimeSpan();

        // Average the last samples for the alert level calculation
        long averageSum = currentSkew;
        int sampleCount = 1;
        Iterator<TableMultiResult> prevIter = previousResults.iterator();
        while(sampleCount<NUM_SAMPLES_IN_AVERAGE && prevIter.hasNext()) {
            TableMultiResult previous = prevIter.next();
            if(previous.getError()==null) {
                averageSum += ((TimeSpan)previous.getRowData().get(0)).getTimeSpan();
                sampleCount++;
            }
        }
        long averageSkew = averageSum / sampleCount;
        return new AlertLevelAndMessage(
            getAlertLevel(averageSkew),
            ApplicationResourcesAccessor.getMessage(
                locale,
                "TimeNodeWorker.alertMessage",
                currentSkew
            )
        );
    }
}
