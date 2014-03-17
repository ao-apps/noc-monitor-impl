/*
 * Copyright 2012, 2013, 2014 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.UpsResult;
import com.aoindustries.sql.MilliInterval;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author  AO Industries, Inc.
 */
class UpsNodeWorker extends TableMultiResultNodeWorker<UpsStatus,UpsResult> {

    private static final float LINEV_LOW_TOLERANCE = 0.25f;
    private static final float LINEV_HIGH_TOLERANCE = 0.75f;

    private static final float LOW_LINEFREQ = 58;
    private static final float HIGH_LINEFREQ = 62;

    private static final float OUTPUTV_TOLERANCE = 5;

    private static final float CRITICAL_LOAD_PERCENT = 96;
    private static final float HIGH_LOAD_PERCENT     = 94;
    private static final float MEDIUM_LOAD_PERCENT   = 92;
    private static final float LOW_LOAD_PERCENT      = 90;

    private static final float CRITICAL_BCHARGE = 80;
    private static final float HIGH_BCHARGE     = 85;
    private static final float MEDIUM_BCHARGE   = 90;
    private static final float LOW_BCHARGE      = 95;

    private static final float LOW_BATTV_TOLERANCE = 2;

    private static final MilliInterval CRITICAL_TONBATT = new MilliInterval(4L * 60L * 1000L);
    private static final MilliInterval HIGH_TONBATT     = new MilliInterval(3L * 60L * 1000L);
    private static final MilliInterval MEDIUM_TONBATT   = new MilliInterval(2L * 60L * 1000L);
    private static final MilliInterval LOW_TONBATT      = new MilliInterval(1L * 60L * 1000L);

    private static final MilliInterval CRITICAL_TIMELEFT = new MilliInterval(10L * 60L * 1000L);
    private static final MilliInterval HIGH_TIMELEFT     = new MilliInterval(12L * 60L * 1000L);
    private static final MilliInterval MEDIUM_TIMELEFT   = new MilliInterval(14L * 60L * 1000L);
    private static final MilliInterval LOW_TIMELEFT      = new MilliInterval(16L * 60L * 1000L);

    // http://nam-en.apc.com/app/answers/detail/a_id/8301/~/what-is-the-expected-life-of-my-apc-ups-battery%3F
	// now http://forums.apc.com/message/6778
    private static final float LOW_ITEMP = 20;
    private static final float HIGH_ITEMP = 35; // Was 30 for a long time, but ups907 jumped from 28.5 to 32.4 in mid March 2014.

    /**
     * One unique worker is made per persistence directory (and should match aoServer exactly)
     */
    private static final Map<String, UpsNodeWorker> workerCache = new HashMap<>();
    static UpsNodeWorker getWorker(File persistenceDirectory, AOServer aoServer) throws IOException {
        String path = persistenceDirectory.getCanonicalPath();
        synchronized(workerCache) {
            UpsNodeWorker worker = workerCache.get(path);
            if(worker==null) {
                worker = new UpsNodeWorker(persistenceDirectory, aoServer);
                workerCache.put(path, worker);
            } else {
                if(!worker._aoServer.equals(aoServer)) throw new AssertionError("worker.aoServer!=aoServer: "+worker._aoServer+"!="+aoServer);
            }
            return worker;
        }
    }

    final private AOServer _aoServer;

    private UpsNodeWorker(File persistenceDirectory, AOServer aoServer) throws IOException {
        super(new File(persistenceDirectory, "ups"), new UpsResultSerializer());
        this._aoServer = aoServer;
    }

    /**
     * Keep approximately one week of results.
     */
    @Override
    protected int getHistorySize() {
        return 10000;
    }

    @Override
    protected UpsStatus getSample(Locale locale) throws Exception {
        return new UpsStatus(_aoServer.getUpsStatus());
    }

