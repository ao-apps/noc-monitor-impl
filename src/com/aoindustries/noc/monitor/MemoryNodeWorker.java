/*
 * Copyright 2008-2012, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.ApproximateDisplayExactSize;
import com.aoindustries.noc.monitor.common.MemoryResult;
import com.aoindustries.util.StringUtility;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory + Swap space - checked every minute.
 *      memory just informational
 *      percent of memory+swap used controls alert, buffers and cache do not count towards used
 *          &gt;=95% Critical
 *          &gt;=90% High
 *          &gt;=85% Medium
 *          &gt;=80% Low
 *          &lt;80%  None
 *
 * @author  AO Industries, Inc.
 */
class MemoryNodeWorker extends TableMultiResultNodeWorker<List<ApproximateDisplayExactSize>,MemoryResult> {

	/**
	 * One unique worker is made per persistence directory (and should match aoServer exactly)
	 */
	private static final Map<String, MemoryNodeWorker> workerCache = new HashMap<>();
	static MemoryNodeWorker getWorker(File persistenceDirectory, AOServer aoServer) throws IOException {
		String path = persistenceDirectory.getCanonicalPath();
		synchronized(workerCache) {
			MemoryNodeWorker worker = workerCache.get(path);
			if(worker==null) {
				worker = new MemoryNodeWorker(persistenceDirectory, aoServer);
				workerCache.put(path, worker);
			} else {
				if(!worker._aoServer.equals(aoServer)) throw new AssertionError("worker.aoServer!=aoServer: "+worker._aoServer+"!="+aoServer);
			}
			return worker;
		}
	}

	final private AOServer _aoServer;
	private AOServer currentAOServer;

	private MemoryNodeWorker(File persistenceDirectory, AOServer aoServer) throws IOException {
		super(new File(persistenceDirectory, "meminfo"), new MemoryResultSerializer());
		this._aoServer = currentAOServer = aoServer;
	}

	@Override
	protected int getHistorySize() {
		return 2000;
	}

	@Override
	protected List<ApproximateDisplayExactSize> getSample() throws Exception {
		// Get the latest limits
		currentAOServer = _aoServer.getTable().getConnector().getAoServers().get(_aoServer.getKey().intValue());
		String meminfo = currentAOServer.getMemInfoReport();
		long memTotal = -1;
		long memFree = -1;
		long buffers = -1;
		long cached = -1;
		long swapTotal = -1;
		long swapFree = -1;

		List<String> lines = StringUtility.splitLines(meminfo);
		for(String line : lines) {
			if(line.endsWith(" kB")) { //throw new ParseException("Line doesn't end with \" kB\": "+line, 0);
				line = line.substring(0, line.length()-3);
				if(line.startsWith("MemTotal:")) memTotal = Long.parseLong(line.substring("MemTotal:".length()).trim()) << 10; // * 1024
				else if(line.startsWith("MemFree:")) memFree = Long.parseLong(line.substring("MemFree:".length()).trim()) << 10;
				else if(line.startsWith("Buffers:")) buffers = Long.parseLong(line.substring("Buffers:".length()).trim()) << 10;
				else if(line.startsWith("Cached:")) cached = Long.parseLong(line.substring("Cached:".length()).trim()) << 10;
				else if(line.startsWith("SwapTotal:")) swapTotal = Long.parseLong(line.substring("SwapTotal:".length()).trim()) << 10;
				else if(line.startsWith("SwapFree:")) swapFree = Long.parseLong(line.substring("SwapFree:".length()).trim()) << 10;
			}
		}
		if(memTotal==-1) throw new ParseException("Unable to find MemTotal:", 0);
		if(memFree==-1) throw new ParseException("Unable to find MemFree:", 0);
		if(buffers==-1) throw new ParseException("Unable to find Buffers:", 0);
		if(cached==-1) throw new ParseException("Unable to find Cached:", 0);
		if(swapTotal==-1) throw new ParseException("Unable to find SwapTotal:", 0);
		if(swapFree==-1) throw new ParseException("Unable to find SwapFree:", 0);

		return Arrays.asList(
			new ApproximateDisplayExactSize(memTotal),
			new ApproximateDisplayExactSize(memFree),
			new ApproximateDisplayExactSize(buffers),
			new ApproximateDisplayExactSize(cached),
			new ApproximateDisplayExactSize(swapTotal),
			new ApproximateDisplayExactSize(swapFree)
		);
	}

	private static AlertLevel getAlertLevel(long memoryPercent) {
		if(memoryPercent<0) return AlertLevel.UNKNOWN;
		if(memoryPercent>=95) return AlertLevel.CRITICAL;
		if(memoryPercent>=90) return AlertLevel.HIGH;
		if(memoryPercent>=85) return AlertLevel.MEDIUM;
		if(memoryPercent>=80) return AlertLevel.LOW;
		return AlertLevel.NONE;
	}

	@Override
	protected AlertLevelAndMessage getAlertLevelAndMessage(List<ApproximateDisplayExactSize> sample, Iterable<? extends MemoryResult> previousResults) throws Exception {
		long memTotal = sample.get(0).getSize();
		long memFree = sample.get(1).getSize();
		long buffers = sample.get(2).getSize();
		long cached = sample.get(3).getSize();
		long swapTotal = sample.get(4).getSize();
		long swapFree = sample.get(5).getSize();
		long memoryPercent = ((memTotal - (memFree + buffers + cached)) + (swapTotal - swapFree)) * 100 / (memTotal + swapTotal);
		return new AlertLevelAndMessage(
			getAlertLevel(memoryPercent),
			locale -> accessor.getMessage(
				locale,
				"MemoryNodeWorker.alertMessage",
				memoryPercent
			)
		);
	}

	@Override
	protected MemoryResult newErrorResult(long time, long latency, AlertLevel alertLevel, String error) {
		return new MemoryResult(time, latency, alertLevel, error);
	}

	@Override
	protected MemoryResult newSampleResult(long time, long latency, AlertLevel alertLevel, List<ApproximateDisplayExactSize> sample) {
		return new MemoryResult(
			time,
			latency,
			alertLevel,
			sample.get(0).getSize(),
			sample.get(1).getSize(),
			sample.get(2).getSize(),
			sample.get(3).getSize(),
			sample.get(4).getSize(),
			sample.get(5).getSize()
		);
	}
}
