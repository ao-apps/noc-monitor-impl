/*
 * Copyright 2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.noc.monitor.common.Node;

/**
 * The monitor context receives call-back notifications for important events
 * that facilitate integration with the system, such as exporting objects for
 * RMI.
 *
 * @author  AO Industries, Inc.
 */
public interface MonitorContext {

    /**
     * Called before a new node is added to the system.
     * This must execute quickly as it could block critical processing inside synchronized blocks.
     */
    void initNode(Node node);

    /**
     * Called after a node is removed from the system.
     * This must execute quickly as it could block critical processing inside synchronized blocks.
     */
    void destroyNode(Node node);
}
