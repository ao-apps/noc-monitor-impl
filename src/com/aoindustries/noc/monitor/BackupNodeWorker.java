/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.aoserv.client.FailoverFileLog;
import com.aoindustries.aoserv.client.FailoverFileReplication;
import com.aoindustries.aoserv.client.Server;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.MonitoringPoint;
import com.aoindustries.noc.monitor.common.TableResult;
import com.aoindustries.noc.monitor.common.TimeWithTimeZone;
import com.aoindustries.util.StringUtility;
import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * The workers for 3ware RAID.
 *
 * @author  AO Industries, Inc.
 */
class BackupNodeWorker extends TableResultNodeWorker<List<FailoverFileLog>,Object> {

    private static final int HISTORY_SIZE = 100;

    /**
     * One unique worker is made per persistence file (and should match the failoverFileReplication exactly)
     */
    private static final Map<String, BackupNodeWorker> workerCache = new HashMap<String,BackupNodeWorker>();
    static BackupNodeWorker getWorker(MonitoringPoint monitoringPoint, File persistenceFile, FailoverFileReplication failoverFileReplication) throws IOException {
        String path = persistenceFile.getCanonicalPath();
        synchronized(workerCache) {
            BackupNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new BackupNodeWorker(monitoringPoint, persistenceFile, failoverFileReplication);
                workerCache.put(path, worker);
            } else {
                if(!worker.failoverFileReplication.equals(failoverFileReplication)) throw new AssertionError("worker.failoverFileReplication!=failoverFileReplication: "+worker.failoverFileReplication+"!="+failoverFileReplication);
            }
            return worker;
        }
    }

    // Will use whichever connector first created this worker, even if other accounts connect later.
    final private FailoverFileReplication failoverFileReplication;

    BackupNodeWorker(MonitoringPoint monitoringPoint, File persistenceFile, FailoverFileReplication failoverFileReplication) {
        super(monitoringPoint, persistenceFile);
        this.failoverFileReplication = failoverFileReplication;
    }

    /**
     * Determines the alert message for the provided result.
     * 
     * If there is not any data (no backups logged, make high level)
     */
    @Override
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, TableResult result) {
        AlertLevel highestAlertLevel = AlertLevel.NONE;
        String highestAlertMessage = "";
        List<?> tableData = result.getTableData();
        if(result.isError()) {
            highestAlertLevel = result.getAlertLevels().get(0);
            highestAlertMessage = tableData.get(0).toString();
        } else if(tableData.isEmpty()) {
            highestAlertLevel = AlertLevel.MEDIUM;
            highestAlertMessage = accessor.getMessage(/*locale,*/ "BackupNodeWorker.noBackupPassesLogged");
        } else {
            // We try to find the most recent successful pass
            // If <30 hours NONE
            // if <48 hours LOW
            // otherwise MEDIUM
            long lastSuccessfulTime = -1;
            for(int index=0,len=tableData.size();index<len;index+=6) {
                boolean successful = (Boolean)tableData.get(index+5);
                if(successful) {
                    lastSuccessfulTime = ((TimeWithTimeZone)tableData.get(index)).getTime();
                    break;
                }
            }
            if(lastSuccessfulTime==-1) {
                // No success found, is MEDIUM
                highestAlertLevel = AlertLevel.MEDIUM;
                highestAlertMessage = accessor.getMessage(/*locale,*/ "BackupNodeWorker.noSuccessfulPassesFound", result.getRows());
            } else {
                long hoursSince = (System.currentTimeMillis() - lastSuccessfulTime)/((long)60*60*1000);
                if(hoursSince<0) {
                    highestAlertLevel = AlertLevel.CRITICAL;
                    highestAlertMessage = accessor.getMessage(/*locale,*/ "BackupNodeWorker.lastSuccessfulPassInFuture");
                } else {
                    if(hoursSince<30) {
                        highestAlertLevel = AlertLevel.NONE;
                    } else if(hoursSince<48) {
                        highestAlertLevel = AlertLevel.LOW;
                    } else {
                        highestAlertLevel = AlertLevel.MEDIUM;
                    }
                    if(hoursSince<=48) {
                        highestAlertMessage = accessor.getMessage(/*locale,*/ "BackupNodeWorker.lastSuccessfulPass", hoursSince);
                    } else {
                        long days = hoursSince / 24;
                        long hours = hoursSince % 24;
                        highestAlertMessage = accessor.getMessage(/*locale,*/ "BackupNodeWorker.lastSuccessfulPassDays", days, hours);
                    }
                }
            }
            // We next see if the last pass failed - if so this will be considered low priority (higher priority is time-based above)
            boolean lastSuccessful = (Boolean)tableData.get(5);
            if(!lastSuccessful) {
                if(AlertLevel.LOW.compareTo(highestAlertLevel)>0) {
                    highestAlertLevel = AlertLevel.LOW;
                    highestAlertMessage = accessor.getMessage(/*locale,*/ "BackupNodeWorker.lastPassNotSuccessful");
                }
            }
        }

        return new AlertLevelAndMessage(highestAlertLevel, highestAlertMessage);
    }

    @Override
    protected int getColumns() {
        return 6;
    }

    @Override
    protected List<String> getColumnHeaders(Locale locale) {
        List<String> columnHeaders = new ArrayList<String>(6);
        columnHeaders.add(accessor.getMessage(/*locale,*/ "BackupNodeWorker.columnHeader.startTime"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "BackupNodeWorker.columnHeader.duration"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "BackupNodeWorker.columnHeader.scanned"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "BackupNodeWorker.columnHeader.updated"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "BackupNodeWorker.columnHeader.bytes"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "BackupNodeWorker.columnHeader.successful"));
        return columnHeaders;
    }

    @Override
    protected List<FailoverFileLog> getQueryResult(Locale locale) throws Exception {
        return failoverFileReplication.getFailoverFileLogs(HISTORY_SIZE);
    }

    @Override
    protected List<Object> getTableData(List<FailoverFileLog> failoverFileLogs, Locale locale) throws Exception {
        if(failoverFileLogs.isEmpty()) {
            return Collections.emptyList();
        } else {
            Server server = failoverFileReplication.getServer();
            AOServer aoServer = server.getAOServer();
            TimeZone timeZone = aoServer==null ? null : aoServer.getTimeZone().getTimeZone();

            List<Object> tableData = new ArrayList<Object>(failoverFileLogs.size()*6);
            int lineNum = 0;
            for(FailoverFileLog failoverFileLog : failoverFileLogs) {
                lineNum++;
                Timestamp startTime = failoverFileLog.getStartTime();
                tableData.add(new TimeWithTimeZone(startTime.getTime(), timeZone));
                tableData.add(StringUtility.getTimeLengthString(failoverFileLog.getEndTime().getTime() - startTime.getTime()));
                tableData.add(failoverFileLog.getScanned());
                tableData.add(failoverFileLog.getUpdated());
                tableData.add(StringUtility.getApproximateSize(failoverFileLog.getBytes()));
                tableData.add(failoverFileLog.isSuccessful());
            }
            return tableData;
        }
    }

    @Override
    protected List<AlertLevel> getAlertLevels(List<FailoverFileLog> queryResult) {
        List<AlertLevel> alertLevels = new ArrayList<AlertLevel>(queryResult.size());
        for(FailoverFileLog failoverFileLog : queryResult) {
            // If pass failed then it is HIGH, otherwise it is NONE
            alertLevels.add(failoverFileLog.isSuccessful() ? AlertLevel.NONE : AlertLevel.MEDIUM);
        }
        return alertLevels;
    }
}
