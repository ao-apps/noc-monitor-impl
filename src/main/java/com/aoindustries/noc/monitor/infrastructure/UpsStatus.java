/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2012, 2013, 2016, 2018, 2020, 2021, 2022  AO Industries, Inc.
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
 * along with noc-monitor-impl.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoindustries.noc.monitor.infrastructure;

import com.aoapps.lang.Strings;
import com.aoapps.lang.i18n.Resources;
import com.aoapps.lang.text.LocalizedParseException;
import com.aoapps.sql.MilliInterval;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.UpsResult;
import java.text.ParseException;
import java.util.ResourceBundle;

/**
 * @author  AO Industries, Inc.
 */
class UpsStatus {

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, UpsStatus.class);

  private static float parseVolts(String value) {
    if (value == null) {
      return Float.NaN;
    }
    if (value.endsWith(" Volts")) {
      value = value.substring(0, value.length() - 6);
    }
    value = value.trim();
    return value.length() == 0 ? Float.NaN : Float.parseFloat(value);
  }

  private static float parseFrequency(String value) {
    if (value == null) {
      return Float.NaN;
    }
    if (value.endsWith(" Hz")) {
      value = value.substring(0, value.length() - 3);
    }
    value = value.trim();
    return value.length() == 0 ? Float.NaN : Float.parseFloat(value);
  }

  private static float parsePercent(String value) {
    if (value == null) {
      return Float.NaN;
    }
    if (value.endsWith(" Percent Load Capacity")) {
      value = value.substring(0, value.length() - 22);
    } else if (value.endsWith(" Percent")) {
      value = value.substring(0, value.length() - 8);
    }
    value = value.trim();
    return value.length() == 0 ? Float.NaN : Float.parseFloat(value);
  }

  private static int parseInt(String value) {
    if (value == null) {
      return -1;
    }
    value = value.trim();
    return value.length() == 0 ? -1 : Integer.parseInt(value);
  }

  private static MilliInterval parseTimeSpan(String value) {
    if (value == null) {
      return null;
    }
    if (
        value.endsWith(" Minutes")
            || value.endsWith(" minutes")
    ) {
      value = value.substring(0, value.length() - 8).trim();
      return new MilliInterval(
          (long) (
              Float.parseFloat(value)
                  * 60L
                  * 1000L
          )
      );
    }
    if (
        value.endsWith(" Seconds")
            || value.endsWith(" seconds")
    ) {
      value = value.substring(0, value.length() - 8).trim();
      return new MilliInterval(
          (long) (
              Float.parseFloat(value)
                  * 1000L
          )
      );
    }
    throw new IllegalArgumentException(value);
  }

  private static float parseTemperature(String value) {
    if (value == null) {
      return Float.NaN;
    } else if (value.endsWith(" C Internal")) {
      value = value.substring(0, value.length() - 11);
    } else if (value.endsWith(" C")) {
      value = value.substring(0, value.length() - 2);
    }
    value = value.trim();
    return value.length() == 0 ? Float.NaN : Float.parseFloat(value);
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
  private final MilliInterval tonbatt;
  private final MilliInterval cumonbatt;
  private final MilliInterval timeleft;
  private final float itemp;

  UpsStatus(String upsStatus) throws ParseException {
    // Default values
    String upsnameTmp = null;
    String statusTmp = null;
    float linevTmp = Float.NaN;
    float lotransTmp = Float.NaN;
    float hitransTmp = Float.NaN;
    float linefreqTmp = Float.NaN;
    float outputvTmp = Float.NaN;
    float nomoutvTmp = Float.NaN;
    float loadpctTmp = Float.NaN;
    float bchargeTmp = Float.NaN;
    float battvTmp = Float.NaN;
    float nombattvTmp = Float.NaN;
    int extbattsTmp = -1;
    int badbattsTmp = -1;
    MilliInterval tonbattTmp = null;
    MilliInterval cumonbattTmp = null;
    MilliInterval timeleftTmp = null;
    float itempTmp = Float.NaN;

    // Parse the status
    for (String line : Strings.splitLines(upsStatus)) {
      int colonPos = line.indexOf(':');
      if (colonPos == -1) {
        throw new LocalizedParseException(0, RESOURCES, "parse.noColon", line);
      }
      String name = line.substring(0, colonPos).trim();
      String value = line.substring(colonPos + 1).trim();
      if ("UPSNAME".equals(name)) {
        upsnameTmp   = value;
      } else if ("STATUS".equals(name)) {
        statusTmp    = value;
      } else if ("LINEV".equals(name)) {
        linevTmp     = parseVolts(value);
      } else if ("LOTRANS".equals(name)) {
        lotransTmp   = parseVolts(value);
      } else if ("HITRANS".equals(name)) {
        hitransTmp   = parseVolts(value);
      } else if ("LINEFREQ".equals(name)) {
        linefreqTmp  = parseFrequency(value);
      } else if ("OUTPUTV".equals(name)) {
        outputvTmp   = parseVolts(value);
      } else if ("NOMOUTV".equals(name)) {
        nomoutvTmp   = parseVolts(value);
      } else if ("LOADPCT".equals(name)) {
        loadpctTmp   = parsePercent(value);
      } else if ("BCHARGE".equals(name)) {
        bchargeTmp   = parsePercent(value);
      } else if ("BATTV".equals(name)) {
        battvTmp     = parseVolts(value);
      } else if ("NOMBATTV".equals(name)) {
        nombattvTmp  = parseVolts(value);
      } else if ("EXTBATTS".equals(name)) {
        extbattsTmp  = parseInt(value);
      } else if ("BADBATTS".equals(name)) {
        badbattsTmp  = parseInt(value);
      } else if ("TONBATT".equals(name)) {
        tonbattTmp   = parseTimeSpan(value);
      } else if ("CUMONBATT".equals(name)) {
        cumonbattTmp = parseTimeSpan(value);
      } else if ("TIMELEFT".equals(name)) {
        timeleftTmp  = parseTimeSpan(value);
      } else if ("ITEMP".equals(name)) {
        itempTmp     = parseTemperature(value);
      }
    }

    this.upsname   = upsnameTmp;
    this.status    = statusTmp;
    this.linev     = linevTmp;
    this.lotrans   = lotransTmp;
    this.hitrans   = hitransTmp;
    this.linefreq  = linefreqTmp;
    this.outputv   = outputvTmp;
    this.nomoutv   = nomoutvTmp;
    this.loadpct   = loadpctTmp;
    this.bcharge   = bchargeTmp;
    this.battv     = battvTmp;
    this.nombattv  = nombattvTmp;
    this.extbatts  = extbattsTmp;
    this.badbatts  = badbattsTmp;
    this.tonbatt   = tonbattTmp;
    this.cumonbatt = cumonbattTmp;
    this.timeleft  = timeleftTmp;
    this.itemp     = itempTmp;
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
  MilliInterval getTonbatt() {
    return tonbatt;
  }

  /**
   * @see  UpsResult#getCumonbatt()
   */
  MilliInterval getCumonbatt() {
    return cumonbatt;
  }

  /**
   * @see  UpsResult#getTimeleft()
   */
  MilliInterval getTimeleft() {
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
        cumonbatt,
        timeleft,
        itemp
    );
  }
}
