/*
 * Copyright 2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.TimeSpan;
import com.aoindustries.noc.common.UpsResult;
import com.aoindustries.util.StringUtility;
import java.text.ParseException;

/**
 * @author  AO Industries, Inc.
 */
class UpsStatus {

    private static float parseVolts(String value) {
        if(value==null) return Float.NaN;
        if(value.endsWith(" Volts")) value = value.substring(0, value.length()-6);
        value = value.trim();
        return value.length()==0 ? Float.NaN : Float.parseFloat(value);
    }

    private static float parseFrequency(String value) {
        if(value==null) return Float.NaN;
        if(value.endsWith(" Hz")) value = value.substring(0, value.length()-3);
        value = value.trim();
        return value.length()==0 ? Float.NaN : Float.parseFloat(value);
    }

    private static float parsePercent(String value) {
        if(value==null) return Float.NaN;
        if(value.endsWith(" Percent Load Capacity")) value = value.substring(0, value.length()-22);
        else if(value.endsWith(" Percent")) value = value.substring(0, value.length()-8);
        value = value.trim();
        return value.length()==0 ? Float.NaN : Float.parseFloat(value);
    }

    private static int parseInt(String value) {
        if(value==null) return -1;
        value = value.trim();
        return value.length()==0 ? -1 : Integer.parseInt(value);
    }

    private static TimeSpan parseTimeSpan(String value) {
        if(value==null) return null;
        if(
            value.endsWith(" Minutes")
            || value.endsWith(" minutes")
        ) {
            value = value.substring(0, value.length()-8).trim();
            return new TimeSpan(
                (long)(
                    Float.parseFloat(value)
                    * 60L
                    * 1000L
                )
            );
        }
        if(
            value.endsWith(" Seconds")
            || value.endsWith(" seconds")
        ) {
            value = value.substring(0, value.length()-8).trim();
            return new TimeSpan(
                (long)(
                    Float.parseFloat(value)
                    * 1000L
                )
            );
        }
        throw new IllegalArgumentException(value);
    }

    private static float parseTemperature(String value) {
        if(value==null) return Float.NaN;
        if(value.endsWith(" C Internal")) value = value.substring(0, value.length()-11);
        value = value.trim();
        return value.length()==0 ? Float.NaN : Float.parseFloat(value);
    }

    private final String upsname;
    // Overall status
    private final String status;
    // Line
    private final float linev;
    private final float lotrans;
    private final float hitrans;
    private final float linefreq;
    // Output
    private final float outputv;
    private final float nomoutv;
    // Load
    private final float loadpct;
    // Batteries
    private final float bcharge;
    private final float battv;
    private final float nombattv;
    private final int extbatts;
    private final int badbatts;
    // Runtime
    private final TimeSpan tonbatt;
    private final TimeSpan timeleft;
    private final float itemp;

