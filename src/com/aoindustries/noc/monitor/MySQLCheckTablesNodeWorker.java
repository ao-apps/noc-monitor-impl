/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.MySQLDatabase;
import com.aoindustries.aoserv.client.MySQLServer;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.TableResult;
import com.aoindustries.noc.common.TimeSpan;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The workers for MySQLCheckTablesNode.
 *
 * @author  AO Industries, Inc.
 */
class MySQLCheckTablesNodeWorker extends TableResultNodeWorker {

    /**
     * One unique worker is made per persistence file (and should match the mysqlDatabase exactly)
     */
    private static final Map<String, MySQLCheckTablesNodeWorker> workerCache = new HashMap<String,MySQLCheckTablesNodeWorker>();
    static MySQLCheckTablesNodeWorker getWorker(MySQLDatabaseNode databaseNode, File persistenceFile) throws IOException {
        String path = persistenceFile.getCanonicalPath();
        synchronized(workerCache) {
            MySQLCheckTablesNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new MySQLCheckTablesNodeWorker(databaseNode, persistenceFile);
                workerCache.put(path, worker);
            } else {
                if(!worker.databaseNode.getMySQLDatabase().equals(databaseNode.getMySQLDatabase())) throw new AssertionError("worker.mysqlDatabase!=mysqlDatabase: "+worker.databaseNode.getMySQLDatabase()+"!="+databaseNode.getMySQLDatabase());
            }
            return worker;
        }
    }

    // Will use whichever connector first created this worker, even if other accounts connect later.
    final private MySQLDatabaseNode databaseNode;

    MySQLCheckTablesNodeWorker(MySQLDatabaseNode databaseNode, File persistenceFile) {
        super(persistenceFile);
        this.databaseNode = databaseNode;
    }

    @Override
    protected int getColumns() {
        return 5;
    }

    @Override
    protected List<?> getColumnHeaders(Locale locale) {
        List<String> columnHeaders = new ArrayList<String>(5);
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLCheckTablesNodeWorker.columnHeader.name"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLCheckTablesNodeWorker.columnHeader.engine"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLCheckTablesNodeWorker.columnHeader.duration"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLCheckTablesNodeWorker.columnHeader.msgType"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "MySQLCheckTablesNodeWorker.columnHeader.msgText"));
        return columnHeaders;
    }

    @Override
    protected List<?> getTableData(Locale locale) throws Exception {
        MySQLDatabase mysqlDatabase = databaseNode.getMySQLDatabase();
        // Don't check any table on MySQL 5.1 information_schema database
        if(mysqlDatabase.getName().equals(MySQLDatabase.INFORMATION_SCHEMA)) {
            String version = mysqlDatabase.getMySQLServer().getVersion().getVersion();
            if(version.startsWith(MySQLServer.VERSION_5_1_PREFIX)) return Collections.emptyList();
        }

        List<MySQLDatabase.TableStatus> lastTableStatuses = databaseNode.databaseWorker.getLastTableStatuses();
        if(lastTableStatuses.isEmpty()) return Collections.emptyList();
        // Build the set of table names and types
        List<String> tableNames = new ArrayList<String>(lastTableStatuses.size());
        Map<String,MySQLDatabase.Engine> tables = new HashMap<String,MySQLDatabase.Engine>(lastTableStatuses.size()*4/3+1);
        for(MySQLDatabase.TableStatus lastTableStatus : lastTableStatuses) {
            MySQLDatabase.Engine engine = lastTableStatus.getEngine();
            if(
                engine!=MySQLDatabase.Engine.CSV
                && engine!=MySQLDatabase.Engine.HEAP
                && engine!=MySQLDatabase.Engine.InnoDB
                && engine!=MySQLDatabase.Engine.MEMORY
                && !(engine==null && "VIEW".equals(lastTableStatus.getComment()))
            ) {
                String name = lastTableStatus.getName();
                tableNames.add(name);
                tables.put(name, engine);
            }
        }
        List<MySQLDatabase.CheckTableResult> checkTableResults = mysqlDatabase.checkTables(tableNames);
        List<Object> tableData = new ArrayList<Object>(checkTableResults.size()*5);

        for(MySQLDatabase.CheckTableResult checkTableResult : checkTableResults) {
            String table = checkTableResult.getTable();
            tableData.add(table);
            tableData.add(tables.get(table));
            tableData.add(new TimeSpan(checkTableResult.getDuration()));
            tableData.add(checkTableResult.getMsgType());
            tableData.add(checkTableResult.getMsgText());
        }
        return tableData;
    }

    /**
     * If is a slowServer (many tables), only checks once every 12 hours.
     * Only check once every five minutes when successful, retry
     * in one minute when unsuccessful.
     */
    @Override
    protected long getSleepDelay(boolean lastSuccessful) {
        if(databaseNode.databaseWorker.isSlowServer) return 12L*60*60*1000; // Only check tables once every 12 hours
        return lastSuccessful ? 300000 : 60000;
    }

    @Override
    protected long getTimeout() {
        return databaseNode.databaseWorker.isSlowServer ? 30 : 5;
    }

    @Override
    protected List<AlertLevel> getAlertLevels(List<?> tableData) {
        List<AlertLevel> alertLevels = new ArrayList<AlertLevel>(tableData.size()/5);
        for(int index=0,len=tableData.size();index<len;index+=5) {
            String msgText = (String)tableData.get(index+4);
            alertLevels.add(
                msgText!=null && (msgText.equals("OK") || msgText.equals("Table is already up to date"))
                ? AlertLevel.NONE
                : AlertLevel.CRITICAL
            );
        }
        return alertLevels;
    }

    /**
     * Determines the alert message for the provided result.
     */
    @Override
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, TableResult result) {
        List<?> tableData = result.getTableData();
        if(result.isError()) {
            return new AlertLevelAndMessage(result.getAlertLevels().get(0), tableData.get(0).toString());
        } else {
            for(int index=0,len=tableData.size();index<len;index+=5) {
                String msgText = (String)tableData.get(index+4);
                if(msgText==null || (!msgText.equals("OK") && !msgText.equals("Table is already up to date"))) {
                    return new AlertLevelAndMessage(
                        AlertLevel.CRITICAL,
                        ((String)tableData.get(index))+" - "+msgText
                    );
                }
            }
        }
        return new AlertLevelAndMessage(AlertLevel.NONE, "");
    }
}
