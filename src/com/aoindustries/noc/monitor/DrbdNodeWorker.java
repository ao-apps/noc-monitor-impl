/*
 * Copyright 2008 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.TableResult;
import com.aoindustries.util.ErrorHandler;
import com.aoindustries.util.StringUtility;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The workers for DRBD.
 *
 * @author  AO Industries, Inc.
 */
class DrbdNodeWorker extends TableResultNodeWorker {

    /**
     * One unique worker is made per persistence file (and should match the aoServer exactly)
     */
    private static final Map<String, DrbdNodeWorker> workerCache = new HashMap<String,DrbdNodeWorker>();
    static DrbdNodeWorker getWorker(ErrorHandler errorHandler, File persistenceFile, AOServer aoServer) throws IOException {
        String path = persistenceFile.getCanonicalPath();
        synchronized(workerCache) {
            DrbdNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new DrbdNodeWorker(errorHandler, persistenceFile, aoServer);
                workerCache.put(path, worker);
            } else {
                if(!worker.aoServer.equals(aoServer)) throw new AssertionError("worker.aoServer!=aoServer: "+worker.aoServer+"!="+aoServer);
            }
            return worker;
        }
    }

    // Will use whichever connector first created this worker, even if other accounts connect later.
    final private AOServer aoServer;

    DrbdNodeWorker(ErrorHandler errorHandler, File persistenceFile, AOServer aoServer) {
        super(errorHandler, persistenceFile);
        this.aoServer = aoServer;
    }

    /**
     * Determines the alert message for the provided result.
     * 
     * @link http://www.drbd.org/users-guide/ch-admin.html#s-disk-states
     */
    @Override
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, TableResult result) {
        AlertLevel highestAlertLevel = AlertLevel.NONE;
        String highestAlertMessage = "";
        List<?> tableData = result.getTableData();
        if(result.isError()) {
            highestAlertLevel = result.getAlertLevels().get(0);
            highestAlertMessage = tableData.get(0).toString();
        } else {
            List<AlertLevel> alertLevels = result.getAlertLevels();
            for(int index=0,len=tableData.size();index<len;index+=4) {
                AlertLevel alertLevel = alertLevels.get(index/4);
                if(alertLevel.compareTo(highestAlertLevel)>0) {
                    highestAlertLevel = alertLevel;
                    highestAlertMessage = tableData.get(index)+" "+tableData.get(index+1)+" "+tableData.get(index+2)+" "+tableData.get(index+3);
                }
            }
        }
        return new AlertLevelAndMessage(highestAlertLevel, highestAlertMessage);
    }

    @Override
    protected int getColumns() {
        return 4;
    }

    @Override
    protected List<?> getColumnHeaders(Locale locale) {
        List<String> columnHeaders = new ArrayList<String>(4);
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "DrbdNodeWorker.columnHeader.device"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "DrbdNodeWorker.columnHeader.resource"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "DrbdNodeWorker.columnHeader.cs"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "DrbdNodeWorker.columnHeader.st"));
        return columnHeaders;
    }

    @Override
    protected List<?> getTableData(Locale locale) throws Exception {
        String report = aoServer.getDrbdReport();
        List<String> lines = StringUtility.splitLines(report);
        List<String> tableData = new ArrayList<String>(lines.size()*4);
        int lineNum = 0;
        for(String line : lines) {
            lineNum++;
            String[] values = StringUtility.splitString(line, '\t');
            if(values.length!=4) {
                throw new ParseException(
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "DrbdNode.alertMessage.badColumnCount",
                        line
                    ),
                    lineNum
                );
            }
            for(int c=0,len=values.length; c<len; c++) {
                tableData.add(values[c]);
            }
        }
        return tableData;
    }

    @Override
    protected List<AlertLevel> getAlertLevels(List<?> tableData) {
        List<AlertLevel> alertLevels = new ArrayList<AlertLevel>(tableData.size()/4);
        for(int index=0,len=tableData.size();index<len;index+=4) {
            AlertLevel alertLevel;
            if(
                !"Connected".equals(tableData.get(index+2))
                || !"UpToDate/UpToDate".equals(tableData.get(index+3))
            ) {
                alertLevel = AlertLevel.HIGH;
            } else {
                alertLevel = AlertLevel.NONE;
            }
            alertLevels.add(alertLevel);
        }
        return alertLevels;
    }
}
