/*
 * Copyright 2008-2013, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TimeResult;
import com.aoindustries.sql.MilliInterval;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * The clock skew for a single sample in milliseconds is calculated as follows:
 *      st: remote system time (in milliseconds from Epoch)
 *      rt: request time (in milliseconds from Epoch)
 *      l:  request latency (in nanoseconds)
 *
 *      skew = st - (rt + round(l/2000000))
 *
 * Alert levels are:
 *          &gt;=1 minute  Critical
 *          &gt;=4 seconds High
 *          &gt;=2 seconds Medium
 *          &gt;=1 second  Low
 *          &lt;1  second  None
 *
 * @author  AO Industries, Inc.
 */
class TimeNodeWorker extends TableMultiResultNodeWorker<MilliInterval,TimeResult> {

	/**
	 * One unique worker is made per persistence directory (and should match aoServer exactly)
	 */
	private static final Map<String, TimeNodeWorker> workerCache = new HashMap<>();
	static TimeNodeWorker getWorker(File persistenceDirectory, AOServer aoServer) throws IOException {
		String path = persistenceDirectory.getCanonicalPath();
		synchronized(workerCache) {
			TimeNodeWorker worker = workerCache.get(path);
			if(worker==null) {
				worker = new TimeNodeWorker(persistenceDirectory, aoServer);
				workerCache.put(path, worker);
			} else {
				if(!worker._aoServer.equals(aoServer)) throw new AssertionError("worker.aoServer!=aoServer: "+worker._aoServer+"!="+aoServer);
			}
			return worker;
		}
	}

	final private AOServer _aoServer;
	private AOServer currentAOServer;

	private TimeNodeWorker(File persistenceDirectory, AOServer aoServer) throws IOException {
		super(new File(persistenceDirectory, "time"), new TimeResultSerializer());
		this._aoServer = currentAOServer = aoServer;
	}

	@Override
	protected int getHistorySize() {
		return 2000;
	}

	@Override
	protected MilliInterval getSample() throws Exception {
		// Get the latest limits
		currentAOServer = _aoServer.getTable().getConnector().getAoServers().get(_aoServer.getKey().intValue());

		long requestTime = System.currentTimeMillis();
		long startNanos = System.nanoTime();
		long systemTime = currentAOServer.getSystemTimeMillis();
		long latency = System.nanoTime() - startNanos;
		long lRemainder = latency % 2000000;
		long skew = systemTime - (requestTime + latency/2000000);
		if(lRemainder >= 1000000) skew--;

		return new MilliInterval(skew);
	}

	private static AlertLevel getAlertLevel(long skew) {
		if(skew >= 60000 || skew <= -60000) return AlertLevel.CRITICAL;
		if(skew >=  4000 || skew <=  -4000) return AlertLevel.HIGH;
		if(skew >=  2000 || skew <=  -2000) return AlertLevel.MEDIUM;
		if(skew >=  1000 || skew <=  -1000) return AlertLevel.MEDIUM;
		return AlertLevel.NONE;
	}

	@Override
	protected AlertLevelAndMessage getAlertLevelAndMessage(MilliInterval sample, Iterable<? extends TimeResult> previousResults) throws Exception {
		final long currentSkew = sample.getIntervalMillis();

		return new AlertLevelAndMessage(
			getAlertLevel(currentSkew),
			locale -> accessor.getMessage(
				locale,
				"TimeNodeWorker.alertMessage",
				currentSkew
			)
		);
	}

	@Override
	protected TimeResult newErrorResult(long time, long latency, AlertLevel alertLevel, String error) {
		return new TimeResult(time, latency, alertLevel, error);
	}

	@Override
	protected TimeResult newSampleResult(long time, long latency, AlertLevel alertLevel, MilliInterval sample) {
		return new TimeResult(time, latency, alertLevel, sample.getIntervalMillis());
	}
}
