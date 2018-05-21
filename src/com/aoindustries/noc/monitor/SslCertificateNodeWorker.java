/*
 * Copyright 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.SslCertificate;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TableResult;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The workers for SSL certificates.
 *
 * @author  AO Industries, Inc.
 */
class SslCertificateNodeWorker extends TableResultNodeWorker<List<SslCertificate.Check>,Object> {

	private static final int NUM_COLS = 3;

	/**
	 * One unique worker is made per persistence file (and should match the sslCertificate exactly)
	 */
	private static final Map<String, SslCertificateNodeWorker> workerCache = new HashMap<>();
	static SslCertificateNodeWorker getWorker(File persistenceFile, SslCertificate sslCertificate) throws IOException, SQLException {
		String path = persistenceFile.getCanonicalPath();
		synchronized(workerCache) {
			SslCertificateNodeWorker worker = workerCache.get(path);
			if(worker == null) {
				worker = new SslCertificateNodeWorker(persistenceFile, sslCertificate);
				workerCache.put(path, worker);
			} else {
				if(!worker.sslCertificate.equals(sslCertificate)) throw new AssertionError("worker.sslCertificate!=sslCertificate: "+worker.sslCertificate+"!="+sslCertificate);
			}
			return worker;
		}
	}

	// Will use whichever connector first created this worker, even if other accounts connect later.
	final private SslCertificate sslCertificate;

	SslCertificateNodeWorker(File persistenceFile, SslCertificate sslCertificate) throws IOException, SQLException {
		super(persistenceFile);
		this.sslCertificate = sslCertificate;
	}

	/**
	 * Determines the alert message for the provided result.
	 * The alert level is the first result of the highest alert level.
	 */
	@Override
	protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, AlertLevel curAlertLevel, TableResult result) {
		AlertLevel highestAlertLevel = AlertLevel.NONE;
		String highestAlertMessage = "";
		List<?> tableData = result.getTableData();
		if(result.isError()) {
			highestAlertLevel = result.getAlertLevels().get(0);
			highestAlertMessage = tableData.get(0).toString();
		} else {
			List<AlertLevel> alertLevels = result.getAlertLevels();
			for(
				int index = 0, len = tableData.size();
				index < len;
				index += NUM_COLS
			) {
				AlertLevel alertLevel = alertLevels.get(index / NUM_COLS);
				if(alertLevel.compareTo(highestAlertLevel) > 0) {
					highestAlertLevel = alertLevel;
					highestAlertMessage = (String)tableData.get(index + 2);
					if(highestAlertMessage == null || highestAlertMessage.isEmpty()) {
						highestAlertMessage = (String)tableData.get(index + 1);
					}
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
	protected List<String> getColumnHeaders(Locale locale) {
		List<String> columnHeaders = new ArrayList<>(NUM_COLS);
		columnHeaders.add(accessor.getMessage(/*locale,*/ "SslCertificateNodeWorker.columnHeader.check"));
		columnHeaders.add(accessor.getMessage(/*locale,*/ "SslCertificateNodeWorker.columnHeader.value"));
		columnHeaders.add(accessor.getMessage(/*locale,*/ "SslCertificateNodeWorker.columnHeader.message"));
		return columnHeaders;
	}

	@Override
	protected List<SslCertificate.Check> getQueryResult(Locale locale) throws Exception {
		return sslCertificate.check();
	}

	@Override
	protected List<Object> getTableData(List<SslCertificate.Check> results, Locale locale) throws Exception {
		List<Object> tableData = new ArrayList<>(results.size() * NUM_COLS);
		for(SslCertificate.Check result : results) {
			tableData.add(result.getCheck());
			tableData.add(result.getValue());
			tableData.add(result.getMessage());
		}
		return tableData;
	}

	@Override
	protected List<AlertLevel> getAlertLevels(List<SslCertificate.Check> results) {
		List<AlertLevel> alertLevels = new ArrayList<>(results.size());
		for(SslCertificate.Check result : results) {
			alertLevels.add(AlertLevelUtils.getMonitoringAlertLevel(result.getAlertLevel()));
		}
		return alertLevels;
	}
}
