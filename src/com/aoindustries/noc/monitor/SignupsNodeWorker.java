/*
 * Copyright 2008-2009, 2015, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.BusinessAdministrator;
import com.aoindustries.aoserv.client.SignupRequest;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.SerializableFunction;
import com.aoindustries.noc.monitor.common.TableResult;
import com.aoindustries.noc.monitor.common.TimeWithTimeZone;
import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The workers for 3ware RAID.
 *
 * @author  AO Industries, Inc.
 */
class SignupsNodeWorker extends TableResultNodeWorker<List<Object>,Object> {

	/**
	 * One unique worker is made per persistence file.
	 */
	private static final Map<String, SignupsNodeWorker> workerCache = new HashMap<>();
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
	protected AlertLevelAndMessage getAlertLevelAndMessage(AlertLevel curAlertLevel, TableResult result) {
		if(result.isError()) {
			return new AlertLevelAndMessage(
				result.getAlertLevels().get(0),
				locale -> result.getTableData(locale).get(0).toString()
			);
		} else {
			List<?> tableData = result.getTableData(Locale.getDefault());
			// Count the number of incompleted signups
			int incompleteCount;
			{
				int i = 0;
				for(int index=0,len=tableData.size();index<len;index+=6) {
					String completedBy = (String)tableData.get(index+4);
					if(completedBy==null) i++;
				}
				incompleteCount = i;
			}
			if(incompleteCount==0) {
				return AlertLevelAndMessage.NONE;
			} else {
				return new AlertLevelAndMessage(
					AlertLevel.CRITICAL,
					locale -> incompleteCount==1
						? accessor.getMessage(locale, "SignpusNodeWorker.incompleteCount.singular", incompleteCount)
						: accessor.getMessage(locale, "SignpusNodeWorker.incompleteCount.plural", incompleteCount)
				);
			}
		}
	}

	@Override
	protected int getColumns() {
		return 6;
	}

	@Override
	protected SerializableFunction<Locale,List<String>> getColumnHeaders() {
		return locale -> Arrays.asList(
			accessor.getMessage(locale, "SignpusNodeWorker.columnHeader.source"),
			accessor.getMessage(locale, "SignpusNodeWorker.columnHeader.pkey"),
			accessor.getMessage(locale, "SignpusNodeWorker.columnHeader.time"),
			accessor.getMessage(locale, "SignpusNodeWorker.columnHeader.ip_address"),
			accessor.getMessage(locale, "SignpusNodeWorker.columnHeader.completed_by"),
			accessor.getMessage(locale, "SignpusNodeWorker.columnHeader.completed_time")
		);
	}

	@Override
	protected List<Object> getQueryResult() throws Exception {
		// Add the old signup forms
		final List<Object> tableData = WebSiteDatabase.getDatabase().executeQuery(
			(ResultSet results) -> {
				List<Object> tableData1 = new ArrayList<>();
				while (results.next()) {
					tableData1.add("aoweb");
					tableData1.add(results.getInt("pkey"));
					tableData1.add(new TimeWithTimeZone(results.getTimestamp("time").getTime()));
					tableData1.add(results.getString("ip_address"));
					tableData1.add(results.getString("completed_by"));
					Timestamp completedTime = results.getTimestamp("completed_time");
					tableData1.add(completedTime==null ? null : new TimeWithTimeZone(completedTime.getTime()));
				}
				return tableData1;
			},
			"select * from signup_requests order by time"
		);

		// Add the aoserv signups
		for(SignupRequest request : conn.getSignupRequests()) {
			tableData.add(request.getPackageDefinition().getBusiness().getAccounting());
			tableData.add(request.getPkey());
			tableData.add(new TimeWithTimeZone(request.getTime().getTime()));
			tableData.add(request.getIpAddress());
			BusinessAdministrator completedBy = request.getCompletedBy();
			tableData.add(completedBy==null ? null : completedBy.getUsername().getUsername());
			Timestamp completedTime = request.getCompletedTime();
			tableData.add(completedTime==null ? null : new TimeWithTimeZone(completedTime.getTime()));
		}
		return tableData;
	}

	@Override
	protected SerializableFunction<Locale,List<Object>> getTableData(List<Object> tableData) throws Exception {
		return locale -> tableData;
	}

	@Override
	protected List<AlertLevel> getAlertLevels(List<Object> tableData) {
		List<AlertLevel> alertLevels = new ArrayList<>(tableData.size()/6);
		for(int index=0,len=tableData.size();index<len;index+=6) {
			String completedBy = (String)tableData.get(index+4);
			alertLevels.add(completedBy==null ? AlertLevel.CRITICAL : AlertLevel.NONE);
		}
		return alertLevels;
	}
}