    UpsStatus(String upsStatus) throws ParseException {
        // Default values
        String upsname = null;
        String status = null;
        float linev = Float.NaN;
        float lotrans = Float.NaN;
        float hitrans = Float.NaN;
        float linefreq = Float.NaN;
        float outputv = Float.NaN;
        float nomoutv = Float.NaN;
        float loadpct = Float.NaN;
        float bcharge = Float.NaN;
        float battv = Float.NaN;
        float nombattv = Float.NaN;
        int extbatts = -1;
        int badbatts = -1;
        TimeSpan tonbatt = null;
        TimeSpan timeleft = null;
        float itemp = Float.NaN;

        // Parse the status
        for(String line : StringUtility.splitLines(upsStatus)) {
            int colonPos = line.indexOf(':');
            if(colonPos==-1) throw new ParseException(accessor.getMessage("UpsStatus.parse.noColon", line), 0);
            String name = line.substring(0, colonPos).trim();
            String value = line.substring(colonPos+1).trim();
                 if("UPSNAME" .equals(name)) upsname  = value;
            else if("STATUS"  .equals(name)) status   = value;
            else if("LINEV"   .equals(name)) linev    = parseVolts(value);
            else if("LOTRANS" .equals(name)) lotrans  = parseVolts(value);
            else if("HITRANS" .equals(name)) hitrans  = parseVolts(value);
            else if("LINEFREQ".equals(name)) linefreq = parseFrequency(value);
            else if("OUTPUTV" .equals(name)) outputv  = parseVolts(value);
            else if("NOMOUTV" .equals(name)) nomoutv  = parseVolts(value);
            else if("LOADPCT" .equals(name)) loadpct  = parsePercent(value);
            else if("BCHARGE" .equals(name)) bcharge  = parsePercent(value);
            else if("BATTV"   .equals(name)) battv    = parseVolts(value);
            else if("NOMBATTV".equals(name)) nombattv = parseVolts(value);
            else if("EXTBATTS".equals(name)) extbatts = parseInt(value);
            else if("BADBATTS".equals(name)) badbatts = parseInt(value);
            else if("TONBATT" .equals(name)) tonbatt  = parseTimeSpan(value);
            else if("TIMELEFT".equals(name)) timeleft = parseTimeSpan(value);
            else if("ITEMP"   .equals(name)) itemp    = parseTemperature(value);
        }

        this.upsname = upsname;
        this.status = status;
        this.linev = linev;
        this.lotrans = lotrans;
        this.hitrans = hitrans;
        this.linefreq = linefreq;
        this.outputv = outputv;
        this.nomoutv = nomoutv;
        this.loadpct = loadpct;
        this.bcharge = bcharge;
        this.battv = battv;
        this.nombattv = nombattv;
        this.extbatts = extbatts;
        this.badbatts = badbatts;
        this.tonbatt = tonbatt;
        this.timeleft = timeleft;
        this.itemp = itemp;
    }

    /**
     * @see  UpsResult#getUpsname()
     */
    String getUpsname() {
        return upsname;
    }

    /**
     * @see  UpsResult#getStatus()
     */
    String getStatus() {
        return status;
    }

    /**
     * @see  UpsResult#getLinev()
     */
    float getLinev() {
        return linev;
    }

    /**
     * @see  UpsResult#getLotrans()
     */
    float getLotrans() {
        return lotrans;
    }

    /**
     * @see  UpsResult#getHitrans()
     */
    float getHitrans() {
        return hitrans;
    }

    /**
     * @see  UpsResult#getLinefreq()
     */
    float getLinefreq() {
        return linefreq;
    }

    /**
     * @see  UpsResult#getOutputv()
     */
    float getOutputv() {
        return outputv;
    }

    /**
     * @see  UpsResult#getNomoutv()
     */
    float getNomoutv() {
        return nomoutv;
    }

    /**
     * @see  UpsResult#getLoadpct()
     */
    float getLoadpct() {
        return loadpct;
    }

    /**
     * @see  UpsResult#getBcharge()
     */
    float getBcharge() {
        return bcharge;
    }

    /**
     * @see  UpsResult#getBattv()
     */
    float getBattv() {
        return battv;
    }

    /**
     * @see  UpsResult#getNombattv()
     */
    float getNombattv() {
        return nombattv;
    }

    /**
     * @see  UpsResult#getExtbatts()
     */
    int getExtbatts() {
        return extbatts;
    }

    /**
     * @see  UpsResult#getBadbatts()
     */
    int getBadbatts() {
        return badbatts;
    }

    /**
     * @see  UpsResult#getTonbatt()
     */
    TimeSpan getTonbatt() {
        return tonbatt;
    }

    /**
     * @see  UpsResult#getTimeleft()
     */
    TimeSpan getTimeleft() {
        return timeleft;
    }

    /**
     * @see  UpsResult#getItemp()
     */
    float getItemp() {
        return itemp;
    }

    UpsResult getResult(long time, long latency, AlertLevel alertLevel) {
        return new UpsResult(
            time,
            latency,
            alertLevel,
            upsname,
            status,
            linev,
            lotrans,
            hitrans,
            linefreq,
            outputv,
            nomoutv,
            loadpct,
            bcharge,
            battv,
            nombattv,
            extbatts,
            badbatts,
            tonbatt,
            timeleft,
            itemp
        );
    }
}
