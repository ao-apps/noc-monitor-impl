/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.IPAddress;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.NanoTimeSpan;
import com.aoindustries.noc.common.TableResult;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Name;
import org.xbill.DNS.PTRRecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.ReverseMap;
import org.xbill.DNS.Type;

/**
 * The workers for reverse DNS monitoring.
 *
 * @author  AO Industries, Inc.
 */
class ReverseDnsNodeWorker extends TableResultNodeWorker<List<ReverseDnsNodeWorker.ReverseDnsQueryResult>,Object> {

    private static final Logger logger = Logger.getLogger(ReverseDnsNodeWorker.class.getName());

    static class ReverseDnsQueryResult {
        final String query;
        final long latency;
        final String result;
        final String message;
        final AlertLevel alertLevel;

        public ReverseDnsQueryResult(String query, long latency, String result, String message, AlertLevel alertLevel) {
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
    private static final Map<String, ReverseDnsNodeWorker> workerCache = new HashMap<String,ReverseDnsNodeWorker>();
    static ReverseDnsNodeWorker getWorker(File persistenceFile, IPAddress ipAddress) throws IOException, SQLException {
        String path = persistenceFile.getCanonicalPath();
        synchronized(workerCache) {
            ReverseDnsNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new ReverseDnsNodeWorker(persistenceFile, ipAddress);
                workerCache.put(path, worker);
            } else {
                if(!worker.ipAddress.equals(ipAddress)) throw new AssertionError("worker.ipAddress!=ipAddress: "+worker.ipAddress+"!="+ipAddress);
            }
            return worker;
        }
    }

    final private IPAddress ipAddress;

    ReverseDnsNodeWorker(File persistenceFile, IPAddress ipAddress) throws IOException, SQLException {
        super(persistenceFile);
        this.ipAddress = ipAddress;
    }

    @Override
    protected int getColumns() {
        return 4;
    }

