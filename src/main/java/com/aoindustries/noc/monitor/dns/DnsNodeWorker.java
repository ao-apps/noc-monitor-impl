/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2009-2013, 2016, 2017, 2018, 2020, 2021  AO Industries, Inc.
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
 * along with noc-monitor-impl.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.noc.monitor.dns;

import com.aoindustries.aoserv.client.dns.RecordType;
import com.aoindustries.aoserv.client.net.IpAddress;
import com.aoindustries.aoserv.client.net.monitoring.IpAddressMonitoring;
import com.aoindustries.net.InetAddress;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;
import com.aoindustries.noc.monitor.RootNodeImpl;
import com.aoindustries.noc.monitor.TableResultNodeWorker;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TableResult;
import com.aoindustries.sql.NanoInterval;
import com.aoindustries.util.function.SerializableFunction;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Name;
import org.xbill.DNS.PTRRecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.ReverseMap;
import org.xbill.DNS.Type;

/**
 * The workers for DNS monitoring.
 *
 * @author  AO Industries, Inc.
 */
class DnsNodeWorker extends TableResultNodeWorker<List<DnsNodeWorker.DnsQueryResult>, Object> {

	static class DnsQueryResult {
		final String query;
		final long latency;
		final String result;
		final String message;
		final AlertLevel alertLevel;

		DnsQueryResult(String query, long latency, String result, String message, AlertLevel alertLevel) {
			this.query = query;
			this.latency = latency;
			this.result = result;
			this.message = message;
			this.alertLevel = alertLevel;
		}
	}

	/**
	 * One unique worker is made per persistence file (and should match the ipAddress exactly)
	 */
	private static final Map<String, DnsNodeWorker> workerCache = new HashMap<>();
	static DnsNodeWorker getWorker(File persistenceFile, IpAddress ipAddress) throws IOException, SQLException {
		String path = persistenceFile.getCanonicalPath();
		synchronized(workerCache) {
			DnsNodeWorker worker = workerCache.get(path);
			if(worker==null) {
				worker = new DnsNodeWorker(persistenceFile, ipAddress);
				workerCache.put(path, worker);
			} else {
				if(!worker.ipAddress.equals(ipAddress)) throw new AssertionError("worker.ipAddress!=ipAddress: "+worker.ipAddress+"!="+ipAddress);
			}
			return worker;
		}
	}

	final private IpAddress ipAddress;

	DnsNodeWorker(File persistenceFile, IpAddress ipAddress) throws IOException, SQLException {
		super(persistenceFile);
		this.ipAddress = ipAddress;
	}

	@Override
	protected int getColumns() {
		return 4;
	}

	@Override
	protected SerializableFunction<Locale, List<String>> getColumnHeaders() {
		return locale -> Arrays.asList(PACKAGE_RESOURCES.getMessage(locale, "DnsNodeWorker.columnHeader.query"),
			PACKAGE_RESOURCES.getMessage(locale, "DnsNodeWorker.columnHeader.latency"),
			PACKAGE_RESOURCES.getMessage(locale, "DnsNodeWorker.columnHeader.result"),
			PACKAGE_RESOURCES.getMessage(locale, "DnsNodeWorker.columnHeader.message")
		);
	}

	@Override
	protected List<DnsQueryResult> getQueryResult() throws Exception {
		IpAddress currentIpAddress = ipAddress.getTable().getConnector().getNet().getIpAddress().get(ipAddress.getPkey());
		IpAddressMonitoring iam = currentIpAddress.getMonitoring();
		if(iam == null) return Collections.emptyList();
		InetAddress ip = currentIpAddress.getExternalInetAddress();
		if(ip==null) ip = currentIpAddress.getInetAddress();
		String expectedHostname = currentIpAddress.getHostname().toString();
		if(!expectedHostname.endsWith(".")) expectedHostname += '.';
		// Priority is higher when assigned, lower when unassigned
		final AlertLevel problemAlertLevel = currentIpAddress.getDevice() != null ? AlertLevel.MEDIUM : AlertLevel.LOW;
		StringBuilder SB = new StringBuilder();
		List<DnsQueryResult> results = new ArrayList<>();
		boolean didHostnameAVerification = false;
		// Reverse DNS
		if(iam.getVerifyDnsPtr()) {
			//String ptrQuery = IpAddress.getReverseDnsQuery(ip);
			Name ptrQuery = ReverseMap.fromAddress(ip.toString());
			long ptrStartNanos = System.nanoTime();
			Lookup ptrLookup = new Lookup(ptrQuery, Type.PTR);
			ptrLookup.run();
			long ptrLatency = System.nanoTime()-ptrStartNanos;
			if(ptrLookup.getResult()!=Lookup.SUCCESSFUL) {
				results.add(new DnsQueryResult(ptrQuery.toString(), ptrLatency, ptrLookup.getErrorString(), "", problemAlertLevel));
			} else {
				Record[] ptrRecords = ptrLookup.getAnswers();
				if(ptrRecords.length==0) {
					results.add(new DnsQueryResult(ptrQuery.toString(), ptrLatency, "", "No " + RecordType.PTR +" records found", problemAlertLevel));
				} else {
					String ptrList;
					boolean expectedHostnameFound = false;
					{
						SB.setLength(0);
						for(Record record : ptrRecords) {
							if(SB.length()>0) SB.append(", ");
							PTRRecord ptrRecord = (PTRRecord)record;
							String hostname = ptrRecord.getTarget().toString();
							SB.append(hostname);
							if(expectedHostname.equals(hostname)) expectedHostnameFound = true;
						}
						ptrList = SB.toString();
					}
					boolean hasPtrResult = false;
					if(ptrRecords.length > 1) {
						results.add(new DnsQueryResult(ptrQuery.toString(), ptrLatency, ptrList, "More than one " + RecordType.PTR +" record found", problemAlertLevel));
						hasPtrResult = true;
					}
					if(!expectedHostnameFound) {
						results.add(new DnsQueryResult(ptrQuery.toString(), ptrLatency, ptrList, "Hostname not in results: "+expectedHostname, problemAlertLevel));
						hasPtrResult = true;
					}
					if(!hasPtrResult) {
						results.add(new DnsQueryResult(ptrQuery.toString(), ptrLatency, ptrList, "", AlertLevel.NONE));
					}
					if(iam.getVerifyDnsA()) {
						// Lookup each A record, making sure one of its IP addresses is the current IP
						for(Record record : ptrRecords) {
							PTRRecord ptrRecord = (PTRRecord)record;
							verifyDnsA(ptrRecord.getTarget(), results, problemAlertLevel, SB, ip);
						}
						if(expectedHostnameFound) didHostnameAVerification = true;
					}
				}
			}
		}
		// Check forward DNS for the hostname, if not already done as part of the above
		if(iam.getVerifyDnsA() && !didHostnameAVerification) {
			verifyDnsA(new Name(expectedHostname), results, problemAlertLevel, SB, ip);
		}
		return results;
	}

