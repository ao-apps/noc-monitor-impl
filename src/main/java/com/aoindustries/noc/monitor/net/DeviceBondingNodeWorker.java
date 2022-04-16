/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008-2013, 2014, 2016, 2018, 2020, 2021, 2022  AO Industries, Inc.
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
 * along with noc-monitor-impl.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoindustries.noc.monitor.net;

import com.aoapps.lang.EnumUtils;
import com.aoapps.lang.Strings;
import com.aoindustries.aoserv.client.net.Device;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;
import com.aoindustries.noc.monitor.SingleResultNodeWorker;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.SingleResult;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The workers for bonding monitoring.
 *
 * @author  AO Industries, Inc.
 */
class DeviceBondingNodeWorker extends SingleResultNodeWorker {

	/**
	 * One unique worker is made per persistence file (and should match the net device exactly)
	 */
	private static final Map<String, DeviceBondingNodeWorker> workerCache = new HashMap<>();
	static DeviceBondingNodeWorker getWorker(File persistenceFile, Device device) throws IOException {
		String path = persistenceFile.getCanonicalPath();
		synchronized(workerCache) {
			DeviceBondingNodeWorker worker = workerCache.get(path);
			if(worker==null) {
				worker = new DeviceBondingNodeWorker(persistenceFile, device);
				workerCache.put(path, worker);
			} else {
				if(!worker.device.equals(device)) throw new AssertionError("worker.device!=device: "+worker.device+"!="+device);
			}
			return worker;
		}
	}

	// Will use whichever connector first created this worker, even if other accounts connect later.
	private volatile Device device;

	private DeviceBondingNodeWorker(File persistenceFile, Device device) {
		super(persistenceFile);
		this.device = device;
	}

	@Override
	protected String getReport() throws IOException, SQLException {
		// Get a new version of the Device object
		Device newNetDevice = device.getTable().getConnector().getNet().getDevice().get(device.getPkey());
		if(newNetDevice!=null) device = newNetDevice;
		// Get report from server
		return device.getBondingReport();
	}

	private enum BondingMode {
		ACTIVE_BACKUP,
		ROUND_ROBIN,
		XOR,
		UNKNOWN
	}

