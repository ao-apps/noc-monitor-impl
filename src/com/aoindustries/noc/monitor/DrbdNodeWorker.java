/*
 * Copyright 2008-2013 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.aoserv.client.AOServer.DrbdReport;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TableResult;
import java.io.File;
import java.io.IOException;
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
class DrbdNodeWorker extends TableResultNodeWorker<List<DrbdReport>,String> {

    /**
     * One unique worker is made per persistence file (and should match the aoServer exactly)
     */
    private static final Map<String, DrbdNodeWorker> workerCache = new HashMap<String,DrbdNodeWorker>();
    static DrbdNodeWorker getWorker(File persistenceFile, AOServer aoServer) throws IOException {
        String path = persistenceFile.getCanonicalPath();
        synchronized(workerCache) {
            DrbdNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new DrbdNodeWorker(persistenceFile, aoServer);
                workerCache.put(path, worker);
            } else {
                if(!worker.aoServer.equals(aoServer)) throw new AssertionError("worker.aoServer!=aoServer: "+worker.aoServer+"!="+aoServer);
            }
            return worker;
        }
    }

    // Will use whichever connector first created this worker, even if other accounts connect later.
    final private AOServer aoServer;

    DrbdNodeWorker(File persistenceFile, AOServer aoServer) {
        super(persistenceFile);
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
            for(int index=0,len=tableData.size();index<len;index+=5) {
                AlertLevel alertLevel = alertLevels.get(index/5);
                if(alertLevel.compareTo(highestAlertLevel)>0) {
                    highestAlertLevel = alertLevel;
                    highestAlertMessage = tableData.get(index)+" "+tableData.get(index+1)+" "+tableData.get(index+2)+" "+tableData.get(index+3)+" "+tableData.get(index+4);
                }
            }
        }
        return new AlertLevelAndMessage(highestAlertLevel, highestAlertMessage);
    }

    @Override
    protected int getColumns() {
        return 5;
    }

    @Override
    protected List<String> getColumnHeaders(Locale locale) {
        List<String> columnHeaders = new ArrayList<String>(5);
        columnHeaders.add(accessor.getMessage(/*locale,*/ "DrbdNodeWorker.columnHeader.device"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "DrbdNodeWorker.columnHeader.resource"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "DrbdNodeWorker.columnHeader.cs"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "DrbdNodeWorker.columnHeader.ds"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "DrbdNodeWorker.columnHeader.st"));
        return columnHeaders;
    }

    @Override
    protected List<DrbdReport> getQueryResult(Locale locale) throws Exception {
        return aoServer.getDrbdReport();
    }

    @Override
    protected List<String> getTableData(List<DrbdReport> reports, Locale locale) throws Exception {
        List<String> tableData = new ArrayList<String>(reports.size()*5);
        for(DrbdReport report : reports) {
            tableData.add(report.getDevice());
            tableData.add(report.getResourceHostname()+'-'+report.getResourceDevice());
            tableData.add(report.getConnectionState().toString());
            tableData.add(report.getLocalDiskState()+"/"+report.getRemoteDiskState());
            tableData.add(report.getLocalRole()+"/"+report.getRemoteRole());
        }
        return tableData;
    }

    @Override
    protected List<AlertLevel> getAlertLevels(List<DrbdReport> reports) {
        List<AlertLevel> alertLevels = new ArrayList<AlertLevel>(reports.size());
        for(DrbdReport report : reports) {
			DrbdReport.ConnectionState connectionState = report.getConnectionState();
            AlertLevel alertLevel;
            if(
                (
					connectionState!=DrbdReport.ConnectionState.Connected
					&& connectionState!=DrbdReport.ConnectionState.VerifyS
					&& connectionState!=DrbdReport.ConnectionState.VerifyT
				) || report.getLocalDiskState()!=DrbdReport.DiskState.UpToDate
                || report.getRemoteDiskState()!=DrbdReport.DiskState.UpToDate
                || !(
                    (report.getLocalRole()==DrbdReport.Role.Primary && report.getRemoteRole()==DrbdReport.Role.Secondary)
                    || (report.getLocalRole()==DrbdReport.Role.Secondary && report.getRemoteRole()==DrbdReport.Role.Primary)
                )
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
