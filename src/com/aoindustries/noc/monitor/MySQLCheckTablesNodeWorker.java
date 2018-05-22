/*
 * Copyright 2009-2013, 2016, 2017, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.FailoverMySQLReplication;
import com.aoindustries.aoserv.client.MySQLDatabase;
import com.aoindustries.aoserv.client.MySQLServer;
import com.aoindustries.aoserv.client.validator.MySQLTableName;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.SerializableFunction;
import com.aoindustries.noc.monitor.common.TableResult;
import com.aoindustries.sql.MilliInterval;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The workers for MySQLCheckTablesNode.
 *
 * @author  AO Industries, Inc.
 */
class MySQLCheckTablesNodeWorker extends TableResultNodeWorker<List<Object>,Object> {

	/**
	 * One unique worker is made per persistence file (and should match the mysqlDatabase exactly)
	 */
	private static final Map<String, MySQLCheckTablesNodeWorker> workerCache = new HashMap<>();
	static MySQLCheckTablesNodeWorker getWorker(MySQLDatabaseNode databaseNode, File persistenceFile) throws IOException {
		String path = persistenceFile.getCanonicalPath();
		synchronized(workerCache) {
			MySQLCheckTablesNodeWorker worker = workerCache.get(path);
			if(worker==null) {
				worker = new MySQLCheckTablesNodeWorker(databaseNode, persistenceFile);
				workerCache.put(path, worker);
			} else {
				if(!worker.databaseNode.getMySQLDatabase().equals(databaseNode.getMySQLDatabase())) throw new AssertionError("worker.mysqlDatabase!=mysqlDatabase: "+worker.databaseNode.getMySQLDatabase()+"!="+databaseNode.getMySQLDatabase());
			}
			return worker;
		}
	}

	// Will use whichever connector first created this worker, even if other accounts connect later.
	final private MySQLDatabaseNode databaseNode;

	MySQLCheckTablesNodeWorker(MySQLDatabaseNode databaseNode, File persistenceFile) {
		super(persistenceFile);
		this.databaseNode = databaseNode;
	}

	@Override
	protected int getColumns() {
		return 5;
	}

	@Override
	protected SerializableFunction<Locale,List<String>> getColumnHeaders() {
		return locale -> Arrays.asList(
			accessor.getMessage(locale, "MySQLCheckTablesNodeWorker.columnHeader.name"),
			accessor.getMessage(locale, "MySQLCheckTablesNodeWorker.columnHeader.engine"),
			accessor.getMessage(locale, "MySQLCheckTablesNodeWorker.columnHeader.duration"),
			accessor.getMessage(locale, "MySQLCheckTablesNodeWorker.columnHeader.msgType"),
			accessor.getMessage(locale, "MySQLCheckTablesNodeWorker.columnHeader.msgText")
		);
	}