	/**
	 * Determines the alert level for the provided result.
	 */
	@Override
	protected AlertLevelAndMessage getAlertLevelAndMessage(AlertLevel curAlertLevel, SingleResult result) {
		Function<Locale, String> error = result.getError();
		if(error != null) {
			return new AlertLevelAndMessage(
				// Don't downgrade UNKNOWN to CRITICAL on error
				EnumUtils.max(AlertLevel.CRITICAL, curAlertLevel),
				locale -> PACKAGE_RESOURCES.getMessage(
					locale,
					"NetDeviceBondingNode.alertMessage.error",
					error.apply(locale)
				)
			);
		}
		String report = result.getReport();
		List<String> lines = Strings.splitLines(report);
		final int upCount;
		final int downCount;
		{
			int up = 0;
			int down = 0;
			boolean skippedFirst = false;
			for(String line : lines) {
				if(line.startsWith("MII Status: ")) {
					if(!skippedFirst) {
						skippedFirst = true;
					} else {
						if(line.equals("MII Status: up")) up++;
						else down++;
					}
				}
			}
			upCount = up;
			downCount = down;
		}
		AlertLevel alertLevel;
		Function<Locale, String> alertMessage = locale -> PACKAGE_RESOURCES.getMessage(
			locale,
			"NetDeviceBondingNode.alertMessage.counts",
			upCount,
			downCount
		);
		if(upCount==0) {
			alertLevel = AlertLevel.CRITICAL;
		} else if(downCount!=0) {
			alertLevel = AlertLevel.HIGH;
		} else {
			alertLevel = AlertLevel.NONE;
			// Look for any non-duplex
			for(String line : lines) {
				if(line.startsWith("Duplex: ")) {
					if(!line.equals("Duplex: full")) {
						alertLevel = AlertLevel.LOW;
						alertMessage = locale -> PACKAGE_RESOURCES.getMessage(
							locale,
							"NetDeviceBondingNode.alertMessage.notFullDuplex",
							line
						);
						break;
					}
				}
			}
			// Find the bonding mode
			BondingMode bondindMode = null;
			for(String line : lines) {
				if(line.startsWith("Bonding Mode: ")) {
					switch (line) {
						case "Bonding Mode: fault-tolerance (active-backup)":
							bondindMode = BondingMode.ACTIVE_BACKUP;
							break;
						case "Bonding Mode: load balancing (round-robin)":
							bondindMode = BondingMode.ROUND_ROBIN;
							break;
						case "Bonding Mode: load balancing (xor)":
							bondindMode = BondingMode.XOR;
							break;
						default:
							bondindMode = BondingMode.UNKNOWN;
							alertLevel = AlertLevel.HIGH;
							alertMessage = locale -> PACKAGE_RESOURCES.getMessage(
								locale,
								"NetDeviceBondingNode.alertMessage.unexpectedBondingMode",
								line
							);	break;
					}
					break;
				}
			}
			if(bondindMode == null) {
				alertLevel = AlertLevel.HIGH;
				alertMessage = locale -> PACKAGE_RESOURCES.getMessage(locale, "NetDeviceBondingNode.alertMessage.noBondingMode");
			} else if(bondindMode == BondingMode.ACTIVE_BACKUP) {
				// Look for any mismatched speed
				for(String line : lines) {
					if(line.startsWith("Speed: ")) {
						long bps;
						switch (line) {
							case "Speed: 10000 Mbps":
								bps = 10000000000L;
								break;
							case "Speed: 1000 Mbps":
								bps = 1000000000L;
								break;
							case "Speed: 100 Mbps":
								bps = 100000000L;
								break;
							default:
								bps = -1L;
								break;
						}
						if(bps == -1L) {
							alertLevel = AlertLevel.HIGH;
							alertMessage = locale -> PACKAGE_RESOURCES.getMessage(
								locale,
								"NetDeviceBondingNode.alertMessage.unknownSpeed",
								line
							);
							break;
						}
						long maxBitRate = device.getMaxBitRate();
						if(maxBitRate!=-1 && bps != maxBitRate) {
							alertLevel = AlertLevel.HIGH;
							alertMessage = locale -> PACKAGE_RESOURCES.getMessage(
								locale,
								"NetDeviceBondingNode.alertMessage.speedMismatch",
								maxBitRate,
								bps
							);
							break;
						}
					}
				}
			} else if(
				bondindMode == BondingMode.ROUND_ROBIN
				|| bondindMode == BondingMode.XOR
			) {
				// Get the sum of all speeds found
				final long totalBps;
				{
					long sum = 0;
					for(String line : lines) {
						if(line.startsWith("Speed: ")) {
							long bps;
							switch (line) {
								case "Speed: 10000 Mbps":
									bps = 10000000000L;
									break;
								case "Speed: 1000 Mbps":
									bps = 1000000000L;
									break;
								case "Speed: 100 Mbps":
									bps = 100000000L;
									break;
								default:
									bps = -1L;
									break;
							}
							if(bps == -1L) {
								alertLevel = AlertLevel.HIGH;
								alertMessage = locale -> PACKAGE_RESOURCES.getMessage(
									locale,
									"NetDeviceBondingNode.alertMessage.unknownSpeed",
									line
								);
								break;
							} else {
								sum += bps;
							}
						}
					}
					totalBps = sum;
				}
				if(alertLevel.compareTo(AlertLevel.HIGH) < 0 ) {
					long maxBitRate = device.getMaxBitRate();
					if(maxBitRate!=-1 && totalBps != maxBitRate) {
						alertLevel = AlertLevel.HIGH;
						alertMessage = locale -> PACKAGE_RESOURCES.getMessage(
							locale,
							"NetDeviceBondingNode.alertMessage.speedMismatch",
							maxBitRate,
							totalBps
						);
					}
				}
			} else if(bondindMode == BondingMode.UNKNOWN) {
				// alertLevel and alertMessage set above
			} else {
				throw new AssertionError();
			}
		}
		return new AlertLevelAndMessage(alertLevel, alertMessage);
	}
}