	private static void verifyDnsA(Name target, List<DnsQueryResult> results, AlertLevel problemAlertLevel, StringBuilder SB, InetAddress ip) {
		long aStartNanos = System.nanoTime();
		Lookup aLookup = new Lookup(target, Type.A);
		aLookup.run();
		long aLatency = System.nanoTime() - aStartNanos;
		if(aLookup.getResult()!=Lookup.SUCCESSFUL) {
			results.add(new DnsQueryResult(target.toString(), aLatency, aLookup.getErrorString(), "", problemAlertLevel));
		} else {
			Record[] aRecords = aLookup.getAnswers();
			if(aRecords.length==0) {
				results.add(new DnsQueryResult(target.toString(), aLatency, "", "No A records found", problemAlertLevel));
			} else {
				String ipList;
				boolean ipFound = false;
				{
					SB.setLength(0);
					for(Record rec : aRecords) {
						if(SB.length()>0) SB.append(", ");
						ARecord aRecord = (ARecord)rec;
						String aIp = aRecord.getAddress().getHostAddress();
						SB.append(aIp);
						if(ip.toString().equals(aIp)) ipFound = true;
					}
					ipList = SB.toString();
				}
				String aMessage;
				AlertLevel aAlertLevel;
				if(!ipFound) {
					aMessage = "Address not in results: "+ip;
					aAlertLevel = problemAlertLevel;
				} else {
					aMessage = "";
					aAlertLevel = AlertLevel.NONE;
				}
				results.add(new DnsQueryResult(target.toString(), aLatency, ipList, aMessage, aAlertLevel));
			}
		}
	}

	@Override
	protected SerializableFunction<Locale, List<Object>> getTableData(List<DnsQueryResult> results) throws Exception {
		List<Object> tableData = new ArrayList<>(results.size()*4);
		for(DnsQueryResult result : results) {
			tableData.add(result.query);
			tableData.add(new NanoInterval(result.latency));
			tableData.add(result.result);
			tableData.add(result.message);
		}
		return locale -> tableData;
	}

	@Override
	protected List<AlertLevel> getAlertLevels(List<DnsQueryResult> results) {
		List<AlertLevel> alertLevels = new ArrayList<>(results.size());
		for(DnsQueryResult result : results) {
			alertLevels.add(result.alertLevel);
		}
		return alertLevels;
	}

	@Override
	public AlertLevelAndMessage getAlertLevelAndMessage(AlertLevel curAlertLevel, TableResult result) {
		AlertLevel highestAlertLevel = AlertLevel.NONE;
		Function<Locale, String> highestAlertMessage = null;
		if(result.isError()) {
			highestAlertLevel = result.getAlertLevels().get(0);
			highestAlertMessage = locale -> result.getTableData(locale).get(0).toString();
		} else {
			List<?> tableData = result.getTableData(Locale.getDefault());
			for(int index=0,len=tableData.size();index<len;index+=4) {
				AlertLevel alertLevel = result.getAlertLevels().get(index/4);
				if(alertLevel.compareTo(highestAlertLevel)>0) {
					highestAlertLevel = alertLevel;
					Object resultQuery = tableData.get(index);
					Object resultResult = tableData.get(index+2);
					Object resultMessage = tableData.get(index+3);
					highestAlertMessage = locale -> resultQuery + "->" + resultResult + ": " + resultMessage;
				}
			}
		}
		return new AlertLevelAndMessage(highestAlertLevel, highestAlertMessage);
	}

	/**
	 * The sleep delay is 15 minutes when unsuccessful or one hour when successful.
	 */
	@Override
	protected long getSleepDelay(boolean lastSuccessful, AlertLevel alertLevel) {
		return lastSuccessful && alertLevel==AlertLevel.NONE ? 60L * 60L * 1000L : 15L * 60L * 1000L;
	}

	/**
	 * The startup delay is within fifteen minutes.
	 */
	@Override
	protected int getNextStartupDelay() {
		return RootNodeImpl.getNextStartupDelayFifteenMinutes();
	}
}