	@Override
	protected List<Object> getQueryResult() throws Exception {
		MySQLDatabase mysqlDatabase = databaseNode.getMySQLDatabase();
		FailoverMySQLReplication mysqlSlave = databaseNode.getMySQLSlave();

		// Don't check any table on MySQL 5.1+ information_schema database
		if(mysqlDatabase.getName().equals(MySQLDatabase.INFORMATION_SCHEMA)) {
			String version = mysqlDatabase.getMySQLServer().getVersion().getVersion();
			if(
				version.startsWith(MySQLServer.VERSION_5_1_PREFIX)
				|| version.startsWith(MySQLServer.VERSION_5_6_PREFIX)
				|| version.startsWith(MySQLServer.VERSION_5_7_PREFIX)
			) return Collections.emptyList();
		}

		// Don't check any table on MySQL 5.6+ performance_schema database
		if(mysqlDatabase.getName().equals(MySQLDatabase.PERFORMANCE_SCHEMA)) {
			String version = mysqlDatabase.getMySQLServer().getVersion().getVersion();
			if(
				version.startsWith(MySQLServer.VERSION_5_6_PREFIX)
				|| version.startsWith(MySQLServer.VERSION_5_7_PREFIX)
			) return Collections.emptyList();
		}

		// Don't check any table on MySQL 5.7+ sys database
		if(mysqlDatabase.getName().equals(MySQLDatabase.SYS)) {
			String version = mysqlDatabase.getMySQLServer().getVersion().getVersion();
			if(
				version.startsWith(MySQLServer.VERSION_5_7_PREFIX)
			) return Collections.emptyList();
		}

		List<MySQLDatabase.TableStatus> lastTableStatuses = databaseNode.databaseWorker.getLastTableStatuses();
		if(lastTableStatuses.isEmpty()) return Collections.emptyList();
		// Build the set of table names and types
		List<MySQLTableName> tableNames = new ArrayList<>(lastTableStatuses.size());
		Map<MySQLTableName,MySQLDatabase.Engine> tables = new HashMap<>(lastTableStatuses.size()*4/3+1);
		for(MySQLDatabase.TableStatus lastTableStatus : lastTableStatuses) {
			MySQLDatabase.Engine engine = lastTableStatus.getEngine();
			if(
				engine!=MySQLDatabase.Engine.CSV
				&& engine!=MySQLDatabase.Engine.HEAP
				&& engine!=MySQLDatabase.Engine.InnoDB
				&& engine!=MySQLDatabase.Engine.MEMORY
				&& engine!=MySQLDatabase.Engine.PERFORMANCE_SCHEMA
				&& !(engine==null && "VIEW".equals(lastTableStatus.getComment()))
			) {
				MySQLTableName name = lastTableStatus.getName();
				if(
					// Skip the four expected non-checkable tables in information_schema
					!mysqlDatabase.getName().equals(MySQLDatabase.INFORMATION_SCHEMA)
					|| (
						!name.toString().equals("COLUMNS")
						&& !name.toString().equals("ROUTINES")
						&& !name.toString().equals("TRIGGERS")
						&& !name.toString().equals("VIEWS")
					)
				) {
					tableNames.add(name);
					tables.put(name, engine);
				}
			}
		}
		List<MySQLDatabase.CheckTableResult> checkTableResults = mysqlDatabase.checkTables(mysqlSlave, tableNames);
		List<Object> tableData = new ArrayList<>(checkTableResults.size()*5);

		for(MySQLDatabase.CheckTableResult checkTableResult : checkTableResults) {
			MySQLTableName table = checkTableResult.getTable();
			tableData.add(table);
			tableData.add(tables.get(table));
			tableData.add(new MilliInterval(checkTableResult.getDuration()));
			tableData.add(checkTableResult.getMsgType());
			tableData.add(checkTableResult.getMsgText());
		}
		return tableData;
	}

	@Override
	protected SerializableFunction<Locale,List<Object>> getTableData(List<Object> tableData) throws Exception {
		return locale -> tableData;
	}

	/**
	 * If is a slowServer (many tables), only checks once every 12 hours.
	 * Otherwise checks once every five minutes.
	 */
	@Override
	protected long getSleepDelay(boolean lastSuccessful, AlertLevel alertLevel) {
		if(databaseNode.databaseWorker.isSlowServer) return 12L*60*60000; // Only check tables once every 12 hours
		return 5*60000;
	}

	@Override
	protected long getTimeout() {
		return databaseNode.databaseWorker.isSlowServer ? 30 : 5;
	}

	@Override
	protected List<AlertLevel> getAlertLevels(List<Object> tableData) {
		List<AlertLevel> alertLevels = new ArrayList<>(tableData.size()/5);
		for(int index=0,len=tableData.size();index<len;index+=5) {
			String msgText = (String)tableData.get(index+4);
			alertLevels.add(
				msgText!=null && (msgText.equals("OK") || msgText.equals("Table is already up to date"))
				? AlertLevel.NONE
				: AlertLevel.CRITICAL
			);
		}
		return alertLevels;
	}

	/**
	 * Determines the alert message for the provided result.
	 */
	@Override
	protected AlertLevelAndMessage getAlertLevelAndMessage(AlertLevel curAlertLevel, TableResult result) {
		if(result.isError()) {
			return new AlertLevelAndMessage(
				result.getAlertLevels().get(0),
				locale -> result.getTableData(locale).get(0).toString()
			);
		} else {
			List<?> tableData = result.getTableData(Locale.getDefault());
			for(int index=0,len=tableData.size();index<len;index+=5) {
				String msgText = (String)tableData.get(index+4);
				if(msgText==null || (!msgText.equals("OK") && !msgText.equals("Table is already up to date"))) {
					Object name = tableData.get(index);
					return new AlertLevelAndMessage(
						AlertLevel.CRITICAL,
						locale -> name + " - " + msgText
					);
				}
			}
		}
		return AlertLevelAndMessage.NONE;
	}
}