    @Override
    protected List<String> getColumnHeaders(Locale locale) {
        List<String> columnHeaders = new ArrayList<String>(4);
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "ReverseDnsNodeWorker.columnHeader.query"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "ReverseDnsNodeWorker.columnHeader.latency"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "ReverseDnsNodeWorker.columnHeader.result"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "ReverseDnsNodeWorker.columnHeader.message"));
        return columnHeaders;
    }

    @Override
    protected List<ReverseDnsQueryResult> getQueryResult(Locale locale) throws Exception {
        IPAddress currentIPAddress = ipAddress.getTable().get(ipAddress.getKey());
        String ip = currentIPAddress.getExternalIpAddress();
        if(ip==null) ip = currentIPAddress.getIPAddress();
        ArrayList<ReverseDnsQueryResult> results = new ArrayList<ReverseDnsQueryResult>(2);
        // Reverse DNS
        //String ptrQuery = IPAddress.getReverseDnsQuery(ip);
        Name ptrQuery = ReverseMap.fromAddress(ip);
        long ptrStartNanos = System.nanoTime();
        Lookup ptrLookup = new Lookup(ptrQuery, Type.PTR);
        ptrLookup.run();
        long ptrLatency = System.nanoTime()-ptrStartNanos;
        if(ptrLookup.getResult()!=Lookup.SUCCESSFUL) {
            results.add(new ReverseDnsQueryResult(ptrQuery.toString(), ptrLatency, ptrLookup.getErrorString(), "", AlertLevel.LOW));
        } else {
            Record[] ptrRecords = ptrLookup.getAnswers();
            if(ptrRecords.length==0) {
                results.add(new ReverseDnsQueryResult(ptrQuery.toString(), ptrLatency, "", "No PTR records found", AlertLevel.LOW));
            } else {
                String expectedHostname = currentIPAddress.getHostname();
                if(!expectedHostname.endsWith(".")) expectedHostname = expectedHostname+'.';
                StringBuilder SB = new StringBuilder();
                boolean expectedHostnameFound = false;
                for(Record record : ptrRecords) {
                    if(SB.length()>0) SB.append(", ");
                    PTRRecord ptrRecord = (PTRRecord)record;
                    String hostname = ptrRecord.getTarget().toString();
                    SB.append(hostname);
                    if(expectedHostname.equals(hostname)) expectedHostnameFound = true;
                }
                String ptrMessage;
                AlertLevel ptrAlertLevel;
                if(!expectedHostnameFound) {
                    ptrMessage = "Hostname not in results: "+expectedHostname;
                    ptrAlertLevel = AlertLevel.LOW;
                } else {
                    ptrMessage = "";
                    ptrAlertLevel = AlertLevel.NONE;
                }
                results.add(new ReverseDnsQueryResult(ptrQuery.toString(), ptrLatency, SB.toString(), ptrMessage, ptrAlertLevel));
                // Lookup each A record, making sure one of its IP addresses is the current IP
                results.ensureCapacity(1+ptrRecords.length);
                for(Record record : ptrRecords) {
                    PTRRecord ptrRecord = (PTRRecord)record;
                    Name target = ptrRecord.getTarget();
                    long aStartNanos = System.nanoTime();
                    Lookup aLookup = new Lookup(target, Type.A);
                    aLookup.run();
                    long aLatency = System.nanoTime() - aStartNanos;
                    if(aLookup.getResult()!=Lookup.SUCCESSFUL) {
                        results.add(new ReverseDnsQueryResult(target.toString(), aLatency, aLookup.getErrorString(), "", AlertLevel.LOW));
                    } else {
                        Record[] aRecords = aLookup.getAnswers();
                        if(aRecords.length==0) {
                            results.add(new ReverseDnsQueryResult(target.toString(), aLatency, "", "No A records found", AlertLevel.LOW));
                        } else {
                            SB.setLength(0);
                            boolean ipFound = false;
                            for(Record rec : aRecords) {
                                if(SB.length()>0) SB.append(", ");
                                ARecord aRecord = (ARecord)rec;
                                String aIp = aRecord.getAddress().getHostAddress();
                                SB.append(aIp);
                                if(ip.equals(aIp)) ipFound = true;
                            }
                            String aMessage;
                            AlertLevel aAlertLevel;
                            if(!ipFound) {
                                aMessage = "Address not in results: "+ip;
                                aAlertLevel = AlertLevel.LOW;
                            } else {
                                aMessage = "";
                                aAlertLevel = AlertLevel.NONE;
                            }
                            results.add(new ReverseDnsQueryResult(target.toString(), aLatency, SB.toString(), aMessage, aAlertLevel));
                        }
                    }
                }
            }
        }
        return results;
    }

    @Override
    protected List<Object> getTableData(List<ReverseDnsQueryResult> results, Locale locale) throws Exception {
        List<Object> tableData = new ArrayList<Object>(results.size()*4);
        for(ReverseDnsQueryResult result : results) {
            tableData.add(result.query);
            tableData.add(new NanoTimeSpan(result.latency));
            tableData.add(result.result);
            tableData.add(result.message);
        }
        return tableData;
    }

    @Override
    protected List<AlertLevel> getAlertLevels(List<ReverseDnsQueryResult> results) {
        List<AlertLevel> alertLevels = new ArrayList<AlertLevel>(results.size());
        for(ReverseDnsQueryResult result : results) alertLevels.add(result.alertLevel);
        return alertLevels;
    }

    @Override
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, TableResult result) {
        AlertLevel highestAlertLevel = AlertLevel.NONE;
        String highestAlertMessage = "";
        List<?> tableData = result.getTableData();
        if(result.isError()) {
            highestAlertLevel = result.getAlertLevels().get(0);
            highestAlertMessage = tableData.get(0).toString();
        } else {
            for(int index=0,len=tableData.size();index<len;index+=4) {
                AlertLevel alertLevel = result.getAlertLevels().get(index/4);
                if(alertLevel.compareTo(highestAlertLevel)>0) {
                    highestAlertLevel = alertLevel;
                    highestAlertMessage = tableData.get(index)+"->"+tableData.get(index+2)+": "+tableData.get(index+3);
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
