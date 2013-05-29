/*
 * Copyright 2009-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.FailoverMySQLReplication;
import com.aoindustries.aoserv.client.MySQLDatabase;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.MonitoringPoint;
import com.aoindustries.noc.monitor.common.TableResult;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The workers for MySQLDatabaseNode.
 *
 * @author  AO Industries, Inc.
 */
class MySQLDatabaseNodeWorker extends TableResultNodeWorker<List<MySQLDatabase.TableStatus>,Object> {

    private static final Logger logger = Logger.getLogger(MySQLDatabaseNodeWorker.class.getName());

    /**
     * One unique worker is made per persistence file (and should match the mysqlDatabase exactly)
     */
    private static final Map<String, MySQLDatabaseNodeWorker> workerCache = new HashMap<String,MySQLDatabaseNodeWorker>();
    static MySQLDatabaseNodeWorker getWorker(MonitoringPoint monitoringPoint, File persistenceFile, MySQLDatabase mysqlDatabase, FailoverMySQLReplication mysqlSlave) throws IOException, SQLException {
        String path = persistenceFile.getCanonicalPath();
        synchronized(workerCache) {
            MySQLDatabaseNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new MySQLDatabaseNodeWorker(monitoringPoint, persistenceFile, mysqlDatabase, mysqlSlave);
                workerCache.put(path, worker);
            } else {
                if(!worker.mysqlDatabase.equals(mysqlDatabase)) throw new AssertionError("worker.mysqlDatabase!=mysqlDatabase: "+worker.mysqlDatabase+"!="+mysqlDatabase);
            }
            return worker;
        }
    }

    // Will use whichever connector first created this worker, even if other accounts connect later.
    final private MySQLDatabase mysqlDatabase;
    final private FailoverMySQLReplication mysqlSlave;
    final boolean isSlowServer;
    final private Object lastTableStatusesLock = new Object();
    private List<MySQLDatabase.TableStatus> lastTableStatuses;

    MySQLDatabaseNodeWorker(MonitoringPoint monitoringPoint, File persistenceFile, MySQLDatabase mysqlDatabase, FailoverMySQLReplication mysqlSlave) throws IOException, SQLException {
        super(monitoringPoint, persistenceFile);
        this.mysqlDatabase = mysqlDatabase;
        this.mysqlSlave = mysqlSlave;
        String hostname = mysqlDatabase.getMySQLServer().getAOServer().getHostname().toString();
        this.isSlowServer =
            hostname.equals("www.swimconnection.com")
            // || hostname.equals("www1.leagle.com")
        ;
    }

    @Override
    protected int getColumns() {
        return 18;
    }

    @Override
    protected List<String> getColumnHeaders(Locale locale) {
        List<String> columnHeaders = new ArrayList<String>(18);
        columnHeaders.add(accessor.getMessage(/*locale,*/ "MySQLDatabaseNodeWorker.columnHeader.name"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "MySQLDatabaseNodeWorker.columnHeader.engine"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "MySQLDatabaseNodeWorker.columnHeader.version"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "MySQLDatabaseNodeWorker.columnHeader.rowFormat"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "MySQLDatabaseNodeWorker.columnHeader.rows"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "MySQLDatabaseNodeWorker.columnHeader.avgRowLength"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "MySQLDatabaseNodeWorker.columnHeader.dataLength"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "MySQLDatabaseNodeWorker.columnHeader.maxDataLength"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "MySQLDatabaseNodeWorker.columnHeader.indexLength"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "MySQLDatabaseNodeWorker.columnHeader.dataFree"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "MySQLDatabaseNodeWorker.columnHeader.autoIncrement"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "MySQLDatabaseNodeWorker.columnHeader.createTime"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "MySQLDatabaseNodeWorker.columnHeader.updateTime"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "MySQLDatabaseNodeWorker.columnHeader.checkTime"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "MySQLDatabaseNodeWorker.columnHeader.collation"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "MySQLDatabaseNodeWorker.columnHeader.checksum"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "MySQLDatabaseNodeWorker.columnHeader.createOptions"));
        columnHeaders.add(accessor.getMessage(/*locale,*/ "MySQLDatabaseNodeWorker.columnHeader.comment"));
        return columnHeaders;
    }

    @Override
    protected List<MySQLDatabase.TableStatus> getQueryResult(Locale locale) throws Exception {
        List<MySQLDatabase.TableStatus> tableStatuses = mysqlDatabase.getTableStatus(mysqlSlave);
        setLastTableStatuses(tableStatuses);
        return tableStatuses;
    }

    @Override
    protected List<?> getTableData(List<MySQLDatabase.TableStatus> tableStatuses, Locale locale) throws Exception {
        List<Object> tableData = new ArrayList<Object>(tableStatuses.size()*18);

        for(MySQLDatabase.TableStatus tableStatus : tableStatuses) {
            tableData.add(tableStatus.getName());
            tableData.add(tableStatus.getEngine());
            tableData.add(tableStatus.getVersion());
            tableData.add(tableStatus.getRowFormat());
            tableData.add(tableStatus.getRows());
            tableData.add(tableStatus.getAvgRowLength());
            tableData.add(tableStatus.getDataLength());
            tableData.add(tableStatus.getMaxDataLength());
            tableData.add(tableStatus.getIndexLength());
            tableData.add(tableStatus.getDataFree());
            tableData.add(tableStatus.getAutoIncrement());
            tableData.add(tableStatus.getCreateTime());
            tableData.add(tableStatus.getUpdateTime());
            tableData.add(tableStatus.getCheckTime());
            tableData.add(tableStatus.getCollation());
            tableData.add(tableStatus.getChecksum());
            tableData.add(tableStatus.getCreateOptions());
            tableData.add(tableStatus.getComment());
        }
        return tableData;
    }

    private void setLastTableStatuses(List<MySQLDatabase.TableStatus> tableStatuses) {
        synchronized(lastTableStatusesLock) {
            this.lastTableStatuses = tableStatuses;
            lastTableStatusesLock.notifyAll();
        }
    }

    /**
     * Gets the last table statuses.  May wait for the data to become available,
     * will not return null.  May wait for a very long time in some cases.
     */
    List<MySQLDatabase.TableStatus> getLastTableStatuses() {
        synchronized(lastTableStatusesLock) {
            while(lastTableStatuses==null) {
                try {
                    lastTableStatusesLock.wait();
                } catch(InterruptedException err) {
                    // logger.warning("wait interrupted");
                }
            }
            return lastTableStatuses;
        }
    }

    /**
     * If is a slowServer (many tables), only updates once every 12 hours.
     * Otherwise updates once every five minutes.
     */
    @Override
    protected long getSleepDelay(boolean lastSuccessful, AlertLevel alertLevel) {
        if(isSlowServer) return 12L*60*60000; // Only update once every 12 hours
        return 5*60000;
    }

    @Override
    protected long getTimeout() {
        return isSlowServer ? 30 : 5;
    }

    @Override
    protected List<AlertLevel> getAlertLevels(List<MySQLDatabase.TableStatus> tableStatuses) {
        List<AlertLevel> alertLevels = new ArrayList<AlertLevel>(tableStatuses.size());
        for(MySQLDatabase.TableStatus tableStatus : tableStatuses) {
            AlertLevel alertLevel = AlertLevel.NONE;
            // Could compare data length to max data length and warn, but max data length is incredibly high in MySQL 5.0+
            alertLevels.add(alertLevel);
        }
        return alertLevels;
    }

    /**
     * Determines the alert message for the provided result.
     */
    @Override
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, TableResult result) {
        AlertLevel highestAlertLevel;
        String highestAlertMessage;
        List<?> tableData = result.getTableData();
        if(result.isError()) {
            highestAlertLevel = result.getAlertLevels().get(0);
            highestAlertMessage = tableData.get(0).toString();
        } else {
            highestAlertLevel = AlertLevel.NONE;
            highestAlertMessage = "";
        }
        return new AlertLevelAndMessage(highestAlertLevel, highestAlertMessage);
    }
}
