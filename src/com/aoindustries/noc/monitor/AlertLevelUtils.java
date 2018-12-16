/*
 * Copyright 2014, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.lang.EnumUtils;
import com.aoindustries.noc.monitor.common.AlertLevel;

/**
 * Maps between different implementations of alert level.
 *
 * @author  AO Industries, Inc.
 */
public class AlertLevelUtils {

	public static AlertLevel getMonitoringAlertLevel(com.aoindustries.aoserv.client.monitoring.AlertLevel aoservAlertLevel) {
		if(aoservAlertLevel == null) return null;
		switch(aoservAlertLevel) {
			case NONE     : return AlertLevel.NONE;
			case LOW      : return AlertLevel.LOW;
			case MEDIUM   : return AlertLevel.MEDIUM;
			case HIGH     : return AlertLevel.HIGH;
			case CRITICAL : return AlertLevel.CRITICAL;
			case UNKNOWN  : return AlertLevel.UNKNOWN;
			default       : throw new AssertionError("Unexpected aoservAlertLevel: " + aoservAlertLevel);
		}
	}

	/**
	 * Gets the greatest alert level found in a collection of nodes and the
	 * given starting value.
	 */
	public static AlertLevel getMaxAlertLevel(AlertLevel level, Iterable<? extends NodeImpl> nodes) {
		for(NodeImpl node : nodes) {
			level = EnumUtils.max(level, node.getAlertLevel());
		}
		return level;
	}

	/**
	 * Gets the greatest alert level found in a collection of nodes.
	 */
	public static AlertLevel getMaxAlertLevel(Iterable<? extends NodeImpl> nodes) {
		return getMaxAlertLevel(AlertLevel.NONE, nodes);
	}

	/**
	 * Gets the greatest alert level between a node and a
	 * given starting value.  If the node is null, alert level is unchanged.
	 */
	public static AlertLevel getMaxAlertLevel(AlertLevel level, NodeImpl node) {
		if(node != null) {
			level = EnumUtils.max(level, node.getAlertLevel());
		}
		return level;
	}

	/**
	 * Gets the alert level for a node.
	 * If the node is null, alert level is NONE.
	 */
	public static AlertLevel getMaxAlertLevel(NodeImpl node) {
		return node==null ? AlertLevel.NONE : node.getAlertLevel();
	}

	/**
	 * Gets the greatest alert level of any node.
	 * If all nodes are null, alert level is NONE.
	 */
	public static AlertLevel getMaxAlertLevel(NodeImpl node1, NodeImpl node2) {
        AlertLevel level = AlertLevel.NONE;
        level = getMaxAlertLevel(level, node1);
        level = getMaxAlertLevel(level, node2);
        return level;
	}

	/**
	 * Gets the greatest alert level of any node.
	 * If all nodes are null, alert level is NONE.
	 */
	public static AlertLevel getMaxAlertLevel(NodeImpl node1, NodeImpl node2, NodeImpl node3) {
        AlertLevel level = AlertLevel.NONE;
        level = getMaxAlertLevel(level, node1);
        level = getMaxAlertLevel(level, node2);
        level = getMaxAlertLevel(level, node3);
        return level;
	}

	/**
	 * Gets the greatest alert level of any node.
	 * If all nodes are null, alert level is NONE.
	 */
	public static AlertLevel getMaxAlertLevel(NodeImpl node1, NodeImpl node2, NodeImpl node3, NodeImpl node4) {
        AlertLevel level = AlertLevel.NONE;
        level = getMaxAlertLevel(level, node1);
        level = getMaxAlertLevel(level, node2);
        level = getMaxAlertLevel(level, node3);
        level = getMaxAlertLevel(level, node4);
        return level;
	}

	/**
	 * Gets the greatest alert level of any node.
	 * If all nodes are null, alert level is NONE.
	 */
	public static AlertLevel getMaxAlertLevel(NodeImpl ... nodes) {
        AlertLevel level = AlertLevel.NONE;
		for(NodeImpl node : nodes) {
	        level = getMaxAlertLevel(level, node);
		}
        return level;
	}

	private AlertLevelUtils() {
	}
}
