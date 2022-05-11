/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2014, 2018, 2020, 2021, 2022  AO Industries, Inc.
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

package com.aoindustries.noc.monitor;

import com.aoapps.lang.EnumUtils;
import com.aoindustries.noc.monitor.common.AlertLevel;

/**
 * Maps between different implementations of alert level.
 *
 * @author  AO Industries, Inc.
 */
public final class AlertLevelUtils {

  /** Make no instances. */
  private AlertLevelUtils() {
    throw new AssertionError();
  }

  public static AlertLevel getMonitoringAlertLevel(com.aoindustries.aoserv.client.monitoring.AlertLevel aoservAlertLevel) {
    if (aoservAlertLevel == null) {
      return null;
    }
    switch (aoservAlertLevel) {
      case NONE:
        return AlertLevel.NONE;
      case LOW:
        return AlertLevel.LOW;
      case MEDIUM:
        return AlertLevel.MEDIUM;
      case HIGH:
        return AlertLevel.HIGH;
      case CRITICAL:
        return AlertLevel.CRITICAL;
      case UNKNOWN:
        return AlertLevel.UNKNOWN;
      default:
        throw new AssertionError("Unexpected aoservAlertLevel: " + aoservAlertLevel);
    }
  }

  /**
   * Gets the greatest alert level found in a collection of nodes and the
   * given starting value.
   */
  public static AlertLevel getMaxAlertLevel(AlertLevel level, Iterable<? extends NodeImpl> nodes) {
    for (NodeImpl node : nodes) {
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
    if (node != null) {
      level = EnumUtils.max(level, node.getAlertLevel());
    }
    return level;
  }

  /**
   * Gets the alert level for a node.
   * If the node is null, alert level is NONE.
   */
  public static AlertLevel getMaxAlertLevel(NodeImpl node) {
    return node == null ? AlertLevel.NONE : node.getAlertLevel();
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
    for (NodeImpl node : nodes) {
      level = getMaxAlertLevel(level, node);
    }
    return level;
  }
}
