/*
 * Copyright 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.pki;

import com.aoindustries.aoserv.client.pki.Certificate;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.TableResultNodeWorker;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.SerializableFunction;
import com.aoindustries.noc.monitor.common.TableResult;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The workers for SSL certificates.
 *
 * @author  AO Industries, Inc.
 */
class CertificateNodeWorker extends TableResultNodeWorker<List<Certificate.Check>,Object> {

	private static final int NUM_COLS = 3;

	/**
	 * @implNote  This is 5 minutes more than "CERTBOT_CACHE_DURATION" in aoserv-daemon/SslCertificateManager.java
	 */
	private static final long NONE_SLEEP_DELAY = 60L * 60 * 1000;

	/**
	 * One unique worker is made per persistence file (and should match the sslCertificate exactly)
	 */
	private static final Map<String, CertificateNodeWorker> workerCache = new HashMap<>();
	static CertificateNodeWorker getWorker(File persistenceFile, Certificate sslCertificate) throws IOException, SQLException {
		String path = persistenceFile.getCanonicalPath();
		synchronized(workerCache) {
			CertificateNodeWorker worker = workerCache.get(path);
			if(worker == null) {
				worker = new CertificateNodeWorker(persistenceFile, sslCertificate);
				workerCache.put(path, worker);
			} else {
				if(!worker.sslCertificate.equals(sslCertificate)) throw new AssertionError("worker.sslCertificate!=sslCertificate: "+worker.sslCertificate+"!="+sslCertificate);
			}
			return worker;
		}
	}

	// Will use whichever connector first created this worker, even if other accounts connect later.
	final private Certificate sslCertificate;

	CertificateNodeWorker(File persistenceFile, Certificate sslCertificate) throws IOException, SQLException {
		super(persistenceFile);
		this.sslCertificate = sslCertificate;
	}

	/**
	 * Sleep delay is one hour when successful or five minutes when unsuccessful.
	 */
	@Override
	protected long getSleepDelay(boolean lastSuccessful, AlertLevel alertLevel) {
		return lastSuccessful && alertLevel == AlertLevel.NONE ? NONE_SLEEP_DELAY : 5 * 60 * 1000;
	}

	/**
	 * Determines the alert message for the provided result.
	 * The alert level is the first result of the highest alert level.
	 */
	@Override
	public AlertLevelAndMessage getAlertLevelAndMessage(AlertLevel curAlertLevel, TableResult result) {
		AlertLevel highestAlertLevel = AlertLevel.NONE;
		Function<Locale,String> highestAlertMessage = null;
		if(result.isError()) {
			highestAlertLevel = result.getAlertLevels().get(0);
			highestAlertMessage = locale -> result.getTableData(locale).get(0).toString();
		} else {
			List<?> tableData = result.getTableData(Locale.getDefault());
			List<AlertLevel> alertLevels = result.getAlertLevels();
			for(
				int index = 0, len = tableData.size();
				index < len;
				index += NUM_COLS
			) {
				AlertLevel alertLevel = alertLevels.get(index / NUM_COLS);
				if(alertLevel.compareTo(highestAlertLevel) > 0) {
					highestAlertLevel = alertLevel;
					String message = (String)tableData.get(index + 2);
					if(message == null || message.isEmpty()) {
						message = (String)tableData.get(index + 1);
					}
					final String msg = message;
					highestAlertMessage = locale -> msg;
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
			accessor.getMessage(locale, "SslCertificateNodeWorker.columnHeader.check"),
			accessor.getMessage(locale, "SslCertificateNodeWorker.columnHeader.value"),
			accessor.getMessage(locale, "SslCertificateNodeWorker.columnHeader.message")
		);
	}

	@Override
	protected List<Certificate.Check> getQueryResult() throws Exception {
		return sslCertificate.check();
	}

	@Override
	protected SerializableFunction<Locale,List<Object>> getTableData(List<Certificate.Check> results) throws Exception {
		List<Object> tableData = new ArrayList<>(results.size() * NUM_COLS);
		for(Certificate.Check result : results) {
			tableData.add(result.getCheck());
			tableData.add(result.getValue());
			tableData.add(result.getMessage());
		}
		return locale -> tableData;
	}

	@Override
	protected List<AlertLevel> getAlertLevels(List<Certificate.Check> results) {
		List<AlertLevel> alertLevels = new ArrayList<>(results.size());
		for(Certificate.Check result : results) {
			alertLevels.add(AlertLevelUtils.getMonitoringAlertLevel(result.getAlertLevel()));
		}
		return alertLevels;
	}
}
