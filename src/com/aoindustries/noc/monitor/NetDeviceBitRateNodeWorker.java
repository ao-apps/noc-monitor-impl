/*
 * Copyright 2008-2013, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.NetDevice;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.NetDeviceBitRateResult;
import com.aoindustries.util.StringUtility;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * network traffic (but no alerts on loopback by default)
 *      configurable limits per alert level per net_device
 *      based on 5-minute averages, sampled every five minutes, will take up to 20 minutes to buzz
 *
 * @author  AO Industries, Inc.
 */
class NetDeviceBitRateNodeWorker extends TableMultiResultNodeWorker<List<Object>,NetDeviceBitRateResult> {

	/**
	 * The number of bytes overhead for each Ethernet frame, including interframe gap, assuming no VLAN tag.
	 *
	 * Preamble + Start of frame + CRC + Interframe gap
	 */
	private static final int FRAME_ADDITIONAL_BYTES = 7 + 1 + 4 + 12;

	/**
	 * One unique worker is made per persistence directory (and should match the net device exactly)
	 */
	private static final Map<String, NetDeviceBitRateNodeWorker> workerCache = new HashMap<>();
	static NetDeviceBitRateNodeWorker getWorker(File persistenceDirectory, NetDevice netDevice) throws IOException {
		String path = persistenceDirectory.getCanonicalPath();
		synchronized(workerCache) {
			NetDeviceBitRateNodeWorker worker = workerCache.get(path);
			if(worker==null) {
				worker = new NetDeviceBitRateNodeWorker(persistenceDirectory, netDevice);
				workerCache.put(path, worker);
			} else {
				if(!worker._netDevice.equals(netDevice)) throw new AssertionError("worker.netDevice!=netDevice: "+worker._netDevice+"!="+netDevice);
			}
			return worker;
		}
	}

	// Will use whichever connector first created this worker, even if other accounts connect later.
	final private NetDevice _netDevice;
	private NetDevice _currentNetDevice;

	private NetDeviceBitRateNodeWorker(File persistenceDirectory, NetDevice netDevice) throws IOException {
		super(new File(persistenceDirectory, "bit_rate"), new NetDeviceBitRateResultSerializer());
		this._netDevice = _currentNetDevice = netDevice;
	}

	@Override
	protected int getHistorySize() {
		return 2000;
	}

	private long lastStatsTime = -1;
	private long lastTxBytes = -1;
	private long lastRxBytes = -1;
	private long lastTxPackets = -1;
	private long lastRxPackets = -1;

	@Override
	protected long getSleepDelay(boolean lastSuccessful, AlertLevel alertLevel) {
		return 5L * 60000L;
	}

	@Override
	protected List<Object> getSample() throws Exception {
		// Get the latest object
		_currentNetDevice = _netDevice.getTable().getConnector().getNetDevices().get(_netDevice.getPkey());

		// Get the current state
		String stats = _currentNetDevice.getStatisticsReport();
		List<String> lines = StringUtility.splitLines(stats);
		if(lines.size()!=5) throw new ParseException("Should have five lines in the stats, have "+lines.size(), 0);
		long thisStatsTime = Long.parseLong(lines.get(0));

		// values of -1 indicate a server-side detected reset
		long thisTxBytes = Long.parseLong(lines.get(1));
		long thisRxBytes = Long.parseLong(lines.get(2));
		long thisTxPackets = Long.parseLong(lines.get(3));
		long thisRxPackets = Long.parseLong(lines.get(4));

		try {
			// Calculate rates from previous state
			long txBitsPerSecond;
			long rxBitsPerSecond;
			long txPacketsPerSecond;
			long rxPacketsPerSecond;
			if(lastStatsTime==-1) { // First report
				txBitsPerSecond = -1;
				rxBitsPerSecond = -1;
				txPacketsPerSecond = -1;
				rxPacketsPerSecond = -1;
			} else if(lastStatsTime>=thisStatsTime) { // Time reset to the past
				throw new Exception("Server time reset to the past");
			} else if(
				// values of -1 indicate a server-side detected reset
				thisTxBytes==-1 || thisTxBytes<lastTxBytes
				|| thisRxBytes==-1 || thisRxBytes<lastRxBytes
				|| thisTxPackets==-1 || thisTxPackets<lastTxPackets
				|| thisRxPackets==-1 || thisRxPackets<lastRxPackets
			) { // device counters reset
				throw new Exception("Device counters reset");
			} else {
				long timeDiff = thisStatsTime - lastStatsTime;
				long txNumPackets = thisTxPackets - lastTxPackets;
				long rxNumPackets = thisRxPackets - lastRxPackets;
				txPacketsPerSecond = txNumPackets*1000 / timeDiff;
				rxPacketsPerSecond = rxNumPackets*1000 / timeDiff;
				txBitsPerSecond = (thisTxBytes - lastTxBytes + FRAME_ADDITIONAL_BYTES * txNumPackets)*8000 / timeDiff;
				rxBitsPerSecond = (thisRxBytes - lastRxBytes + FRAME_ADDITIONAL_BYTES * rxNumPackets)*8000 / timeDiff;
			}
			// Display the alert thresholds
			List<Object> sample = new ArrayList<>(8);
			sample.add(txBitsPerSecond);
			sample.add(rxBitsPerSecond);
			sample.add(txPacketsPerSecond);
			sample.add(rxPacketsPerSecond);
			sample.add(_currentNetDevice.getMonitoringBitRateLow());
			sample.add(_currentNetDevice.getMonitoringBitRateMedium());
			sample.add(_currentNetDevice.getMonitoringBitRateHigh());
			sample.add(_currentNetDevice.getMonitoringBitRateCritical());
			return sample;
		} finally {
			// Store for the next report
			lastStatsTime = thisStatsTime;
			// values of -1 indicate a server-side detected reset
			lastTxBytes = thisTxBytes==-1 ? 0 : thisTxBytes;
			lastRxBytes = thisRxBytes==-1 ? 0 : thisRxBytes;
			lastTxPackets = thisTxPackets==-1 ? 0 : thisTxPackets;
			lastRxPackets = thisRxPackets==-1 ? 0 : thisRxPackets;
		}
	}

