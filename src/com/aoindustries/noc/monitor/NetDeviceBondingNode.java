/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import java.io.File;
import java.io.IOException;

/**
 * The node for the bonding monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class NetDeviceBondingNode extends SingleResultNodeImpl {

    private static final long serialVersionUID = 1L;

    NetDeviceBondingNode(NetDeviceNode netDeviceNode) throws IOException {
        super(
            netDeviceNode._netDevicesNode.serverNode.serversNode.rootNode,
            netDeviceNode,
            NetDeviceBondingNodeWorker.getWorker(
                netDeviceNode._netDevicesNode.serverNode.serversNode.rootNode.monitoringPoint,
                new File(netDeviceNode.getPersistenceDirectory(), "bonding"),
                netDeviceNode.getNetDevice()
            )
        );
    }

    @Override
    public String getId() {
        return "bonding";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*rootNode.locale,*/ "NetDeviceBondingNode.label");
    }
}
