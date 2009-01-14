/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.noc.common.AlertLevel;

/**
 * Stores two return values.
 *
 * @author  AO Industries, Inc.
 */
class AlertLevelAndMessage {

    final private AlertLevel alertLevel;
    final private String alertMessage;

    AlertLevelAndMessage(AlertLevel alertLevel, String alertMessage) {
        this.alertLevel = alertLevel;
        this.alertMessage = alertMessage;
    }

    AlertLevel getAlertLevel() {
        return alertLevel;
    }
    
    /**
     * Gets the alert message or <code>null</code> for none.
     */
    String getAlertMessage() {
        return alertMessage;
    }
}
