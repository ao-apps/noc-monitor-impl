/*
 * Copyright 2009-2012, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.backup.FailoverMySQLReplication;
import com.aoindustries.aoserv.client.mysql.MySQLServer;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.MySQLReplicationResult;
import com.aoindustries.sql.LocalizedSQLException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author  AO Industries, Inc.
 */
class MySQLSlaveStatusNodeWorker extends TableMultiResultNodeWorker<List<String>,MySQLReplicationResult> {

	/**
	 * One unique worker is made per persistence directory (and should match mysqlReplication exactly)
	 */
	private static final Map<String, MySQLSlaveStatusNodeWorker> workerCache = new HashMap<>();
	static MySQLSlaveStatusNodeWorker getWorker(File persistenceDirectory, FailoverMySQLReplication mysqlReplication) throws IOException {
		File persistenceFile = new File(persistenceDirectory, "slave_status");
		String path = persistenceFile.getCanonicalPath();
		synchronized(workerCache) {
			MySQLSlaveStatusNodeWorker worker = workerCache.get(path);
			if(worker==null) {
				worker = new MySQLSlaveStatusNodeWorker(persistenceFile, mysqlReplication);
				workerCache.put(path, worker);
			} else {
				if(!worker._mysqlReplication.equals(mysqlReplication)) throw new AssertionError("worker.mysqlReplication!=mysqlReplication: "+worker._mysqlReplication+"!="+mysqlReplication);
			}
			return worker;
		}
	}

	final private FailoverMySQLReplication _mysqlReplication;
	private FailoverMySQLReplication currentFailoverMySQLReplication;

	private MySQLSlaveStatusNodeWorker(File persistenceFile, FailoverMySQLReplication mysqlReplication) throws IOException {
		super(persistenceFile, new MySQLReplicationResultSerializer());
		this._mysqlReplication = currentFailoverMySQLReplication = mysqlReplication;
	}

	@Override
	protected int getHistorySize() {
		return 2000;
	}

	@Override
	protected List<String> getSample() throws Exception {
		// Get the latest values
		currentFailoverMySQLReplication = _mysqlReplication.getTable().getConnector().getFailoverMySQLReplications().get(_mysqlReplication.getPkey());
		FailoverMySQLReplication.SlaveStatus slaveStatus = currentFailoverMySQLReplication.getSlaveStatus();
		if(slaveStatus==null) throw new LocalizedSQLException(accessor, "MySQLSlaveStatusNodeWorker.slaveNotRunning");
		MySQLServer.MasterStatus masterStatus = _mysqlReplication.getMySQLServer().getMasterStatus();
		if(masterStatus==null) throw new LocalizedSQLException(accessor, "MySQLSlaveStatusNodeWorker.masterNotRunning");
		// Display the alert thresholds
		int secondsBehindLow = currentFailoverMySQLReplication.getMonitoringSecondsBehindLow();
		int secondsBehindMedium = currentFailoverMySQLReplication.getMonitoringSecondsBehindMedium();
		int secondsBehindHigh = currentFailoverMySQLReplication.getMonitoringSecondsBehindHigh();
		int secondsBehindCritical = currentFailoverMySQLReplication.getMonitoringSecondsBehindCritical();
		String alertThresholds =
			(secondsBehindLow==-1 ? "-" : Integer.toString(secondsBehindLow))
			+ " / "
			+ (secondsBehindMedium==-1 ? "-" : Integer.toString(secondsBehindMedium))
			+ " / "
			+ (secondsBehindHigh==-1 ? "-" : Integer.toString(secondsBehindHigh))
			+ " / "
			+ (secondsBehindCritical==-1 ? "-" : Integer.toString(secondsBehindCritical))
		;

		return Arrays.asList(
			slaveStatus.getSecondsBehindMaster(),
			masterStatus.getFile(),
			masterStatus.getPosition(),
			slaveStatus.getSlaveIOState(),
			slaveStatus.getMasterLogFile(),
			slaveStatus.getReadMasterLogPos(),
			slaveStatus.getSlaveIORunning(),
			slaveStatus.getSlaveSQLRunning(),
			slaveStatus.getLastErrno(),
			slaveStatus.getLastError(),
			alertThresholds
		);
	}

