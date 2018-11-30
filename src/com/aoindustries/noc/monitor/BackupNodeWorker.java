/*
 * Copyright 2008, 2009, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.backup.FileReplication;
import com.aoindustries.aoserv.client.backup.FileReplicationLog;
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.aoserv.client.net.Host;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.SerializableFunction;
import com.aoindustries.noc.monitor.common.TableResult;
import com.aoindustries.noc.monitor.common.TimeWithTimeZone;
import com.aoindustries.util.StringUtility;
import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Function;

/**
 * The workers for 3ware RAID.
 *
 * @author  AO Industries, Inc.
 */
class BackupNodeWorker extends TableResultNodeWorker<List<FileReplicationLog>,Object> {

	private static final int HISTORY_SIZE = 100;

	/**
	 * One unique worker is made per persistence file (and should match the failoverFileReplication exactly)
	 */
	private static final Map<String, BackupNodeWorker> workerCache = new HashMap<>();
	static BackupNodeWorker getWorker(File persistenceFile, FileReplication failoverFileReplication) throws IOException {
		String path = persistenceFile.getCanonicalPath();
		synchronized(workerCache) {
			BackupNodeWorker worker = workerCache.get(path);
			if(worker==null) {
				worker = new BackupNodeWorker(persistenceFile, failoverFileReplication);
				workerCache.put(path, worker);
			} else {
				if(!worker.failoverFileReplication.equals(failoverFileReplication)) throw new AssertionError("worker.failoverFileReplication!=failoverFileReplication: "+worker.failoverFileReplication+"!="+failoverFileReplication);
			}
			return worker;
		}
	}

	// Will use whichever connector first created this worker, even if other accounts connect later.
	final private FileReplication failoverFileReplication;

	BackupNodeWorker(File persistenceFile, FileReplication failoverFileReplication) {
		super(persistenceFile);
		this.failoverFileReplication = failoverFileReplication;
	}

	/**
	 * Determines the alert message for the provided result.
	 * 
	 * If there is not any data (no backups logged, make high level)
	 */
	@Override
	protected AlertLevelAndMessage getAlertLevelAndMessage(AlertLevel curAlertLevel, TableResult result) {
		AlertLevel highestAlertLevel;
		Function<Locale,String> highestAlertMessage;
		if(result.isError()) {
			highestAlertLevel = result.getAlertLevels().get(0);
			highestAlertMessage = locale -> result.getTableData(locale).get(0).toString();
		} else {
			List<?> tableData = result.getTableData(Locale.getDefault());
			if(tableData.isEmpty()) {
				highestAlertLevel = AlertLevel.MEDIUM;
				highestAlertMessage = locale -> accessor.getMessage(locale, "BackupNodeWorker.noBackupPassesLogged");
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
					highestAlertMessage = locale -> accessor.getMessage(locale, "BackupNodeWorker.noSuccessfulPassesFound", result.getRows());
				} else {
					long hoursSince = (System.currentTimeMillis() - lastSuccessfulTime)/((long)60*60*1000);
					if(hoursSince<0) {
						highestAlertLevel = AlertLevel.CRITICAL;
						highestAlertMessage = locale -> accessor.getMessage(locale, "BackupNodeWorker.lastSuccessfulPassInFuture");
					} else {
						if(hoursSince<30) {
							highestAlertLevel = AlertLevel.NONE;
						} else if(hoursSince<48) {
							highestAlertLevel = AlertLevel.LOW;
						} else {
							highestAlertLevel = AlertLevel.MEDIUM;
						}
						if(hoursSince<=48) {
							highestAlertMessage = locale -> accessor.getMessage(locale, "BackupNodeWorker.lastSuccessfulPass", hoursSince);
						} else {
							long days = hoursSince / 24;
							long hours = hoursSince % 24;
							highestAlertMessage = locale -> accessor.getMessage(locale, "BackupNodeWorker.lastSuccessfulPassDays", days, hours);
						}
					}
				}
				// We next see if the last pass failed - if so this will be considered low priority (higher priority is time-based above)
				boolean lastSuccessful = (Boolean)tableData.get(5);
				if(!lastSuccessful) {
					if(AlertLevel.LOW.compareTo(highestAlertLevel)>0) {
						highestAlertLevel = AlertLevel.LOW;
						highestAlertMessage = locale -> accessor.getMessage(locale, "BackupNodeWorker.lastPassNotSuccessful");
					}
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
	protected SerializableFunction<Locale,List<String>> getColumnHeaders() {
		return locale -> Arrays.asList(
			accessor.getMessage(locale, "BackupNodeWorker.columnHeader.startTime"),
			accessor.getMessage(locale, "BackupNodeWorker.columnHeader.duration"),
			accessor.getMessage(locale, "BackupNodeWorker.columnHeader.scanned"),
			accessor.getMessage(locale, "BackupNodeWorker.columnHeader.updated"),
			accessor.getMessage(locale, "BackupNodeWorker.columnHeader.bytes"),
			accessor.getMessage(locale, "BackupNodeWorker.columnHeader.successful")
		);
	}

	@Override
	protected List<FileReplicationLog> getQueryResult() throws Exception {
		return failoverFileReplication.getFailoverFileLogs(HISTORY_SIZE);
	}

	@Override
	protected SerializableFunction<Locale,List<Object>> getTableData(List<FileReplicationLog> failoverFileLogs) throws Exception {
		if(failoverFileLogs.isEmpty()) {
			return locale -> Collections.emptyList();
		} else {
			Host server = failoverFileReplication.getServer();
			Server aoServer = server.getAOServer();
			TimeZone timeZone = aoServer==null ? null : aoServer.getTimeZone().getTimeZone();
			List<Object> tableData = new ArrayList<>(failoverFileLogs.size()*6);
			//int lineNum = 0;
			for(FileReplicationLog failoverFileLog : failoverFileLogs) {
				//lineNum++;
				Timestamp startTime = failoverFileLog.getStartTime();
				tableData.add(new TimeWithTimeZone(startTime.getTime(), timeZone));
				tableData.add(StringUtility.getTimeLengthString(failoverFileLog.getEndTime().getTime() - startTime.getTime()));
				tableData.add(failoverFileLog.getScanned());
				tableData.add(failoverFileLog.getUpdated());
				tableData.add(StringUtility.getApproximateSize(failoverFileLog.getBytes()));
				tableData.add(failoverFileLog.isSuccessful());
			}
			return locale -> tableData;
		}
	}

	@Override
	protected List<AlertLevel> getAlertLevels(List<FileReplicationLog> queryResult) {
		List<AlertLevel> alertLevels = new ArrayList<>(queryResult.size());
		for(FileReplicationLog failoverFileLog : queryResult) {
			// If pass failed then it is HIGH, otherwise it is NONE
			alertLevels.add(failoverFileLog.isSuccessful() ? AlertLevel.NONE : AlertLevel.MEDIUM);
		}
		return alertLevels;
	}
}
