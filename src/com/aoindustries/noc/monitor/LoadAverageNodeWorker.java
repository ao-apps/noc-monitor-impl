/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.LoadAverageResult;
import com.aoindustries.noc.monitor.common.MonitoringPoint;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author  AO Industries, Inc.
 */
class LoadAverageNodeWorker extends TableMultiResultNodeWorker<List<Object>,LoadAverageResult> {

    /**
     * One unique worker is made per persistence directory (and should match aoServer exactly)
     */
    private static final Map<String, LoadAverageNodeWorker> workerCache = new HashMap<String,LoadAverageNodeWorker>();
    static LoadAverageNodeWorker getWorker(MonitoringPoint monitoringPoint, File persistenceDirectory, AOServer aoServer) throws IOException {
        String path = persistenceDirectory.getCanonicalPath();
        synchronized(workerCache) {
            LoadAverageNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new LoadAverageNodeWorker(monitoringPoint, persistenceDirectory, aoServer);
                workerCache.put(path, worker);
            } else {
                if(!worker._aoServer.equals(aoServer)) throw new AssertionError("worker.aoServer!=aoServer: "+worker._aoServer+"!="+aoServer);
            }
            return worker;
        }
    }

    final private AOServer _aoServer;
    private AOServer currentAOServer;

    private LoadAverageNodeWorker(MonitoringPoint monitoringPoint, File persistenceDirectory, AOServer aoServer) throws IOException {
        super(monitoringPoint, new File(persistenceDirectory, "loadavg"), new LoadAverageResultSerializer(monitoringPoint));
        this._aoServer = currentAOServer = aoServer;
    }

    @Override
    protected int getHistorySize() {
        return 2000;
    }

    @Override
    protected List<Object> getSample(Locale locale) throws Exception {
        // Get the latest limits
        currentAOServer = _aoServer.getTable().get(_aoServer.getKey());
        String loadavg = currentAOServer.getLoadAvgReport();
        int pos1 = loadavg.indexOf(' ');
        if(pos1==-1) throw new ParseException("Unable to find first space in loadavg", 0);
        int pos2 = loadavg.indexOf(' ', pos1+1);
        if(pos2==-1) throw new ParseException("Unable to find second space in loadavg", pos1+1);
        int pos3 = loadavg.indexOf(' ', pos2+1);
        if(pos3==-1) throw new ParseException("Unable to find third space in loadavg", pos2+1);
        int pos4 = loadavg.indexOf('/', pos3+1);
        if(pos4==-1) throw new ParseException("Unable to find slash in loadavg", pos3+1);
        int pos5 = loadavg.indexOf(' ', pos4+1);
        if(pos5==-1) throw new ParseException("Unable to find fourth space in loadavg", pos4+1);
        List<Object> sample = new ArrayList<Object>(10);
        sample.add(Float.parseFloat(loadavg.substring(0, pos1)));
        sample.add(Float.parseFloat(loadavg.substring(pos1+1, pos2)));
        sample.add(Float.parseFloat(loadavg.substring(pos2+1, pos3)));
        sample.add(Integer.parseInt(loadavg.substring(pos3+1, pos4)));
        sample.add(Integer.parseInt(loadavg.substring(pos4+1, pos5)));
        sample.add(Integer.parseInt(loadavg.substring(pos5+1).trim()));
        sample.add(currentAOServer.getMonitoringLoadLow());
        sample.add(currentAOServer.getMonitoringLoadMedium());
        sample.add(currentAOServer.getMonitoringLoadHigh());
        sample.add(currentAOServer.getMonitoringLoadCritical());
        return sample;
    }

    @Override
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, List<Object> sample, Iterable<? extends LoadAverageResult> previousResults) throws Exception {
        float fiveMinuteAverage = (Float)sample.get(1);
        float loadCritical = currentAOServer.getMonitoringLoadCritical();
        if(!Float.isNaN(loadCritical) && fiveMinuteAverage>=loadCritical) {
            return new AlertLevelAndMessage(
                AlertLevel.CRITICAL,
                accessor.getMessage(
                    //locale,
                    "LoadAverageNodeWorker.alertMessage.critical",
                    loadCritical,
                    fiveMinuteAverage
                )
            );
        }
        float loadHigh = currentAOServer.getMonitoringLoadHigh();
        if(!Float.isNaN(loadHigh) && fiveMinuteAverage>=loadHigh) {
            return new AlertLevelAndMessage(
                AlertLevel.HIGH,
                accessor.getMessage(
                    //locale,
                    "LoadAverageNodeWorker.alertMessage.high",
                    loadHigh,
                    fiveMinuteAverage
                )
            );
        }
        float loadMedium = currentAOServer.getMonitoringLoadMedium();
        if(!Float.isNaN(loadMedium) && fiveMinuteAverage>=loadMedium) {
            return new AlertLevelAndMessage(
                AlertLevel.MEDIUM,
                accessor.getMessage(
                    //locale,
                    "LoadAverageNodeWorker.alertMessage.medium",
                    loadMedium,
                    fiveMinuteAverage
                )
            );
        }
        float loadLow = currentAOServer.getMonitoringLoadLow();
        if(!Float.isNaN(loadLow) && fiveMinuteAverage>=loadLow) {
            return new AlertLevelAndMessage(
                AlertLevel.LOW,
                accessor.getMessage(
                    //locale,
                    "LoadAverageNodeWorker.alertMessage.low",
                    loadLow,
                    fiveMinuteAverage
                )
            );
        }
        if(Float.isNaN(loadLow)) {
            return new AlertLevelAndMessage(
                AlertLevel.NONE,
                accessor.getMessage(
                    //locale,
                    "LoadAverageNodeWorker.alertMessage.notAny",
                    fiveMinuteAverage
                )
            );
        } else {
            return new AlertLevelAndMessage(
                AlertLevel.NONE,
                accessor.getMessage(
                    //locale,
                    "LoadAverageNodeWorker.alertMessage.none",
                    loadLow,
                    fiveMinuteAverage
                )
            );
        }
    }

    @Override
    protected LoadAverageResult newErrorResult(long time, long latency, AlertLevel alertLevel, String error) {
        return new LoadAverageResult(monitoringPoint, time, latency, alertLevel, error);
    }

    @Override
    protected LoadAverageResult newSampleResult(long time, long latency, AlertLevel alertLevel, List<Object> sample) {
        return new LoadAverageResult(
            monitoringPoint,
            time,
            latency,
            alertLevel,
            (Float)sample.get(0),
            (Float)sample.get(1),
            (Float)sample.get(2),
            (Integer)sample.get(3),
            (Integer)sample.get(4),
            (Integer)sample.get(5),
            (Float)sample.get(6),
            (Float)sample.get(7),
            (Float)sample.get(8),
            (Float)sample.get(9)
        );
    }
}
