/*
 * Copyright 2008 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.SingleResult;
import com.aoindustries.util.ErrorHandler;
import com.aoindustries.util.StringUtility;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The workers for 3ware RAID.
 *
 * @author  AO Industries, Inc.
 */
class ThreeWareRaidNodeWorker extends SingleResultNodeWorker {

    /**
     * One unique worker is made per persistence file (and should match the aoServer exactly)
     */
    private static final Map<String, ThreeWareRaidNodeWorker> workerCache = new HashMap<String,ThreeWareRaidNodeWorker>();
    static ThreeWareRaidNodeWorker getWorker(ErrorHandler errorHandler, File persistenceFile, AOServer aoServer) throws IOException {
        String path = persistenceFile.getCanonicalPath();
        synchronized(workerCache) {
            ThreeWareRaidNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new ThreeWareRaidNodeWorker(errorHandler, persistenceFile, aoServer);
                workerCache.put(path, worker);
            } else {
                if(!worker.aoServer.equals(aoServer)) throw new AssertionError("worker.aoServer!=aoServer: "+worker.aoServer+"!="+aoServer);
            }
            return worker;
        }
    }

    // Will use whichever connector first created this worker, even if other accounts connect later.
    final private AOServer aoServer;

    ThreeWareRaidNodeWorker(ErrorHandler errorHandler, File persistenceFile, AOServer aoServer) {
        super(errorHandler, persistenceFile);
        this.aoServer = aoServer;
    }

    @Override
    protected String getReport() {
        return aoServer.get3wareRaidReport();
    }

    /**
     * Determines the alert message for the provided result.
     */
    @Override
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, SingleResult result) {
        if(result.getError()!=null) {
            return new AlertLevelAndMessage(
                AlertLevel.CRITICAL,
                ApplicationResourcesAccessor.getMessage(
                    locale,
                    "ThreeWareRaidNode.alertMessage.error",
                    result.getError()
                )
            );
        }
        String report = result.getReport();
        AlertLevel highestAlertLevel = AlertLevel.NONE;
        String highestAlertMessage = "";
        if(!"\nNo controller found.\nMake sure appropriate AMCC/3ware device driver(s) are loaded.\n\n".equals(report)) {
            List<String> lines = StringUtility.splitLines(report);
            // Should have at least four lines
            if(lines.size()<4) {
                return new AlertLevelAndMessage(
                    AlertLevel.CRITICAL,
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "ThreeWareRaidNode.alertMessage.fourLinesOrMore",
                        lines.size()
                    )
                );
            }
            if(lines.get(0).length()>0) {
                return new AlertLevelAndMessage(
                    AlertLevel.CRITICAL,
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "ThreeWareRaidNode.alertMessage.firstLineShouldBeBlank",
                        lines.get(0)
                    )
                );
            }
            if(
                   !"Ctl   Model        Ports   Drives   Units   NotOpt   RRate   VRate   BBU".equals(lines.get(1))
                && !"Ctl   Model        (V)Ports  Drives   Units   NotOpt  RRate   VRate  BBU".equals(lines.get(1))
            ) {
                return new AlertLevelAndMessage(
                    AlertLevel.CRITICAL,
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "ThreeWareRaidNode.alertMessage.secondLineNotColumns",
                        lines.get(1)
                    )
                );
            }
            if(!"------------------------------------------------------------------------".equals(lines.get(2))) {
                return new AlertLevelAndMessage(
                    AlertLevel.CRITICAL,
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "ThreeWareRaidNode.alertMessage.thirdLineSeparator",
                        lines.get(2)
                    )
                );
            }
            for(int c=3; c<lines.size(); c++) {
                String line = lines.get(c);
                if(line.length()>0) {
                    List<String> values = StringUtility.splitStringCommaSpace(line);
                    if(values.size()!=9) {
                        return new AlertLevelAndMessage(
                            AlertLevel.CRITICAL,
                            ApplicationResourcesAccessor.getMessage(
                                locale,
                                "ThreeWareRaidNode.alertMessage.notNineValues",
                                values.size(),
                                line
                            )
                        );
                    }
                    String notOptString = values.get(5);
                    try {
                        int notOpt = Integer.parseInt(notOptString);
                        if(notOpt>0) {
                            if(AlertLevel.HIGH.compareTo(highestAlertLevel)>0) {
                                highestAlertLevel = AlertLevel.HIGH;
                                highestAlertMessage = ApplicationResourcesAccessor.getMessage(
                                    locale,
                                    notOpt==1 ? "ThreeWareRaidNode.alertMessage.notOpt.singular" : "ThreeWareRaidNode.alertMessage.notOpt.plural",
                                    values.get(0),
                                    notOpt
                                );
                            }
                        }
                    } catch(NumberFormatException err) {
                        return new AlertLevelAndMessage(
                            AlertLevel.CRITICAL,
                            ApplicationResourcesAccessor.getMessage(
                                locale,
                                "ThreeWareRaidNode.alertMessage.badNotOpt",
                                notOptString
                            )
                        );
                    }
                    String bbu = values.get(8);
                    if(
                        !"OK".equals(bbu)   // Not OK BBU
                        && !"-".equals(bbu) // No BBU
                    ) {
                        if(AlertLevel.MEDIUM.compareTo(highestAlertLevel)>0) {
                            highestAlertLevel = AlertLevel.MEDIUM;
                            highestAlertMessage = ApplicationResourcesAccessor.getMessage(
                                locale,
                                "ThreeWareRaidNode.alertMessage.bbuNotOk",
                                values.get(0),
                                bbu
                            );
                        }
                    }
                }
            }
        }
        return new AlertLevelAndMessage(highestAlertLevel, highestAlertMessage);
    }
}
