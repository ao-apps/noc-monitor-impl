/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.ApproximateDisplayExactSize;
import com.aoindustries.noc.common.TableMultiResult;
import com.aoindustries.util.ErrorHandler;
import com.aoindustries.util.StringUtility;
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
 * Memory + Swap space - checked every minute.
 *      memory just informational
 *      swap controls alert
 *          &gt;=95% Critical
 *          &gt;=90% High
 *          &gt;=80% Medium
 *          &gt;=70% Low
 *          &lt;70%  None
 *
 * @author  AO Industries, Inc.
 */
class MemoryNodeWorker extends TableMultiResultNodeWorker {

    /**
     * One unique worker is made per persistence directory (and should match aoServer exactly)
     */
    private static final Map<String, MemoryNodeWorker> workerCache = new HashMap<String,MemoryNodeWorker>();
    static MemoryNodeWorker getWorker(ErrorHandler errorHandler, File persistenceDirectory, AOServer aoServer) throws IOException {
        String path = persistenceDirectory.getCanonicalPath();
        synchronized(workerCache) {
            MemoryNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new MemoryNodeWorker(errorHandler, persistenceDirectory, aoServer);
                workerCache.put(path, worker);
            } else {
                if(!worker._aoServer.equals(aoServer)) throw new AssertionError("worker.aoServer!=aoServer: "+worker._aoServer+"!="+aoServer);
            }
            return worker;
        }
    }

    final private AOServer _aoServer;
    private AOServer currentAOServer;

    private MemoryNodeWorker(ErrorHandler errorHandler, File persistenceDirectory, AOServer aoServer) {
        super(errorHandler, new File(persistenceDirectory, "meminfo"), new File(persistenceDirectory, "meminfo.new"), false);
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
        String meminfo = currentAOServer.getMemInfoReport();
        long memTotal = -1;
        long memFree = -1;
        long buffers = -1;
        long cached = -1;
        long swapTotal = -1;
        long swapFree = -1;
        
        List<String> lines = StringUtility.splitLines(meminfo);
        for(String line : lines) {
            if(line.endsWith(" kB")) { //throw new ParseException("Line doesn't end with \" kB\": "+line, 0);
                line = line.substring(0, line.length()-3);
                if(line.startsWith("MemTotal:")) memTotal = Long.parseLong(line.substring("MemTotal:".length()).trim()) << 10; // * 1024
                else if(line.startsWith("MemFree:")) memFree = Long.parseLong(line.substring("MemFree:".length()).trim()) << 10;
                else if(line.startsWith("Buffers:")) buffers = Long.parseLong(line.substring("Buffers:".length()).trim()) << 10;
                else if(line.startsWith("Cached:")) cached = Long.parseLong(line.substring("Cached:".length()).trim()) << 10;
                else if(line.startsWith("SwapTotal:")) swapTotal = Long.parseLong(line.substring("SwapTotal:".length()).trim()) << 10;
                else if(line.startsWith("SwapFree:")) swapFree = Long.parseLong(line.substring("SwapFree:".length()).trim()) << 10;
            }
        }
        if(memTotal==-1) throw new ParseException("Unable to find MemTotal:", 0);
        if(memFree==-1) throw new ParseException("Unable to find MemFree:", 0);
        if(buffers==-1) throw new ParseException("Unable to find Buffers:", 0);
        if(cached==-1) throw new ParseException("Unable to find Cached:", 0);
        if(swapTotal==-1) throw new ParseException("Unable to find SwapTotal:", 0);
        if(swapFree==-1) throw new ParseException("Unable to find SwapFree:", 0);
        
        List<ApproximateDisplayExactSize> rowData = new ArrayList<ApproximateDisplayExactSize>(6);
        rowData.add(new ApproximateDisplayExactSize(memTotal));
        rowData.add(new ApproximateDisplayExactSize(memFree));
        rowData.add(new ApproximateDisplayExactSize(buffers));
        rowData.add(new ApproximateDisplayExactSize(cached));
        rowData.add(new ApproximateDisplayExactSize(swapTotal));
        rowData.add(new ApproximateDisplayExactSize(swapFree));
        return Collections.unmodifiableList(rowData);
    }

    private static AlertLevel getAlertLevel(long swapUsedPercent) {
        if(swapUsedPercent<0) return AlertLevel.UNKNOWN;
        if(swapUsedPercent>=95) return AlertLevel.CRITICAL;
        if(swapUsedPercent>=90) return AlertLevel.HIGH;
        if(swapUsedPercent>=80) return AlertLevel.MEDIUM;
        if(swapUsedPercent>=70) return AlertLevel.LOW;
        return AlertLevel.NONE;
    }

    @Override
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, List<?> rowData, LinkedList<TableMultiResult> previousResults) throws Exception {
        long swapTotal = ((ApproximateDisplayExactSize)rowData.get(4)).getSize();
        long swapFree = ((ApproximateDisplayExactSize)rowData.get(5)).getSize();
        long swapUsedPercent = (swapTotal - swapFree) * 100 / swapTotal;
        return new AlertLevelAndMessage(
            getAlertLevel(swapUsedPercent),
            ApplicationResourcesAccessor.getMessage(
                locale,
                "MemoryNodeWorker.alertMessage",
                swapUsedPercent
            )
        );
    }
}
