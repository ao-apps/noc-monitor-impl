/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.FailoverMySQLReplication;
import com.aoindustries.aoserv.client.MySQLServer;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.TableMultiResult;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
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
class MySQLReplicationNodeWorker extends TableMultiResultNodeWorker {

    /**
     * The seconds behind that will trigger each alert level.
     */
    private static final int
        SECONDS_BEHIND_CRITICAL = 60,
        SECONDS_BEHIND_HIGH = 30,
        SECONDS_BEHIND_MEDIUM = 15,
        SECONDS_BEHIND_LOW = 5
    ;

    /**
     * One unique worker is made per persistence directory (and should match mysqlReplication exactly)
     */
    private static final Map<String, MySQLReplicationNodeWorker> workerCache = new HashMap<String,MySQLReplicationNodeWorker>();
    static MySQLReplicationNodeWorker getWorker(File persistenceDirectory, FailoverMySQLReplication mysqlReplication) throws IOException {
        String path = persistenceDirectory.getCanonicalPath();
        synchronized(workerCache) {
            MySQLReplicationNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new MySQLReplicationNodeWorker(persistenceDirectory, mysqlReplication);
                workerCache.put(path, worker);
            } else {
                if(!worker._mysqlReplication.equals(mysqlReplication)) throw new AssertionError("worker.mysqlReplication!=mysqlReplication: "+worker._mysqlReplication+"!="+mysqlReplication);
            }
            return worker;
        }
    }

    final private FailoverMySQLReplication _mysqlReplication;
    private FailoverMySQLReplication currentFailoverMySQLReplication;

    private MySQLReplicationNodeWorker(File persistenceDirectory, FailoverMySQLReplication mysqlReplication) {
        super(new File(persistenceDirectory, Integer.toString(mysqlReplication.getPkey())), new File(persistenceDirectory, Integer.toString(mysqlReplication.getPkey())+".new"), false);
        this._mysqlReplication = mysqlReplication;
    }

    @Override
    protected int getHistorySize() {
        return 1000;
    }

    @Override
    protected List<?> getRowData(Locale locale) throws Exception {
        // Get the latest values
        currentFailoverMySQLReplication = _mysqlReplication.getTable().get(_mysqlReplication.getKey());
        FailoverMySQLReplication.SlaveStatus slaveStatus = currentFailoverMySQLReplication.getSlaveStatus();
        if(slaveStatus==null) throw new SQLException(ApplicationResourcesAccessor.getMessage(locale, "MySQLReplicationNodeWorker.slaveNotRunning"));
        MySQLServer.MasterStatus masterStatus = _mysqlReplication.getMySQLServer().getMasterStatus();
        if(masterStatus==null) throw new SQLException(ApplicationResourcesAccessor.getMessage(locale, "MySQLReplicationNodeWorker.masterNotRunning"));

        List<Object> rowData = new ArrayList<Object>(10);
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
        return Collections.unmodifiableList(rowData);
    }

    @Override
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, List<?> rowData, LinkedList<TableMultiResult> previousResults) throws Exception {
        String secondsBehindMaster = (String)rowData.get(0);
        if(secondsBehindMaster==null) {
            return new AlertLevelAndMessage(
                AlertLevel.CRITICAL,
                ApplicationResourcesAccessor.getMessage(
                    locale,
                    "MySQLReplicationNodeWorker.alertMessage.secondsBehindMaster.null"
                )
            );
        }
        try {
            int secondsBehind = Integer.parseInt(secondsBehindMaster);
            if(secondsBehind>=SECONDS_BEHIND_CRITICAL) {
                return new AlertLevelAndMessage(
                    AlertLevel.CRITICAL,
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "MySQLReplicationNodeWorker.alertMessage.critical",
                        SECONDS_BEHIND_CRITICAL,
                        secondsBehind
                    )
                );
            }
            if(secondsBehind>=SECONDS_BEHIND_HIGH) {
                return new AlertLevelAndMessage(
                    AlertLevel.HIGH,
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "MySQLReplicationNodeWorker.alertMessage.high",
                        SECONDS_BEHIND_HIGH,
                        secondsBehind
                    )
                );
            }
            if(secondsBehind>=SECONDS_BEHIND_MEDIUM) {
                return new AlertLevelAndMessage(
                    AlertLevel.MEDIUM,
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "MySQLReplicationNodeWorker.alertMessage.medium",
                        SECONDS_BEHIND_MEDIUM,
                        secondsBehind
                    )
                );
            }
            if(secondsBehind>=SECONDS_BEHIND_LOW) {
                return new AlertLevelAndMessage(
                    AlertLevel.LOW,
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "MySQLReplicationNodeWorker.alertMessage.low",
                        SECONDS_BEHIND_LOW,
                        secondsBehind
                    )
                );
            }
            return new AlertLevelAndMessage(
                AlertLevel.NONE,
                ApplicationResourcesAccessor.getMessage(
                    locale,
                    "MySQLReplicationNodeWorker.alertMessage.none",
                    SECONDS_BEHIND_LOW,
                    secondsBehind
                )
            );
        } catch(NumberFormatException err) {
            return new AlertLevelAndMessage(
                AlertLevel.CRITICAL,
                ApplicationResourcesAccessor.getMessage(
                    locale,
                    "MySQLReplicationNodeWorker.alertMessage.secondsBehindMaster.invalid",
                    secondsBehindMaster
                )
            );
        }
    }
}
