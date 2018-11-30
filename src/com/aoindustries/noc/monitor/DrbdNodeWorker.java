/*
 * Copyright 2008-2013, 2014, 2015, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.aoserv.client.linux.Server.DrbdReport;
import com.aoindustries.lang.ObjectUtils;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.SerializableFunction;
import com.aoindustries.noc.monitor.common.TableResult;
import com.aoindustries.noc.monitor.common.TimeWithTimeZone;
import com.aoindustries.util.i18n.ThreadLocale;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * The workers for DRBD.
 *
 * @author  AO Industries, Inc.
 */
class DrbdNodeWorker extends TableResultNodeWorker<List<DrbdReport>,Object> {

	private static final int NUM_COLS = 7;

	private static final int
		LOW_DAYS = 15,
		MEDIUM_DAYS = 21,
		HIGH_DAYS = 28
	;

	private static final int OUT_OF_SYNC_HIGH_THRESHOLD = 512;

	/**
	 * One unique worker is made per persistence file (and should match the aoServer exactly)
	 */
	private static final Map<String, DrbdNodeWorker> workerCache = new HashMap<>();
	static DrbdNodeWorker getWorker(File persistenceFile, Server aoServer) throws IOException, SQLException {
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
	final private Server aoServer;
	final private TimeZone timeZone;

	DrbdNodeWorker(File persistenceFile, Server aoServer) throws IOException, SQLException {
		super(persistenceFile);
		this.aoServer = aoServer;
		this.timeZone = aoServer.getTimeZone().getTimeZone();
	}

	/**
	 * Determines the alert message for the provided result.
	 * 
	 * @link http://www.drbd.org/users-guide/ch-admin.html#s-disk-states
	 */
	@Override
	protected AlertLevelAndMessage getAlertLevelAndMessage(AlertLevel curAlertLevel, TableResult result) {
		AlertLevel highestAlertLevel = AlertLevel.NONE;
		Function<Locale,String> highestAlertMessage = null;
		if(result.isError()) {
			highestAlertLevel = result.getAlertLevels().get(0);
			highestAlertMessage = locale -> result.getTableData(locale).get(0).toString();
		} else {
			List<?> tableData = result.getTableData(Locale.getDefault());
			List<AlertLevel> alertLevels = result.getAlertLevels();
			for(
				int index=0, len=tableData.size();
				index<len;
				index += NUM_COLS
			) {
				AlertLevel alertLevel = alertLevels.get(index / NUM_COLS);
				if(alertLevel.compareTo(highestAlertLevel)>0) {
					highestAlertLevel = alertLevel;
					String device = (String)tableData.get(index);
					String resource = (String)tableData.get(index + 1);
					String cstate = (String)tableData.get(index + 2);
					String dstate = (String)tableData.get(index + 3);
					String roles = (String)tableData.get(index + 4);
					TimeWithTimeZone lastVerified = (TimeWithTimeZone)tableData.get(index + 5);
					Long outOfSync = (Long)tableData.get(index + 6);
					highestAlertMessage = locale -> ThreadLocale.set(
						locale,
						(ThreadLocale.Supplier<String>)() -> device+" "+resource+" "+cstate+" "+dstate+" "+roles+" "+lastVerified+" "+outOfSync
					);
				}
			}
		}
		return new AlertLevelAndMessage(highestAlertLevel, highestAlertMessage);
	}

	@Override
	protected int getColumns() {
		return NUM_COLS;
	}

	@Override
	protected SerializableFunction<Locale,List<String>> getColumnHeaders() {
		return locale -> Arrays.asList(
			accessor.getMessage(locale, "DrbdNodeWorker.columnHeader.device"),
			accessor.getMessage(locale, "DrbdNodeWorker.columnHeader.resource"),
			accessor.getMessage(locale, "DrbdNodeWorker.columnHeader.cs"),
			accessor.getMessage(locale, "DrbdNodeWorker.columnHeader.ds"),
			accessor.getMessage(locale, "DrbdNodeWorker.columnHeader.roles"),
			accessor.getMessage(locale, "DrbdNodeWorker.columnHeader.lastVerified"),
			accessor.getMessage(locale, "DrbdNodeWorker.columnHeader.outOfSync")
		);
	}

	@Override
	protected List<DrbdReport> getQueryResult() throws Exception {
		return aoServer.getDrbdReport();
	}

	@Override
	protected SerializableFunction<Locale,List<Object>> getTableData(List<DrbdReport> reports) throws Exception {
		List<Object> tableData = new ArrayList<>(reports.size() * NUM_COLS);
		for(DrbdReport report : reports) {
			tableData.add(report.getDevice());
			tableData.add(report.getResourceHostname()+'-'+report.getResourceDevice());
			tableData.add(ObjectUtils.toString(report.getConnectionState()));
			DrbdReport.DiskState localDiskState = report.getLocalDiskState();
			DrbdReport.DiskState remoteDiskState = report.getRemoteDiskState();
			tableData.add(localDiskState==null && remoteDiskState==null ? null : (localDiskState+"/"+remoteDiskState));
			DrbdReport.Role localRole = report.getLocalRole();
			DrbdReport.Role remoteRole = report.getRemoteRole();
			tableData.add(localRole==null && remoteRole==null ? null : (localRole+"/"+remoteRole));
			Long lastVerified = report.getLastVerified();
			tableData.add(lastVerified==null ? null : new TimeWithTimeZone(lastVerified, timeZone));
			tableData.add(report.getOutOfSync());
		}
		return locale -> tableData;
	}

	@Override
	protected List<AlertLevel> getAlertLevels(List<DrbdReport> reports) {
		final long currentTime = System.currentTimeMillis();
		List<AlertLevel> alertLevels = new ArrayList<>(reports.size());
		for(DrbdReport report : reports) {
			final AlertLevel alertLevel;
			// High alert if any out of sync
			Long outOfSync = report.getOutOfSync();
			if(outOfSync != null && outOfSync != 0) {
				if(outOfSync >= OUT_OF_SYNC_HIGH_THRESHOLD) {
					alertLevel = AlertLevel.HIGH;
				} else {
					alertLevel = AlertLevel.MEDIUM;
				}
			} else {
				DrbdReport.ConnectionState connectionState = report.getConnectionState();
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
						// Secondary/Secondary occurs when a virtual server is shutdown
						|| (report.getLocalRole()==DrbdReport.Role.Secondary && report.getRemoteRole()==DrbdReport.Role.Secondary)
					)
				) {
					alertLevel = AlertLevel.HIGH;
				} else {
					// Only check the verified time when primary on at last one side
					if(
						report.getLocalRole()==DrbdReport.Role.Primary
						|| report.getRemoteRole()==DrbdReport.Role.Primary
					) {
						// Check the time since last verified
						Long lastVerified = report.getLastVerified();
						if(lastVerified == null) {
							// Never verified
							alertLevel = AlertLevel.HIGH;
						} else {
							long daysSince = TimeUnit.DAYS.convert(
								Math.abs(currentTime - lastVerified),
								TimeUnit.MILLISECONDS
							);
							if     (daysSince >= HIGH_DAYS)   alertLevel = AlertLevel.HIGH;
							else if(daysSince >= MEDIUM_DAYS) alertLevel = AlertLevel.MEDIUM;
							else if(daysSince >= LOW_DAYS)    alertLevel = AlertLevel.LOW;
							else                              alertLevel = AlertLevel.NONE;
						}
					} else {
						// Secondary/Secondary not verified, no alerts
						alertLevel = AlertLevel.NONE;
					}
				}
			}
			alertLevels.add(alertLevel);
		}
		return alertLevels;
	}
}
