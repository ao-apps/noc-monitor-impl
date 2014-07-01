/*
 * Copyright 2008-2013, 2014 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.NetDevice;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.SingleResult;
import com.aoindustries.util.StringUtility;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The workers for bonding monitoring.
 *
 * @author  AO Industries, Inc.
 */
class NetDeviceBondingNodeWorker extends SingleResultNodeWorker {

    /**
     * One unique worker is made per persistence file (and should match the net device exactly)
     */
    private static final Map<String, NetDeviceBondingNodeWorker> workerCache = new HashMap<>();
    static NetDeviceBondingNodeWorker getWorker(File persistenceFile, NetDevice netDevice) throws IOException {
        String path = persistenceFile.getCanonicalPath();
        synchronized(workerCache) {
            NetDeviceBondingNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new NetDeviceBondingNodeWorker(persistenceFile, netDevice);
                workerCache.put(path, worker);
            } else {
                if(!worker.netDevice.equals(netDevice)) throw new AssertionError("worker.netDevice!=netDevice: "+worker.netDevice+"!="+netDevice);
            }
            return worker;
        }
    }

    // Will use whichever connector first created this worker, even if other accounts connect later.
    volatile private NetDevice netDevice;

    private NetDeviceBondingNodeWorker(File persistenceFile, NetDevice netDevice) {
        super(persistenceFile);
        this.netDevice = netDevice;
    }

    @Override
    protected String getReport() throws IOException, SQLException {
		// Get a new version of the NetDevice object
		NetDevice newNetDevice = netDevice.getTable().get(netDevice.getKey());
		if(newNetDevice!=null) netDevice = newNetDevice;
		// Get report from server
        return netDevice.getBondingReport();
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
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, SingleResult result) {
        if(result.getError()!=null) {
            return new AlertLevelAndMessage(
                AlertLevel.CRITICAL,
                accessor.getMessage(
                    //locale,
                    "NetDeviceBondingNode.alertMessage.error",
                    result.getError()
                )
            );
        }
        String report = result.getReport();
        List<String> lines = StringUtility.splitLines(report);
        int upCount = 0;
        int downCount = 0;
        boolean skippedFirst = false;
        for(String line : lines) {
            if(line.startsWith("MII Status: ")) {
                if(!skippedFirst) {
					skippedFirst = true;
				} else {
                    if(line.equals("MII Status: up")) upCount++;
                    else downCount++;
                }
			}
        }
        AlertLevel alertLevel;
		String alertMessage = accessor.getMessage(
			//locale,
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
						alertMessage = accessor.getMessage(
							//locale,
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
							alertMessage = accessor.getMessage(
								//locale,
								"NetDeviceBondingNode.alertMessage.unexpectedBondingMode",
								line
							);	break;
					}
					break;
				}
			}
			if(bondindMode == null) {
				alertLevel = AlertLevel.HIGH;
				alertMessage = accessor.getMessage(
					//locale,
					"NetDeviceBondingNode.alertMessage.noBondingMode"
				);
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
							alertMessage = accessor.getMessage(
								//locale,
								"NetDeviceBondingNode.alertMessage.unknownSpeed",
								line
							);
							break;
						}
						long maxBitRate = netDevice.getMaxBitRate();
						if(maxBitRate!=-1 && bps != maxBitRate) {
							alertLevel = AlertLevel.HIGH;
							alertMessage = accessor.getMessage(
								//locale,
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
				long totalBps = 0;
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
							alertMessage = accessor.getMessage(
								//locale,
								"NetDeviceBondingNode.alertMessage.unknownSpeed",
								line
							);
							break;
						} else {
							totalBps += bps;
						}
					}
				}
				if(alertLevel.compareTo(AlertLevel.HIGH) < 0 ) {
					long maxBitRate = netDevice.getMaxBitRate();
					if(maxBitRate!=-1 && totalBps != maxBitRate) {
						alertLevel = AlertLevel.HIGH;
						alertMessage = accessor.getMessage(
							//locale,
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
