/*
 * Copyright 2008-2009, 2016, 2018, 2019, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.infrastructure;

import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.lang.Strings;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.TableResultNodeWorker;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.SerializableFunction;
import com.aoindustries.noc.monitor.common.TableResult;
import com.aoindustries.text.LocalizedParseException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

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
	 * One unique worker is made per persistence file (and should match the linuxServer exactly)
	 */
	private static final Map<String, HardDrivesTemperatureNodeWorker> workerCache = new HashMap<>();
	static HardDrivesTemperatureNodeWorker getWorker(File persistenceFile, Server linuxServer) throws IOException {
		String path = persistenceFile.getCanonicalPath();
		synchronized(workerCache) {
			HardDrivesTemperatureNodeWorker worker = workerCache.get(path);
			if(worker==null) {
				worker = new HardDrivesTemperatureNodeWorker(persistenceFile, linuxServer);
				workerCache.put(path, worker);
			} else {
				if(!worker.linuxServer.equals(linuxServer)) throw new AssertionError("worker.linuxServer!=linuxServer: "+worker.linuxServer+"!="+linuxServer);
			}
			return worker;
		}
	}

	// Will use whichever connector first created this worker, even if other accounts connect later.
	final private Server linuxServer;

	HardDrivesTemperatureNodeWorker(File persistenceFile, Server linuxServer) {
		super(persistenceFile);
		this.linuxServer = linuxServer;
	}

	/**
	 * Determines the alert message for the provided result.
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
			for(int index=0,len=tableData.size();index<len;index+=3) {
				AlertLevel alertLevel = alertLevels.get(index/3);
				if(alertLevel.compareTo(highestAlertLevel)>0) {
					highestAlertLevel = alertLevel;
					Object device = tableData.get(index);
					Object model = tableData.get(index+1);
					Object temperature = tableData.get(index+2);
					highestAlertMessage = locale -> device+" "+model+" "+temperature;
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
	protected SerializableFunction<Locale,List<String>> getColumnHeaders() {
		return locale -> Arrays.asList(
			accessor.getMessage(locale, "HardDrivesTemperatureNodeWorker.columnHeader.device"),
			accessor.getMessage(locale, "HardDrivesTemperatureNodeWorker.columnHeader.model"),
			accessor.getMessage(locale, "HardDrivesTemperatureNodeWorker.columnHeader.temperature")
		);
	}

	@Override
	protected List<String> getQueryResult() throws Exception {
		String report = linuxServer.getHddTempReport();
		List<String> lines = Strings.splitLines(report);
		List<String> tableData = new ArrayList<>(lines.size()*3);
		int lineNum = 0;
		for(String line : lines) {
			lineNum++;
			List<String> values = Strings.splitString(line, ':');
			if(values.size()!=3) {
				throw new LocalizedParseException(
					lineNum,
					accessor,
					"HardDrivesTemperatureNodeWorker.alertMessage.badColumnCount",
					line
				);
			}
			for(int c=0,len=values.size(); c<len; c++) {
				tableData.add(values.get(c).trim());
			}
		}
		return tableData;
	}

	@Override
	protected SerializableFunction<Locale,List<String>> getTableData(List<String> tableData) throws Exception {
		return locale -> tableData;
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
//					String hostname = linuxServer.getHostname().toString();
//					String device = tableData.get(index);
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
