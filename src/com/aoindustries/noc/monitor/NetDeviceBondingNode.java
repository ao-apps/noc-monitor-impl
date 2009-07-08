/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;

/**
 * The node for the bonding monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class NetDeviceBondingNode extends SingleResultNodeImpl {

    NetDeviceBondingNode(NetDeviceNode netDeviceNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
        super(
            netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode,
            netDeviceNode,
            NetDeviceBondingNodeWorker.getWorker(
                new File(netDeviceNode.getPersistenceDirectory(), "bonding"),
                netDeviceNode.getNetDevice()
            ),
            port,
            csf,
            ssf
        );
    }

    @Override
    public String getLabel() {
        return ApplicationResourcesAccessor.getMessage(rootNode.locale, "NetDeviceBondingNode.label");
    }
}
