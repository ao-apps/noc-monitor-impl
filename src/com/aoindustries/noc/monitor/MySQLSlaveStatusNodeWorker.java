/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.FailoverMySQLReplication;
import com.aoindustries.aoserv.client.MySQLServer;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.MySQLReplicationResult;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author  AO Industries, Inc.
 */
class MySQLSlaveStatusNodeWorker extends TableMultiResultNodeWorker<String,MySQLReplicationResult> {

    /**
     * One unique worker is made per persistence directory (and should match mysqlReplication exactly)
     */
    private static final Map<String, MySQLSlaveStatusNodeWorker> workerCache = new HashMap<String,MySQLSlaveStatusNodeWorker>();
    static MySQLSlaveStatusNodeWorker getWorker(File persistenceDirectory, FailoverMySQLReplication mysqlReplication) throws IOException {
        File persistenceFile = new File(persistenceDirectory, "slave_status");
        String path = persistenceFile.getCanonicalPath();
        synchronized(workerCache) {
            MySQLSlaveStatusNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new MySQLSlaveStatusNodeWorker(persistenceFile, mysqlReplication);
                workerCache.put(path, worker);
            } else {
                if(!worker._mysqlReplication.equals(mysqlReplication)) throw new AssertionError("worker.mysqlReplication!=mysqlReplication: "+worker._mysqlReplication+"!="+mysqlReplication);
            }
            return worker;
        }
    }

    final private FailoverMySQLReplication _mysqlReplication;
    private FailoverMySQLReplication currentFailoverMySQLReplication;

    private MySQLSlaveStatusNodeWorker(File persistenceFile, FailoverMySQLReplication mysqlReplication) throws IOException {
        super(persistenceFile, new MySQLReplicationResultSerializer());
        this._mysqlReplication = currentFailoverMySQLReplication = mysqlReplication;
    }

    @Override
    protected int getHistorySize() {
        return 1000;
    }

    @Override
    protected List<String> getRowData(Locale locale) throws Exception {
        // Get the latest values
        currentFailoverMySQLReplication = _mysqlReplication.getTable().get(_mysqlReplication.getKey());
        FailoverMySQLReplication.SlaveStatus slaveStatus = currentFailoverMySQLReplication.getSlaveStatus();
        if(slaveStatus==null) throw new SQLException(ApplicationResourcesAccessor.getMessage(locale, "MySQLSlaveStatusNodeWorker.slaveNotRunning"));
        MySQLServer.MasterStatus masterStatus = _mysqlReplication.getMySQLServer().getMasterStatus();
        if(masterStatus==null) throw new SQLException(ApplicationResourcesAccessor.getMessage(locale, "MySQLSlaveStatusNodeWorker.masterNotRunning"));
        // Display the alert thresholds
        int secondsBehindLow = currentFailoverMySQLReplication.getMonitoringSecondsBehindLow();
        int secondsBehindMedium = currentFailoverMySQLReplication.getMonitoringSecondsBehindMedium();
        int secondsBehindHigh = currentFailoverMySQLReplication.getMonitoringSecondsBehindHigh();
        int secondsBehindCritical = currentFailoverMySQLReplication.getMonitoringSecondsBehindCritical();
        String alertThresholds =
            (secondsBehindLow==-1 ? "-" : Integer.toString(secondsBehindLow))
            + " / "
            + (secondsBehindMedium==-1 ? "-" : Integer.toString(secondsBehindMedium))
            + " / "
            + (secondsBehindHigh==-1 ? "-" : Integer.toString(secondsBehindHigh))
            + " / "
            + (secondsBehindCritical==-1 ? "-" : Integer.toString(secondsBehindCritical))
        ;

        List<String> rowData = new ArrayList<String>(11);
        rowData.add(slaveStatus.getSecondsBehindMaster());
        rowData.add(masterStatus.getFile());
        rowData.add(masterStatus.getPosition());
        rowData.add(slaveStatus.getSlaveIOState());
        rowData.add(slaveStatus.getMasterLogFile());
        rowData.add(slaveStatus.getReadMasterLogPos());
        rowData.add(slaveStatus.getSlaveIORunning());
        rowData.add(slaveStatus.getSlaveSQLRunning());
        rowData.add(slaveStatus.getLastErrno());
        rowData.add(slaveStatus.getLastError());
        rowData.add(alertThresholds);
        return rowData;
    }

    @Override
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, List<? extends String> rowData, Iterable<? extends MySQLReplicationResult> previousResults) throws Exception {
        String secondsBehindMaster = (String)rowData.get(0);
        if(secondsBehindMaster==null) {
            // Use the highest alert level that may be returned for this replication
            AlertLevel alertLevel;
            if(currentFailoverMySQLReplication.getMonitoringSecondsBehindCritical()!=-1) alertLevel = AlertLevel.CRITICAL;
            else if(currentFailoverMySQLReplication.getMonitoringSecondsBehindHigh()!=-1) alertLevel = AlertLevel.HIGH;
            else if(currentFailoverMySQLReplication.getMonitoringSecondsBehindMedium()!=-1) alertLevel = AlertLevel.MEDIUM;
            else if(currentFailoverMySQLReplication.getMonitoringSecondsBehindLow()!=-1) alertLevel = AlertLevel.LOW;
            else alertLevel = AlertLevel.NONE;

            return new AlertLevelAndMessage(
                alertLevel,
                ApplicationResourcesAccessor.getMessage(
                    locale,
                    "MySQLSlaveStatusNodeWorker.alertMessage.secondsBehindMaster.null"
                )
            );
        }
        try {
            int secondsBehind = Integer.parseInt(secondsBehindMaster);
            int secondsBehindCritical = currentFailoverMySQLReplication.getMonitoringSecondsBehindCritical();
            if(secondsBehindCritical!=-1 && secondsBehind>=secondsBehindCritical) {
                return new AlertLevelAndMessage(
                    AlertLevel.CRITICAL,
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "MySQLSlaveStatusNodeWorker.alertMessage.critical",
                        secondsBehindCritical,
                        secondsBehind
                    )
                );
            }
            int secondsBehindHigh = currentFailoverMySQLReplication.getMonitoringSecondsBehindHigh();
            if(secondsBehindHigh!=-1 && secondsBehind>=secondsBehindHigh) {
                return new AlertLevelAndMessage(
                    AlertLevel.HIGH,
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "MySQLSlaveStatusNodeWorker.alertMessage.high",
                        secondsBehindHigh,
                        secondsBehind
                    )
                );
            }
            int secondsBehindMedium = currentFailoverMySQLReplication.getMonitoringSecondsBehindMedium();
            if(secondsBehindMedium!=-1 && secondsBehind>=secondsBehindMedium) {
                return new AlertLevelAndMessage(
                    AlertLevel.MEDIUM,
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "MySQLSlaveStatusNodeWorker.alertMessage.medium",
                        secondsBehindMedium,
                        secondsBehind
                    )
                );
            }
            int secondsBehindLow = currentFailoverMySQLReplication.getMonitoringSecondsBehindLow();
            if(secondsBehindLow!=-1 && secondsBehind>=secondsBehindLow) {
                return new AlertLevelAndMessage(
                    AlertLevel.LOW,
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "MySQLSlaveStatusNodeWorker.alertMessage.low",
                        secondsBehindLow,
                        secondsBehind
                    )
                );
            }
            if(secondsBehindLow==-1) {
                return new AlertLevelAndMessage(
                    AlertLevel.NONE,
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "MySQLSlaveStatusNodeWorker.alertMessage.notAny",
                        secondsBehind
                    )
                );
            } else {
                return new AlertLevelAndMessage(
                    AlertLevel.NONE,
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "MySQLSlaveStatusNodeWorker.alertMessage.none",
                        secondsBehindLow,
                        secondsBehind
                    )
                );
            }
        } catch(NumberFormatException err) {
            return new AlertLevelAndMessage(
                AlertLevel.CRITICAL,
                ApplicationResourcesAccessor.getMessage(
                    locale,
                    "MySQLSlaveStatusNodeWorker.alertMessage.secondsBehindMaster.invalid",
                    secondsBehindMaster
                )
            );
        }
    }

    @Override
    protected MySQLReplicationResult newTableMultiResult(long time, long latency, AlertLevel alertLevel, String error) {
        return new MySQLReplicationResult(time, latency, alertLevel, error);
    }

    @Override
    protected MySQLReplicationResult newTableMultiResult(long time, long latency, AlertLevel alertLevel, List<? extends String> rowData) {
        return new MySQLReplicationResult(
            time,
            latency,
            alertLevel,
            rowData.get(0),
            rowData.get(1),
            rowData.get(2),
            rowData.get(3),
            rowData.get(4),
            rowData.get(5),
            rowData.get(6),
            rowData.get(7),
            rowData.get(8),
            rowData.get(9),
            rowData.get(10)
        );
    }
}
