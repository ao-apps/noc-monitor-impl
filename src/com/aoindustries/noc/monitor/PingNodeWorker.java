/*
 * Copyright 2008-2012, 2016, 2017 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.IPAddress;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.PingResult;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Each worker may be shared by any number of <code>PingNodeImpl</code>s.
 * A worker performs the background pinging and reports its results to
 * the ping nodes.  All of the cached data is stored in a Locale-neutral
 * way and converted to Locale-specific representations as needed.
 *
 * @author  AO Industries, Inc.
 */
class PingNodeWorker extends TableMultiResultNodeWorker<Object,PingResult> {

	/**
	 * The ping timeout.
	 */
	private static final int TIMEOUT = 10000;

	/**
	 * One unique worker is made per persistence directory (and should match the IP address exactly)
	 */
	private static final Map<String, PingNodeWorker> workerCache = new HashMap<>();
	static PingNodeWorker getWorker(File persistenceDirectory, IPAddress ipAddress) throws IOException {
		String path = persistenceDirectory.getCanonicalPath();
		com.aoindustries.net.InetAddress ip = ipAddress.getInetAddress();
		com.aoindustries.net.InetAddress externalIp = ipAddress.getExternalIpAddress();
		com.aoindustries.net.InetAddress pingAddress = externalIp==null ? ip : externalIp;
		synchronized(workerCache) {
			PingNodeWorker worker = workerCache.get(path);
			if(worker==null) {
				worker = new PingNodeWorker(persistenceDirectory, pingAddress);
				workerCache.put(path, worker);
			} else {
				if(!worker.ipAddress.equals(pingAddress)) throw new AssertionError("worker.ipAddress!=pingAddress: "+worker.ipAddress+"!="+pingAddress);
			}
			return worker;
		}
	}

	/**
	 * The most recent timer task
	 */
	final private com.aoindustries.net.InetAddress ipAddress;

	private PingNodeWorker(File persistenceDirectory, com.aoindustries.net.InetAddress ipAddress) throws IOException {
		super(new File(persistenceDirectory, "pings"), new PingResultSerializer());
		this.ipAddress = ipAddress;
	}

	@Override
	protected int getHistorySize() {
		return 10000;
	}

	/**
	 * Uses a single sample object because no data is contained in the sample, only the timing information is maintained.
	 */
	private static final Object SAMPLE = new Object();

	@Override
	protected Object getSample(Locale locale) throws Exception {
		final InetAddress inetAddress = InetAddress.getByName(ipAddress.toString());
		boolean timeout = !inetAddress.isReachable(TIMEOUT);
		if(timeout) throw new TimeoutException(accessor.getMessage(/*locale,*/ "PingNodeWorker.error.timeout"));
		return SAMPLE;
	}

	/**
	 * Figures out the alert level.  It considers only the last 10 pings.  The number of timeouts follow:
	 *
	 * >=4  CRITICAL
	 * >=3  HIGH
	 * >=2  MEDIUM
	 * >=1  LOW
	 * =0   NONE
	 */
	private static AlertLevel getAlertLevel(int packetLossPercent) {
		if(packetLossPercent<0) return AlertLevel.UNKNOWN;
		if(packetLossPercent>=40) return AlertLevel.CRITICAL;
		if(packetLossPercent>=30) return AlertLevel.HIGH;
		if(packetLossPercent>=20) return AlertLevel.MEDIUM;
		if(packetLossPercent>=10) return AlertLevel.LOW;
		return AlertLevel.NONE;
	}

	/**
	 * Gets the packet loss percent.
	 */
	private static int getPacketLossPercent(Iterable<? extends PingResult> previousResults) {
		int timeouts = 0;
		// The current value is never a timeout to get this far
		int checked = 1;
		// The history
		for(PingResult previousResult : previousResults) {
			if(previousResult.getError()!=null) timeouts++;
			checked++;
			if(checked>=10) break;
		}
		return timeouts * 10;
	}

	@Override
	protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, Object sample, Iterable<? extends PingResult> previousResults) throws Exception {
		int packetLossPercent = getPacketLossPercent(previousResults);
		return new AlertLevelAndMessage(
			getAlertLevel(packetLossPercent),
			accessor.getMessage(
				//locale,
				"PingNodeWorker.alertMessage",
				packetLossPercent
			)
		);
	}

	/**
	 * Since pings support timeout, no need to provide timeout through
	 * Future objects and ExecutorService.
	 */
	@Override
	protected boolean useFutureTimeout() {
		return false;
	}

	/**
	 * Sleeps one minute between checks.
	 */
	@Override
	protected long getSleepDelay(boolean lastSuccessful, AlertLevel alertLevel) {
		return 60000;
	}

	@Override
	protected PingResult newErrorResult(long time, long latency, AlertLevel alertLevel, String error) {
		return new PingResult(time, latency, alertLevel, error);
	}

	@Override
	protected PingResult newSampleResult(long time, long latency, AlertLevel alertLevel, Object sample) {
		return new PingResult(time, latency, alertLevel);
	}
}
