/*
 * Copyright 2008 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.Ostermiller.util.CSVParse;
import com.Ostermiller.util.CSVParser;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.TableResult;
import com.aoindustries.util.ErrorHandler;
import com.aoindustries.util.StringUtility;
import java.io.CharArrayReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The workers for filesystem monitoring.
 *
 * @author  AO Industries, Inc.
 */
class FilesystemsNodeWorker extends TableResultNodeWorker {

    /**
     * One unique worker is made per persistence file (and should match the aoServer exactly)
     */
    private static final Map<String, FilesystemsNodeWorker> workerCache = new HashMap<String,FilesystemsNodeWorker>();
    static FilesystemsNodeWorker getWorker(ErrorHandler errorHandler, File persistenceFile, AOServer aoServer) throws IOException {
        String path = persistenceFile.getCanonicalPath();
        synchronized(workerCache) {
            FilesystemsNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new FilesystemsNodeWorker(errorHandler, persistenceFile, aoServer);
                workerCache.put(path, worker);
            } else {
                if(!worker.aoServer.equals(aoServer)) throw new AssertionError("worker.aoServer!=aoServer: "+worker.aoServer+"!="+aoServer);
            }
            return worker;
        }
    }

    // Will use whichever connector first created this worker, even if other accounts connect later.
    final private AOServer aoServer;

    FilesystemsNodeWorker(ErrorHandler errorHandler, File persistenceFile, AOServer aoServer) {
        super(errorHandler, persistenceFile);
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
            for(int index=0,len=tableData.size();index<len;index+=12) {
                AlertLevelAndMessage alam;
                try {
                    alam = getAlertLevelAndMessage(locale, tableData, index);
                } catch(Exception err) {
                    errorHandler.reportError(err, null);
                    alam = new AlertLevelAndMessage(AlertLevel.CRITICAL, err.toString());
                }
                AlertLevel alertLevel = alam.getAlertLevel();
                if(alertLevel.compareTo(highestAlertLevel)>0) {
                    highestAlertLevel = alertLevel;
                    highestAlertMessage = alam.getAlertMessage();
                }
            }
        }
        return new AlertLevelAndMessage(highestAlertLevel, highestAlertMessage);
    }

    @Override
    protected int getColumns() {
        return 12;
    }

    @Override
    protected List<?> getColumnHeaders(Locale locale) {
        List<String> columnHeaders = new ArrayList<String>(12);
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.columnHeader.mountpoint"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.columnHeader.device"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.columnHeader.bytes"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.columnHeader.used"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.columnHeader.free"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.columnHeader.use"));
        //columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.columnHeader.inodes"));
        //columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.columnHeader.iused"));
        //columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.columnHeader.ifree"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.columnHeader.iuse"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.columnHeader.fstype"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.columnHeader.mountoptions"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.columnHeader.extstate"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.columnHeader.extmaxmount"));
        columnHeaders.add(ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.columnHeader.extchkint"));
        return columnHeaders;
    }

    @Override
    protected List<?> getTableData(Locale locale) throws Exception {
        String report = aoServer.getFilesystemsCsvReport();

        CSVParse csvParser = new CSVParser(new CharArrayReader(report.toCharArray()));
        try {
            // Skip the columns
            String[] line = csvParser.getLine();
            if(line==null) throw new IOException("No lines from report");
            if(line.length!=15) throw new IOException("First line of report doesn't have 15 columns: "+line.length);
            if(
                !"mountpoint".equals(line[0])
                || !"device".equals(line[1])
                || !"bytes".equals(line[2])
                || !"used".equals(line[3])
                || !"free".equals(line[4])
                || !"use".equals(line[5])
                || !"inodes".equals(line[6])
                || !"iused".equals(line[7])
                || !"ifree".equals(line[8])
                || !"iuse".equals(line[9])
                || !"fstype".equals(line[10])
                || !"mountoptions".equals(line[11])
                || !"extstate".equals(line[12])
                || !"extmaxmount".equals(line[13])
                || !"extchkint".equals(line[14])
            ) throw new IOException("First line is not the expected column labels");

            // Read the report, line-by-line
            List<String> tableData = new ArrayList<String>(90); // Most servers don't have more than 6 filesystems
            while((line=csvParser.getLine())!=null) {
                if(line.length!=15) throw new IOException("line.length!=15: "+line.length);
                tableData.add(line[0]); // mountpoint
                tableData.add(line[1]); // device
                tableData.add(StringUtility.getApproximateSize(Long.parseLong(line[2]))); // bytes
                tableData.add(StringUtility.getApproximateSize(Long.parseLong(line[3]))); // used
                tableData.add(StringUtility.getApproximateSize(Long.parseLong(line[4]))); // free
                tableData.add(line[5]); // use
                //tableData.add(line[6]); // inodes
                //tableData.add(line[7]); // iused
                //tableData.add(line[8]); // ifree
                tableData.add(line[9]); // iuse
                tableData.add(line[10]); // fstype
                tableData.add(line[11]); // mountoptions
                tableData.add(line[12]); // extstate
                tableData.add(line[13]); // extmaxmount
                tableData.add(line[14]); // extchkint
            }
            return tableData;
        } finally {
            csvParser.close();
        }
    }

    @Override
    protected List<AlertLevel> getAlertLevels(List<?> tableData) {
        Locale locale = Locale.getDefault();
        List<AlertLevel> alertLevels = new ArrayList<AlertLevel>(tableData.size()/12);
        for(int index=0,len=tableData.size();index<len;index+=12) {
            try {
                AlertLevelAndMessage alam = getAlertLevelAndMessage(locale, tableData, index);
                alertLevels.add(alam.getAlertLevel());
            } catch(Exception err) {
                errorHandler.reportError(err, null);
                alertLevels.add(AlertLevel.CRITICAL);
            }
        }
        return alertLevels;
    }

    /**
     * Determines one line of alert level and message.
     */
    private AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, List<?> tableData, int index) throws Exception {
        AlertLevel highestAlertLevel = AlertLevel.NONE;
        String highestAlertMessage = ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.alertMessage.allOk");

        // Check extstate
        String fstype = tableData.get(index+7).toString();
        {
            if(
                "ext2".equals(fstype)
                || "ext3".equals(fstype)
            ) {
                String extstate = tableData.get(index+9).toString();
                if(
                    (
                        "ext3".equals(fstype)
                        && !"clean".equals(extstate)
                    ) || (
                        "ext2".equals(fstype)
                        && !"not clean".equals(extstate)
                        && !"clean".equals(extstate)
                    )
                ) {
                    AlertLevel newAlertLevel = AlertLevel.CRITICAL;
                    if(newAlertLevel.compareTo(highestAlertLevel)>0) {
                        highestAlertLevel = newAlertLevel;
                        highestAlertMessage = ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.alertMessage.extstate.unexpectedState", extstate);
                    }
                }
            }
        }

        // Check for inode percent
        {
            String iuse = tableData.get(index+6).toString();
            if(iuse.length()!=0) {
                if(!iuse.endsWith("%")) throw new IOException("iuse doesn't end with '%': "+iuse);
                int iuseNum = Integer.parseInt(iuse.substring(0, iuse.length()-1));
                final AlertLevel newAlertLevel;
                if(iuseNum<0 || iuseNum>=95) newAlertLevel = AlertLevel.CRITICAL;
                else if(iuseNum>=90) newAlertLevel = AlertLevel.HIGH;
                else if(iuseNum>=85) newAlertLevel = AlertLevel.MEDIUM;
                else if(iuseNum>=80) newAlertLevel = AlertLevel.LOW;
                else newAlertLevel = AlertLevel.NONE;
                if(newAlertLevel.compareTo(highestAlertLevel)>0) {
                    highestAlertLevel = newAlertLevel;
                    highestAlertMessage = ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.alertMessage.iuse", iuse);
                }
            }
        }

        // Check for disk space percent
        {
            // Ignore on www1.fc.lnxhosting.ca:/var/backup (until they use AO-based backup code)
            String hostname = aoServer.getHostname();
            String mountpoint = tableData.get(index).toString();
            if(
                !hostname.equals("www1.fc.lnxhosting.ca")
                || !mountpoint.equals("/var/backup")
            ) {
                String use = tableData.get(index+5).toString();
                if(!use.endsWith("%")) throw new IOException("use doesn't end with '%': "+use);
                int useNum = Integer.parseInt(use.substring(0, use.length()-1));
                // Backup partitions and www1.nl.pertinence.net:/ will allow a higher percentage
                final boolean allowHigherPercentage =
                    (hostname.equals("www1.nl.pertinence.net") && mountpoint.equals("/"))
                    || mountpoint.startsWith("/var/backup")
                ;
                final AlertLevel newAlertLevel;
                if(useNum<0 || useNum>=(allowHigherPercentage ? 99 : 97)) newAlertLevel = AlertLevel.CRITICAL;
                else if(useNum>=(allowHigherPercentage ? 98 : 94)) newAlertLevel = AlertLevel.HIGH;
                else if(useNum>=(allowHigherPercentage ? 97 : 91)) newAlertLevel = AlertLevel.MEDIUM;
                else if(useNum>=(allowHigherPercentage ? 96 : 88)) newAlertLevel = AlertLevel.LOW;
                else newAlertLevel = AlertLevel.NONE;
                if(newAlertLevel.compareTo(highestAlertLevel)>0) {
                    highestAlertLevel = newAlertLevel;
                    highestAlertMessage = ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.alertMessage.use", use);
                }
            }
        }

        // Make sure extmaxmount is -1
        if(highestAlertLevel.compareTo(AlertLevel.LOW)<0) {
            String extmaxmount = tableData.get(index+10).toString();
            if("ext3".equals(fstype)) {
                if(!"-1".equals(extmaxmount)) {
                    highestAlertLevel = AlertLevel.LOW;
                    highestAlertMessage = ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.alertMessage.extmaxmount.ext3", extmaxmount);
                }
            } else if("ext2".equals(fstype)) {
                if("-1".equals(extmaxmount)) {
                    highestAlertLevel = AlertLevel.LOW;
                    highestAlertMessage = ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.alertMessage.extmaxmount.ext2", extmaxmount);
                }
            }
        }
        
        // Make sure extchkint is 0
        if(highestAlertLevel.compareTo(AlertLevel.LOW)<0) {
            String extchkint = tableData.get(index+11).toString();
            if("ext3".equals(fstype)) {
                if(!"0 (<none>)".equals(extchkint)) {
                    highestAlertLevel = AlertLevel.LOW;
                    highestAlertMessage = ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.alertMessage.extchkint.ext3", extchkint);
                }
            } else if("ext2".equals(fstype)) {
                if("0 (<none>)".equals(extchkint)) {
                    highestAlertLevel = AlertLevel.LOW;
                    highestAlertMessage = ApplicationResourcesAccessor.getMessage(locale, "FilesystemsNodeWorker.alertMessage.extchkint.ext2", extchkint);
                }
            }
        }

        return new AlertLevelAndMessage(highestAlertLevel, highestAlertMessage);
    }
}