	@Override
	protected AlertLevelAndMessage getAlertLevelAndMessage(List<String> sample, Iterable<? extends MySQLReplicationResult> previousResults) throws Exception {
		String secondsBehindMaster = sample.get(0);
		if(secondsBehindMaster==null) {
			// Use the highest alert level that may be returned for this replication
			AlertLevel alertLevel;
			if(currentFailoverMySQLReplication.getMonitoringSecondsBehindCritical()!=-1) alertLevel = AlertLevel.CRITICAL;
			else if(currentFailoverMySQLReplication.getMonitoringSecondsBehindHigh()!=-1) alertLevel = AlertLevel.HIGH;
			else if(currentFailoverMySQLReplication.getMonitoringSecondsBehindMedium()!=-1) alertLevel = AlertLevel.MEDIUM;
			else if(currentFailoverMySQLReplication.getMonitoringSecondsBehindLow()!=-1) alertLevel = AlertLevel.LOW;
			else alertLevel = AlertLevel.NONE;

			return new AlertLevelAndMessage(
				alertLevel,
				locale -> accessor.getMessage(
					locale,
					"MySQLSlaveStatusNodeWorker.alertMessage.secondsBehindMaster.null"
				)
			);
		}
		try {
			int secondsBehind = Integer.parseInt(secondsBehindMaster);
			int secondsBehindCritical = currentFailoverMySQLReplication.getMonitoringSecondsBehindCritical();
			if(secondsBehindCritical!=-1 && secondsBehind>=secondsBehindCritical) {
				return new AlertLevelAndMessage(
					AlertLevel.CRITICAL,
					locale -> accessor.getMessage(
						locale,
						"MySQLSlaveStatusNodeWorker.alertMessage.critical",
						secondsBehindCritical,
						secondsBehind
					)
				);
			}
			int secondsBehindHigh = currentFailoverMySQLReplication.getMonitoringSecondsBehindHigh();
			if(secondsBehindHigh!=-1 && secondsBehind>=secondsBehindHigh) {
				return new AlertLevelAndMessage(
					AlertLevel.HIGH,
					locale -> accessor.getMessage(
						locale,
						"MySQLSlaveStatusNodeWorker.alertMessage.high",
						secondsBehindHigh,
						secondsBehind
					)
				);
			}
			int secondsBehindMedium = currentFailoverMySQLReplication.getMonitoringSecondsBehindMedium();
			if(secondsBehindMedium!=-1 && secondsBehind>=secondsBehindMedium) {
				return new AlertLevelAndMessage(
					AlertLevel.MEDIUM,
					locale -> accessor.getMessage(
						locale,
						"MySQLSlaveStatusNodeWorker.alertMessage.medium",
						secondsBehindMedium,
						secondsBehind
					)
				);
			}
			int secondsBehindLow = currentFailoverMySQLReplication.getMonitoringSecondsBehindLow();
			if(secondsBehindLow!=-1 && secondsBehind>=secondsBehindLow) {
				return new AlertLevelAndMessage(
					AlertLevel.LOW,
					locale -> accessor.getMessage(
						locale,
						"MySQLSlaveStatusNodeWorker.alertMessage.low",
						secondsBehindLow,
						secondsBehind
					)
				);
			}
			if(secondsBehindLow==-1) {
				return new AlertLevelAndMessage(
					AlertLevel.NONE,
					locale -> accessor.getMessage(
						locale,
						"MySQLSlaveStatusNodeWorker.alertMessage.notAny",
						secondsBehind
					)
				);
			} else {
				return new AlertLevelAndMessage(
					AlertLevel.NONE,
					locale -> accessor.getMessage(
						locale,
						"MySQLSlaveStatusNodeWorker.alertMessage.none",
						secondsBehindLow,
						secondsBehind
					)
				);
			}
		} catch(NumberFormatException err) {
			return new AlertLevelAndMessage(
				AlertLevel.CRITICAL,
				locale -> accessor.getMessage(
					locale,
					"MySQLSlaveStatusNodeWorker.alertMessage.secondsBehindMaster.invalid",
					secondsBehindMaster
				)
			);
		}
	}

	@Override
	protected MySQLReplicationResult newErrorResult(long time, long latency, AlertLevel alertLevel, String error) {
		return new MySQLReplicationResult(time, latency, alertLevel, error);
	}

	@Override
	protected MySQLReplicationResult newSampleResult(long time, long latency, AlertLevel alertLevel, List<String> sample) {
		return new MySQLReplicationResult(
			time,
			latency,
			alertLevel,
			sample.get(0),
			sample.get(1),
			sample.get(2),
			sample.get(3),
			sample.get(4),
			sample.get(5),
			sample.get(6),
			sample.get(7),
			sample.get(8),
			sample.get(9),
			sample.get(10)
		);
	}
}
