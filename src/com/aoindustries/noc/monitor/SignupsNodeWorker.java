/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.BusinessAdministrator;
import com.aoindustries.aoserv.client.SignupRequest;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.TableResult;
import com.aoindustries.noc.common.TimeWithTimeZone;
import com.aoindustries.sql.ResultSetHandler;
import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The workers for 3ware RAID.
 *
 * @author  AO Industries, Inc.
 */
class SignupsNodeWorker extends TableResultNodeWorker {

    /**
     * One unique worker is made per persistence file.
     */
    private static final Map<String, SignupsNodeWorker> workerCache = new HashMap<String,SignupsNodeWorker>();
    static SignupsNodeWorker getWorker(File persistenceFile, AOServConnector conn) throws IOException {
        String path = persistenceFile.getCanonicalPath();
        synchronized(workerCache) {
            SignupsNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new SignupsNodeWorker(persistenceFile, conn);
                workerCache.put(path, worker);
            }
            return worker;
        }
    }

    private final AOServConnector conn;

    SignupsNodeWorker(File persistenceFile, AOServConnector conn) {
        super(persistenceFile);
        this.conn = conn;
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
            // Count the number of incompleted signups
            int incompleteCount = 0;
            for(int index=0,len=tableData.size();index<len;index+=6) {
                String completedBy = (String)tableData.get(index+4);
                if(completedBy==null) incompleteCount++;
            }
            if(incompleteCount==0) {
                return new AlertLevelAndMessage(AlertLevel.NONE, "");
            } else {
                return new AlertLevelAndMessage(
                    AlertLevel.CRITICAL,
                    incompleteCount==1
                    ? ApplicationResourcesAccessor.getMessage(locale, "SignpusNodeWorker.incompleteCount.singular", incompleteCount)
                    : ApplicationResourcesAccessor.getMessage(locale, "SignpusNodeWorker.incompleteCount.plural", incompleteCount)
                );
            }
        }
    }

    @Override
    protected int getColumns() {
        return 6;
    }

    @Override
    protected List<?> getColumnHeaders(Locale locale) {
        List<String> columnHeaders = new ArrayList<String>(6);
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "SignpusNodeWorker.columnHeader.source"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "SignpusNodeWorker.columnHeader.pkey"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "SignpusNodeWorker.columnHeader.time"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "SignpusNodeWorker.columnHeader.ip_address"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "SignpusNodeWorker.columnHeader.completed_by"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "SignpusNodeWorker.columnHeader.completed_time"));
        return columnHeaders;
    }

    @Override
    protected List<?> getTableData(Locale locale) throws Exception {
        final List<Object> tableData = new ArrayList<Object>();
        // Add the old signup forms
        WebSiteDatabase database = WebSiteDatabase.getDatabase();
        database.executeQuery(
            new ResultSetHandler() {
                @Override
                public void handleResultSet(ResultSet result) throws SQLException {
                    tableData.add("aoweb");
                    tableData.add(result.getInt("pkey"));
                    tableData.add(new TimeWithTimeZone(result.getTimestamp("time").getTime()));
                    tableData.add(result.getString("ip_address"));
                    tableData.add(result.getString("completed_by"));
                    Timestamp completedTime = result.getTimestamp("completed_time");
                    tableData.add(completedTime==null ? null : new TimeWithTimeZone(completedTime.getTime()));
                }
            },
            "select * from signup_requests order by time"
        );

        // Add the aoserv signups
        for(SignupRequest request : conn.getSignupRequests()) {
            tableData.add(request.getPackageDefinition().getBusiness().getAccounting());
            tableData.add(request.getPkey());
            tableData.add(new TimeWithTimeZone(request.getTime()));
            tableData.add(request.getIpAddress());
            BusinessAdministrator completedBy = request.getCompletedBy();
            tableData.add(completedBy==null ? null : completedBy.getUsername().getUsername());
            long completedTime = request.getCompletedTime();
            tableData.add(completedTime==-1 ? null : new TimeWithTimeZone(completedTime));
        }
        return tableData;
    }

    @Override
    protected List<AlertLevel> getAlertLevels(List<?> tableData) {
        List<AlertLevel> alertLevels = new ArrayList<AlertLevel>(tableData.size()/6);
        for(int index=0,len=tableData.size();index<len;index+=6) {
            String completedBy = (String)tableData.get(index+4);
            alertLevels.add(completedBy==null ? AlertLevel.CRITICAL : AlertLevel.NONE);
        }
        return alertLevels;
    }
}
