/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2018, 2020  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of noc-monitor-impl.
 *
 * noc-monitor-impl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * noc-monitor-impl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with noc-monitor-impl.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.noc.monitor.web;

import com.aoindustries.aoserv.client.web.HttpdServer;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.TableMultiResultNodeWorker;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.HttpdServerResult;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author  AO Industries, Inc.
 */
class HttpdServerNodeWorker extends TableMultiResultNodeWorker<List<Integer>,HttpdServerResult> {

	private static final boolean DEBUG = false;

	/**
	 * One unique worker is made per persistence file (and should match httpdServer exactly)
	 */
	private static final Map<String, HttpdServerNodeWorker> workerCache = new HashMap<>();
	static HttpdServerNodeWorker getWorker(File persistenceFile, HttpdServer httpdServer) throws IOException {
		String path = persistenceFile.getCanonicalPath();
		synchronized(workerCache) {
			HttpdServerNodeWorker worker = workerCache.get(path);
			if(worker==null) {
				if(DEBUG) System.err.println("Creating new worker for " + httpdServer.getName());
				worker = new HttpdServerNodeWorker(persistenceFile, httpdServer);
				workerCache.put(path, worker);
			} else {
				if(DEBUG) System.err.println("Found existing worker for " + httpdServer.getName());
				if(!worker._httpdServer.equals(httpdServer)) throw new AssertionError("worker.httpdServer!=httpdServer: "+worker._httpdServer+"!="+httpdServer);
			}
			return worker;
		}
	}

	final private HttpdServer _httpdServer;
	private HttpdServer currentHttpdServer;

	private HttpdServerNodeWorker(File persistenceFile, HttpdServer httpdServer) throws IOException {
		super(persistenceFile, new HttpdServerResultSerializer());
		this._httpdServer = currentHttpdServer = httpdServer;
	}

	@Override
	protected int getHistorySize() {
		return 2000;
	}

	@Override
	protected List<Integer> getSample() throws Exception {
		// Get the latest limits
		currentHttpdServer = _httpdServer.getTable().getConnector().getWeb().getHttpdServer().get(_httpdServer.getPkey());
		int concurrency = currentHttpdServer.getConcurrency();
		return Arrays.asList(
			concurrency,
			currentHttpdServer.getMaxConcurrency(),
			currentHttpdServer.getMonitoringConcurrencyLow(),
			currentHttpdServer.getMonitoringConcurrencyMedium(),
			currentHttpdServer.getMonitoringConcurrencyHigh(),
			currentHttpdServer.getMonitoringConcurrencyCritical()
		);
	}

	@Override
	protected AlertLevelAndMessage getAlertLevelAndMessage(List<Integer> sample, Iterable<? extends HttpdServerResult> previousResults) throws Exception {
		int concurrency = sample.get(0);
		int concurrencyCritical = currentHttpdServer.getMonitoringConcurrencyCritical();
		if(concurrencyCritical != -1 && concurrency >= concurrencyCritical) {
			return new AlertLevelAndMessage(
				AlertLevel.CRITICAL,
				locale -> accessor.getMessage(
					locale,
					"HttpdServerNodeWorker.alertMessage.critical",
					concurrencyCritical,
					concurrency
				)
			);
		}
		int concurrencyHigh = currentHttpdServer.getMonitoringConcurrencyHigh();
		if(concurrencyHigh != -1 && concurrency >= concurrencyHigh) {
			return new AlertLevelAndMessage(
				AlertLevel.HIGH,
				locale -> accessor.getMessage(
					locale,
					"HttpdServerNodeWorker.alertMessage.high",
					concurrencyHigh,
					concurrency
				)
			);
		}
		int concurrencyMedium = currentHttpdServer.getMonitoringConcurrencyMedium();
		if(concurrencyMedium != -1 && concurrency >= concurrencyMedium) {
			return new AlertLevelAndMessage(
				AlertLevel.MEDIUM,
				locale -> accessor.getMessage(
					locale,
					"HttpdServerNodeWorker.alertMessage.medium",
					concurrencyMedium,
					concurrency
				)
			);
		}
		int concurrencyLow = currentHttpdServer.getMonitoringConcurrencyLow();
		if(concurrencyLow != -1 && concurrency >= concurrencyLow) {
			return new AlertLevelAndMessage(
				AlertLevel.LOW,
				locale -> accessor.getMessage(
					locale,
					"HttpdServerNodeWorker.alertMessage.low",
					concurrencyLow,
					concurrency
				)
			);
		}
		if(concurrencyLow == -1) {
			return new AlertLevelAndMessage(
				AlertLevel.NONE,
				locale -> accessor.getMessage(
					locale,
					"HttpdServerNodeWorker.alertMessage.notAny",
					concurrency
				)
			);
		} else {
			return new AlertLevelAndMessage(
				AlertLevel.NONE,
				locale -> accessor.getMessage(
					locale,
					"HttpdServerNodeWorker.alertMessage.none",
					concurrencyLow,
					concurrency
				)
			);
		}
	}

	@Override
	protected HttpdServerResult newErrorResult(long time, long latency, AlertLevel alertLevel, String error) {
		return new HttpdServerResult(time, latency, alertLevel, error);
	}

	@Override
	protected HttpdServerResult newSampleResult(long time, long latency, AlertLevel alertLevel, List<Integer> sample) {
		return new HttpdServerResult(
			time,
			latency,
			alertLevel,
			sample.get(0),
			sample.get(1),
			sample.get(2),
			sample.get(3),
			sample.get(4),
			sample.get(5)
		);
	}
}