    @Override
    protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, UpsStatus sample, Iterable<? extends UpsResult> previousResults) throws Exception {
        AlertLevelAndMessage highest = AlertLevelAndMessage.NONE;
        // STATUS
        {
            String status = sample.getStatus();
            if(status==null) {
                highest = highest.escalate(AlertLevel.UNKNOWN, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.status.null"));
            } else if(status.equals("ONLINE") || status.startsWith("ONLINE ")) {
                highest = highest.escalate(AlertLevel.NONE, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.status.online"));
            } else if(status.equals("CHARGING") || status.startsWith("CHARGING ")) {
                highest = highest.escalate(AlertLevel.LOW, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.status.charging"));
            } else if(status.equals("ONBATT") || status.startsWith("ONBATT ")) {
                highest = highest.escalate(AlertLevel.CRITICAL, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.status.onbatt"));
            } else if(status.equals("COMMLOST")) {
                highest = highest.escalate(AlertLevel.CRITICAL, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.status.commlost"));
            } else {
                highest = highest.escalate(AlertLevel.UNKNOWN, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.status.unknown", status));
            }
        }
        // LINEV
        {
            float linev = sample.getLinev();
            float lotrans = sample.getLotrans();
            float hitrans = sample.getHitrans();
            if(
                !Float.isNaN(linev)
                && !Float.isNaN(lotrans)
                && !Float.isNaN(hitrans)
            ) {
                float loAlert = lotrans + LINEV_LOW_TOLERANCE * (hitrans - lotrans);
                float hiAlert = lotrans + LINEV_HIGH_TOLERANCE * (hitrans - lotrans);
                if(linev<loAlert) {
                    highest = highest.escalate(AlertLevel.CRITICAL, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.linev.low", linev, loAlert));
                }
                if(linev>hiAlert) {
                    highest = highest.escalate(AlertLevel.CRITICAL, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.linev.high", linev, hiAlert));
                }
            }
        }
        // LINEFREQ
        {
            float linefreq = sample.getLinefreq();
            if(!Float.isNaN(linefreq)) {
                if(linefreq<LOW_LINEFREQ) {
                    highest = highest.escalate(AlertLevel.CRITICAL, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.linefreq.low", linefreq, LOW_LINEFREQ));
                }
                if(linefreq>HIGH_LINEFREQ) {
                    highest = highest.escalate(AlertLevel.CRITICAL, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.linefreq.high", linefreq, HIGH_LINEFREQ));
                }
            }
        }
        // OUTPUTV
        {
            float outputv = sample.getOutputv();
            float nomoutv = sample.getNomoutv();
            if(
                !Float.isNaN(outputv)
                && !Float.isNaN(nomoutv)
            ) {
                final float loAlert = nomoutv - OUTPUTV_TOLERANCE;
                final float hiAlert = nomoutv + OUTPUTV_TOLERANCE;
                if(nomoutv<loAlert) {
                    highest = highest.escalate(AlertLevel.CRITICAL, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.nomoutv.low", nomoutv, loAlert));
                }
                if(nomoutv>hiAlert) {
                    highest = highest.escalate(AlertLevel.CRITICAL, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.nomoutv.high", nomoutv, hiAlert));
                }
            }
        }
        // LOADPCT
        {
            float loadpct = sample.getLoadpct();
            if(!Float.isNaN(loadpct)) {
                if(loadpct>=CRITICAL_LOAD_PERCENT) {
                    highest = highest.escalate(AlertLevel.CRITICAL, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.loadpct", loadpct, CRITICAL_LOAD_PERCENT));
                } else if(loadpct>=HIGH_LOAD_PERCENT) {
                    highest = highest.escalate(AlertLevel.HIGH,     accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.loadpct", loadpct, HIGH_LOAD_PERCENT));
                } else if(loadpct>=MEDIUM_LOAD_PERCENT) {
                    highest = highest.escalate(AlertLevel.MEDIUM,   accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.loadpct", loadpct, MEDIUM_LOAD_PERCENT));
                } else if(loadpct>=LOW_LOAD_PERCENT) {
                    highest = highest.escalate(AlertLevel.LOW,      accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.loadpct", loadpct, LOW_LOAD_PERCENT));
                }
            }
        }
        // BCHARGE
        {
            float bcharge = sample.getBcharge();
            if(!Float.isNaN(bcharge)) {
                if(bcharge<=CRITICAL_BCHARGE) {
                    highest = highest.escalate(AlertLevel.CRITICAL, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.bcharge", bcharge, CRITICAL_BCHARGE));
                } else if(bcharge<=HIGH_BCHARGE) {
                    highest = highest.escalate(AlertLevel.HIGH,     accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.bcharge", bcharge, HIGH_BCHARGE));
                } else if(bcharge<=MEDIUM_BCHARGE) {
                    highest = highest.escalate(AlertLevel.MEDIUM,   accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.bcharge", bcharge, MEDIUM_BCHARGE));
                } else if(bcharge<=LOW_BCHARGE) {
                    highest = highest.escalate(AlertLevel.LOW,      accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.bcharge", bcharge, LOW_BCHARGE));
                }
            }
        }
        // BATTV
        {
            float battv = sample.getBattv();
            float nombattv = sample.getNombattv();
            if(
                !Float.isNaN(battv)
                && !Float.isNaN(nombattv)
            ) {
                final float loAlert = nombattv - LOW_BATTV_TOLERANCE;
                if(battv<loAlert) {
                    highest = highest.escalate(AlertLevel.HIGH, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.battv.low", battv, loAlert));
                }
            }
        }
        // BADBATTS
        {
            int badbatts = sample.getBadbatts();
            if(badbatts>0) {
                highest = highest.escalate(AlertLevel.HIGH, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.badbatts"));
            }
        }
        // TONBATT
        {
            MilliInterval tonbatt = sample.getTonbatt();
            if(tonbatt!=null) {
                if(tonbatt.compareTo(CRITICAL_TONBATT)>0) {
                    highest = highest.escalate(AlertLevel.CRITICAL, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.tonbatt", tonbatt, CRITICAL_TONBATT));
                } else if(tonbatt.compareTo(HIGH_TONBATT)>0) {
                    highest = highest.escalate(AlertLevel.HIGH,     accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.tonbatt", tonbatt, HIGH_TONBATT));
                } else if(tonbatt.compareTo(MEDIUM_TONBATT)>0) {
                    highest = highest.escalate(AlertLevel.MEDIUM,   accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.tonbatt", tonbatt, MEDIUM_TONBATT));
                } else if(tonbatt.compareTo(LOW_TONBATT)>0) {
                    highest = highest.escalate(AlertLevel.LOW,      accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.tonbatt", tonbatt, LOW_TONBATT));
                }
            }
        }
        // TIMELEFT
        {
            MilliInterval timeleft = sample.getTimeleft();
            if(timeleft!=null) {
                if(timeleft.compareTo(CRITICAL_TIMELEFT)<0) {
                    highest = highest.escalate(AlertLevel.CRITICAL, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.timeleft", timeleft, CRITICAL_TIMELEFT));
                } else if(timeleft.compareTo(HIGH_TIMELEFT)<0) {
                    highest = highest.escalate(AlertLevel.HIGH,     accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.timeleft", timeleft, HIGH_TIMELEFT));
                } else if(timeleft.compareTo(MEDIUM_TIMELEFT)<0) {
                    highest = highest.escalate(AlertLevel.MEDIUM,   accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.timeleft", timeleft, MEDIUM_TIMELEFT));
                } else if(timeleft.compareTo(LOW_TIMELEFT)<0) {
                    highest = highest.escalate(AlertLevel.LOW,      accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.timeleft", timeleft, LOW_TIMELEFT));
                }
            }
        }
        // ITEMP
        {
            float itemp = sample.getItemp();
            if(!Float.isNaN(itemp)) {
                if(itemp<LOW_ITEMP) {
                    highest = highest.escalate(AlertLevel.CRITICAL, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.itemp.low", itemp, LOW_ITEMP));
                }
                if(itemp>HIGH_ITEMP) {
                    highest = highest.escalate(AlertLevel.CRITICAL, accessor.getMessage("UpsNodeWorker.getAlertLevelAndMessage.itemp.high", itemp, HIGH_ITEMP));
                }
            }
        }

        return highest;
    }

    @Override
    protected UpsResult newErrorResult(long time, long latency, AlertLevel alertLevel, String error) {
        return new UpsResult(time, latency, alertLevel, error);
    }

    @Override
    protected UpsResult newSampleResult(long time, long latency, AlertLevel alertLevel, UpsStatus sample) {
        return sample.getResult(time, latency, alertLevel);
    }

    /**
     * Check once a minute.
     */
    @Override
    protected long getSleepDelay(boolean lastSuccessful, AlertLevel alertLevel) {
        return 60000;
    }

    /**
     * Shorter timeout of one minute.
     */
    @Override
    protected long getFutureTimeout() {
        return 1;
    }
}
