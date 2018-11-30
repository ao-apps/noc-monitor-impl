/*
 * Copyright 2014, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.linux.AOServer;
import com.aoindustries.aoserv.client.linux.AOServer.MdMismatchReport;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.SerializableFunction;
import com.aoindustries.noc.monitor.common.TableResult;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The workers for MD mismatch monitoring.
 *
 * @author  AO Industries, Inc.
 */
class MdMismatchWorker extends TableResultNodeWorker<List<MdMismatchReport>,String> {

	private static final int RAID1_HIGH_THRESHOLD = 2048;
	private static final int RAID1_MEDIUM_THRESHOLD = 1024;
	private static final int RAID1_LOW_THRESHOLD = 1;

	/**
	 * One unique worker is made per persistence file (and should match the aoServer exactly)
	 */
	private static final Map<String, MdMismatchWorker> workerCache = new HashMap<>();
	static MdMismatchWorker getWorker(File persistenceFile, AOServer aoServer) throws IOException {
		String path = persistenceFile.getCanonicalPath();
		synchronized(workerCache) {
			MdMismatchWorker worker = workerCache.get(path);
			if(worker==null) {
				worker = new MdMismatchWorker(persistenceFile, aoServer);
				workerCache.put(path, worker);
			} else {
				if(!worker.aoServer.equals(aoServer)) throw new AssertionError("worker.aoServer!=aoServer: "+worker.aoServer+"!="+aoServer);
			}
			return worker;
		}
	}

	// Will use whichever connector first created this worker, even if other accounts connect later.
	final private AOServer aoServer;

	MdMismatchWorker(File persistenceFile, AOServer aoServer) {
		super(persistenceFile);
		this.aoServer = aoServer;
	}

	/**
	 * Determines the alert message for the provided result.
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
				index < len;
				index += 3
			) {
				AlertLevel alertLevel = alertLevels.get(index / 3);
				if(alertLevel.compareTo(highestAlertLevel)>0) {
					highestAlertLevel = alertLevel;
					Object device = tableData.get(index);
					Object level = tableData.get(index+1);
					Object count = tableData.get(index+2);
					highestAlertMessage = locale -> device + " " + level + " " + count;
				}
			}
		}
		return new AlertLevelAndMessage(highestAlertLevel, highestAlertMessage);
	}

	@Override
	protected int getColumns() {
		return 3;
	}

	@Override
	protected SerializableFunction<Locale,List<String>> getColumnHeaders() {
		return locale -> Arrays.asList(
			accessor.getMessage(locale, "MdMismatchWorker.columnHeader.device"),
			accessor.getMessage(locale, "MdMismatchWorker.columnHeader.level"),
			accessor.getMessage(locale, "MdMismatchWorker.columnHeader.count")
		);
	}

	@Override
	protected List<MdMismatchReport> getQueryResult() throws Exception {
		return aoServer.getMdMismatchReport();
	}

	@Override
	protected SerializableFunction<Locale,List<String>> getTableData(List<MdMismatchReport> reports) throws Exception {
		List<String> tableData = new ArrayList<>(reports.size() * 3);
		for(MdMismatchReport report : reports) {
			tableData.add(report.getDevice());
			tableData.add(report.getLevel().name());
			tableData.add(Long.toString(report.getCount()));
		}
		return locale -> tableData;
	}

	@Override
	protected List<AlertLevel> getAlertLevels(List<MdMismatchReport> reports) {
		List<AlertLevel> alertLevels = new ArrayList<>(reports.size());
		for(MdMismatchReport report : reports) {
			long count = report.getCount();
			final AlertLevel alertLevel;
			if(count == 0) {
				alertLevel = AlertLevel.NONE;
			} else {
				if(report.getLevel() == AOServer.RaidLevel.raid1) {
					// Allow small amount of mismatch for RAID1 only
					alertLevel =
						count >= RAID1_HIGH_THRESHOLD ? AlertLevel.HIGH
						: count >= RAID1_MEDIUM_THRESHOLD ? AlertLevel.MEDIUM
						: count >= RAID1_LOW_THRESHOLD ? AlertLevel.LOW
						: AlertLevel.NONE
					;
				} else {
					// All other types allow no mismatch
					alertLevel = AlertLevel.HIGH;
				}
			}
			alertLevels.add(alertLevel);
		}
		return alertLevels;
	}
}