	@Override
	protected AlertLevelAndMessage getAlertLevelAndMessage(List<Object> sample, Iterable<? extends NetDeviceBitRateResult> previousResults) throws Exception {
		long txBitsPerSecond = (Long)sample.get(0);
		long rxBitsPerSecond = (Long)sample.get(1);
		if(txBitsPerSecond==-1 || rxBitsPerSecond==-1) {
			return new AlertLevelAndMessage(AlertLevel.UNKNOWN, null);
		}
		long bps;
		String direction;
		if(txBitsPerSecond>rxBitsPerSecond) {
			// Base result on tx
			bps = txBitsPerSecond;
			direction = "tx";
		} else {
			// Base result on rx
			bps = rxBitsPerSecond;
			direction = "rx";
		}

		// Get the alert limits
		long bitRateCritical = _currentNetDevice.getMonitoringBitRateCritical();
		if(bitRateCritical!=-1 && bps>=bitRateCritical) {
			return new AlertLevelAndMessage(
				AlertLevel.CRITICAL,
				locale -> accessor.getMessage(
					locale,
					"NetDeviceBitRateNodeWorker.alertMessage."+direction+".critical",
					bitRateCritical,
					bps
				)
			);
		}
		long bitRateHigh = _currentNetDevice.getMonitoringBitRateHigh();
		if(bitRateHigh!=-1 && bps>=bitRateHigh) {
			return new AlertLevelAndMessage(
				AlertLevel.HIGH,
				locale -> accessor.getMessage(
					locale,
					"NetDeviceBitRateNodeWorker.alertMessage."+direction+".high",
					bitRateHigh,
					bps
				)
			);
		}
		long bitRateMedium = _currentNetDevice.getMonitoringBitRateMedium();
		if(bitRateMedium!=-1 && bps>=bitRateMedium) {
			return new AlertLevelAndMessage(
				AlertLevel.MEDIUM,
				locale -> accessor.getMessage(
					locale,
					"NetDeviceBitRateNodeWorker.alertMessage."+direction+".medium",
					bitRateMedium,
					bps
				)
			);
		}
		long bitRateLow = _currentNetDevice.getMonitoringBitRateLow();
		if(bitRateLow!=-1 && bps>=bitRateLow) {
			return new AlertLevelAndMessage(
				AlertLevel.LOW,
				locale -> accessor.getMessage(
					locale,
					"NetDeviceBitRateNodeWorker.alertMessage."+direction+".low",
					bitRateLow,
					bps
				)
			);
		}
		return new AlertLevelAndMessage(
			AlertLevel.NONE,
			locale -> accessor.getMessage(
				locale,
				"NetDeviceBitRateNodeWorker.alertMessage."+direction+".none",
				bps
			)
		);
	}

	@Override
	protected NetDeviceBitRateResult newErrorResult(long time, long latency, AlertLevel alertLevel, String error) {
		return new NetDeviceBitRateResult(time, latency, alertLevel, error);
	}

	@Override
	protected NetDeviceBitRateResult newSampleResult(long time, long latency, AlertLevel alertLevel, List<Object> sample) {
		return new NetDeviceBitRateResult(
			time,
			latency,
			alertLevel,
			(Long)sample.get(0),
			(Long)sample.get(1),
			(Long)sample.get(2),
			(Long)sample.get(3),
			(Long)sample.get(4),
			(Long)sample.get(5),
			(Long)sample.get(6),
			(Long)sample.get(7)
		);
	}
}
