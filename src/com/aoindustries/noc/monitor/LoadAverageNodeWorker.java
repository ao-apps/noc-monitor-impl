/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.TableMultiResult;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author  AO Industries, Inc.
 */
class LoadAverageNodeWorker extends TableMultiResultNodeWorker {

    /**
     * One unique worker is made per persistence directory (and should match aoServer exactly)
     */
    private static final Map<String, LoadAverageNodeWorker> workerCache = new HashMap<String,LoadAverageNodeWorker>();
    static LoadAverageNodeWorker getWorker(File persistenceDirectory, AOServer aoServer) throws IOException {
        String path = persistenceDirectory.getCanonicalPath();
        synchronized(workerCache) {
            LoadAverageNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new LoadAverageNodeWorker(persistenceDirectory, aoServer);
                workerCache.put(path, worker);
            } else {
                if(!worker._aoServer.equals(aoServer)) throw new AssertionError("worker.aoServer!=aoServer: "+worker._aoServer+"!="+aoServer);
            }
            return worker;
        }
    }

    final private AOServer _aoServer;
    private AOServer currentAOServer;

    private LoadAverageNodeWorker(File persistenceDirectory, AOServer aoServer) {
        super(new File(persistenceDirectory, "loadavg"), new File(persistenceDirectory, "loadavg.new"), false);
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
        // Display the alert thresholds
        float loadLow = currentAOServer.getMonitoringLoadLow();
        float loadMedium = currentAOServer.getMonitoringLoadMedium();
        float loadHigh = currentAOServer.getMonitoringLoadHigh();
        float loadCritical = currentAOServer.getMonitoringLoadCritical();
        String alertThresholds =
            (Float.isNaN(loadLow) ? "-" : Float.toString(loadLow))
            + " / "
            + (Float.isNaN(loadMedium) ? "-" : Float.toString(loadMedium))
            + " / "
            + (Float.isNaN(loadHigh) ? "-" : Float.toString(loadHigh))
            + " / "
            + (Float.isNaN(loadCritical) ? "-" : Float.toString(loadCritical))
        ;
        List<Object> rowData = new ArrayList<Object>(7);
        rowData.add(Float.parseFloat(loadavg.substring(0, pos1)));
        rowData.add(Float.parseFloat(loadavg.substring(pos1+1, pos2)));
        rowData.add(Float.parseFloat(loadavg.substring(pos2+1, pos3)));
        rowData.add(Integer.parseInt(loadavg.substring(pos3+1, pos4)));
        rowData.add(Integer.parseInt(loadavg.substring(pos4+1, pos5)));
        rowData.add(Integer.parseInt(loadavg.substring(pos5+1).trim()));
        rowData.add(alertThresholds);
        return Collections.unmodifiableList(rowData);
    }

    @Override
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, List<?> rowData, LinkedList<TableMultiResult> previousResults) throws Exception {
        float fiveMinuteAverage = (Float)rowData.get(1);
        float loadCritical = currentAOServer.getMonitoringLoadCritical();
        if(!Float.isNaN(loadCritical) && fiveMinuteAverage>=loadCritical) {
            return new AlertLevelAndMessage(
                AlertLevel.CRITICAL,
                ApplicationResourcesAccessor.getMessage(
                    locale,
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
                ApplicationResourcesAccessor.getMessage(
                    locale,
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
                ApplicationResourcesAccessor.getMessage(
                    locale,
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
                ApplicationResourcesAccessor.getMessage(
                    locale,
                    "LoadAverageNodeWorker.alertMessage.low",
                    loadLow,
                    fiveMinuteAverage
                )
            );
        }
        return new AlertLevelAndMessage(
            AlertLevel.NONE,
            ApplicationResourcesAccessor.getMessage(
                locale,
                "LoadAverageNodeWorker.alertMessage.none",
                loadLow,
                fiveMinuteAverage
            )
        );
    }
}
