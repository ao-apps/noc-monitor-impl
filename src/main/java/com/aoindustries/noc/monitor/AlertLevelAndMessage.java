/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008-2012, 2016, 2018, 2020, 2021  AO Industries, Inc.
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
package com.aoindustries.noc.monitor;

import com.aoindustries.noc.monitor.common.AlertLevel;
import java.util.Locale;
import java.util.function.Function;

/**
 * Stores two return values.
 *
 * @author  AO Industries, Inc.
 */
public class AlertLevelAndMessage {

	/**
	 * An alert level and message with no alert and no message.
	 */
	public static final AlertLevelAndMessage NONE = new AlertLevelAndMessage(AlertLevel.NONE, null);

	private final AlertLevel alertLevel;
	private final Function<Locale, String> alertMessage;

	public AlertLevelAndMessage(AlertLevel alertLevel, Function<Locale, String> alertMessage) {
		this.alertLevel = alertLevel;
		this.alertMessage = alertMessage;
	}

	public AlertLevel getAlertLevel() {
		return alertLevel;
	}

	/**
	 * Gets the alert message or <code>null</code> for none.
	 */
	public Function<Locale, String> getAlertMessage() {
		return alertMessage;
	}

	/**
	 * Gets a new alert level and message if a higher alert level, otherwise returns
	 * this alert level and message.
	 */
	public AlertLevelAndMessage escalate(AlertLevel newAlertLevel, Function<Locale, String> newAlertMessage) {
		int diff = newAlertLevel.compareTo(this.alertLevel);
		if(diff > 0) return new AlertLevelAndMessage(newAlertLevel, newAlertMessage);
		if(
			diff == 0
			// Use the new alert if the old one had an empty message (like NONE above)
			&& this.alertMessage == null
			&& newAlertMessage != null
		) {
			return new AlertLevelAndMessage(newAlertLevel, newAlertMessage);
		}
		return this;
	}
}
