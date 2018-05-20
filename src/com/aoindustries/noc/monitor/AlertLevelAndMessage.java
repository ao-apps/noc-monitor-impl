/*
 * Copyright 2008-2012, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.noc.monitor.common.AlertLevel;
import java.util.function.Supplier;

/**
 * Stores two return values.
 *
 * @author  AO Industries, Inc.
 */
class AlertLevelAndMessage {

	/**
	 * An alert level and message with no alert and no message.
	 */
	static final AlertLevelAndMessage NONE = new AlertLevelAndMessage(AlertLevel.NONE, "");

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

	/**
	 * Gets a new alert level and message if a higher alert level, otherwise returns
	 * this alert level and message.
	 */
	AlertLevelAndMessage escalate(AlertLevel newAlertLevel, Supplier<String> newAlertMessage) {
		int diff = newAlertLevel.compareTo(this.alertLevel);
		if(diff > 0) return new AlertLevelAndMessage(newAlertLevel, newAlertMessage.get());
		if(
			diff == 0
			// Use the new alert if the old one had an empty message (like NONE above)
			&& this.alertMessage.isEmpty()
		) {
			String message = newAlertMessage.get();
			if(!message.isEmpty()) return new AlertLevelAndMessage(newAlertLevel, message);
		}
		return this;
	}
}
