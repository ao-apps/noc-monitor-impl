/*
 * Copyright 2008-2009, 2016 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TableResult;
import com.aoindustries.util.StringUtility;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The workers for hard drive temperature monitoring.
 * 
 * TODO: Keep historical data and warn if temp increases more than 20C/hour
 *
 * @author  AO Industries, Inc.
 */
class HardDrivesTemperatureNodeWorker extends TableResultNodeWorker<List<String>,String> {

	/**
	 * The normal alert thresholds.
	 */
	private static final int
		COLD_CRITICAL = 8,
		COLD_HIGH = 14,
		COLD_MEDIUM = 17,
		COLD_LOW = 20,
		HOT_LOW = 48,
		HOT_MEDIUM = 51,
		HOT_HIGH = 54,
		HOT_CRITICAL = 60
	;

	/**
	 * One unique worker is made per persistence file (and should match the aoServer exactly)
	 */
	private static final Map<String, HardDrivesTemperatureNodeWorker> workerCache = new HashMap<>();
	static HardDrivesTemperatureNodeWorker getWorker(File persistenceFile, AOServer aoServer) throws IOException {
		String path = persistenceFile.getCanonicalPath();
		synchronized(workerCache) {
			HardDrivesTemperatureNodeWorker worker = workerCache.get(path);
			if(worker==null) {
				worker = new HardDrivesTemperatureNodeWorker(persistenceFile, aoServer);
				workerCache.put(path, worker);
			} else {
				if(!worker.aoServer.equals(aoServer)) throw new AssertionError("worker.aoServer!=aoServer: "+worker.aoServer+"!="+aoServer);
			}
			return worker;
		}
	}

	// Will use whichever connector first created this worker, even if other accounts connect later.
	final private AOServer aoServer;

	HardDrivesTemperatureNodeWorker(File persistenceFile, AOServer aoServer) {
		super(persistenceFile);
		this.aoServer = aoServer;
	}

	/**
	 * Determines the alert message for the provided result.
	 */
	@Override
	protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, TableResult result) {
		AlertLevel highestAlertLevel = AlertLevel.NONE;
		String highestAlertMessage = "";
		List<?> tableData = result.getTableData();
		if(result.isError()) {
			highestAlertLevel = result.getAlertLevels().get(0);
			highestAlertMessage = tableData.get(0).toString();
		} else {
			List<AlertLevel> alertLevels = result.getAlertLevels();
			for(int index=0,len=tableData.size();index<len;index+=3) {
				AlertLevel alertLevel = alertLevels.get(index/3);
				if(alertLevel.compareTo(highestAlertLevel)>0) {
					highestAlertLevel = alertLevel;
					highestAlertMessage = tableData.get(index)+" "+tableData.get(index+1)+" "+tableData.get(index+2);
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
	protected List<String> getColumnHeaders(Locale locale) {
		List<String> columnHeaders = new ArrayList<>(3);
		columnHeaders.add(accessor.getMessage(/*locale,*/ "HardDrivesTemperatureNodeWorker.columnHeader.device"));
		columnHeaders.add(accessor.getMessage(/*locale,*/ "HardDrivesTemperatureNodeWorker.columnHeader.model"));
		columnHeaders.add(accessor.getMessage(/*locale,*/ "HardDrivesTemperatureNodeWorker.columnHeader.temperature"));
		return columnHeaders;
	}

	@Override
	protected List<String> getQueryResult(Locale locale) throws Exception {
		String report = aoServer.getHddTempReport();
		List<String> lines = StringUtility.splitLines(report);
		List<String> tableData = new ArrayList<>(lines.size()*3);
		int lineNum = 0;
		for(String line : lines) {
			lineNum++;
			List<String> values = StringUtility.splitString(line, ':');
			if(values.size()!=3) {
				throw new ParseException(
					accessor.getMessage(
						//locale,
						"HardDrivesTemperatureNodeWorker.alertMessage.badColumnCount",
						line
					),
					lineNum
				);
			}
			for(int c=0,len=values.size(); c<len; c++) {
				tableData.add(values.get(c).trim());
			}
		}
		return tableData;
	}

	@Override
	protected List<String> getTableData(List<String> tableData, Locale locale) throws Exception {
		return tableData;
	}

	@Override
	protected List<AlertLevel> getAlertLevels(List<String> tableData) {
		List<AlertLevel> alertLevels = new ArrayList<>(tableData.size()/3);
		for(int index=0,len=tableData.size();index<len;index+=3) {
			String value = tableData.get(index+2);
			AlertLevel alertLevel = AlertLevel.NONE;
			if(
				// These values all mean not monitored, keep at alert=NONE
				!"S.M.A.R.T. not available".equals(value)
				&& !"drive supported, but it doesn't have a temperature sensor.".equals(value)
				&& !"drive is sleeping".equals(value)
				&& !"no sensor".equals(value)
			) {
				// Parse the temperature value and compare
				boolean parsed;
				if(value.endsWith(" C")) {
					// A few hard drives read much differently than other drives, offset the thresholds here
					String hostname = aoServer.getHostname().toString();
					String device = tableData.get(index);
					int offset;
//                    if(
//                        hostname.equals("xen1.mob.aoindustries.com")
//                        && device.equals("/dev/sda")
//                    ) {
//                        offset = -7;
//                    } else if(
//                        hostname.equals("xen907-4.fc.aoindustries.com")
//                        && (
//                            device.equals("/dev/sda")
//                            || device.equals("/dev/sdb")
//                        )
//                    ) {
//                        offset = 12;
//                    } else {
						offset = 0;
//                    }
					String numString = value.substring(0, value.length()-2);
					try {
						int num = Integer.parseInt(numString);
						if(num<=(COLD_CRITICAL+offset) || num>=(HOT_CRITICAL+offset)) alertLevel = AlertLevel.CRITICAL;
						else if(num<=(COLD_HIGH+offset) || num>=(HOT_HIGH+offset)) alertLevel = AlertLevel.HIGH;
						else if(num<=(COLD_MEDIUM+offset) || num>=(HOT_MEDIUM+offset)) alertLevel = AlertLevel.MEDIUM;
						else if(num<=(COLD_LOW+offset) || num>=(HOT_LOW+offset)) alertLevel = AlertLevel.LOW;
						parsed = true;
					} catch(NumberFormatException err) {
						parsed = false;
					}
				} else {
					parsed = false;
				}
				if(!parsed) {
					alertLevel = AlertLevel.CRITICAL;
				}
			}
			alertLevels.add(alertLevel);
		}
		return alertLevels;
	}
}
