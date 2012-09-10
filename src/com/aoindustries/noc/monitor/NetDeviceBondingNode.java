/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
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

    private static final long serialVersionUID = 1L;

    NetDeviceBondingNode(NetDeviceNode netDeviceNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
        super(
            netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode,
            netDeviceNode,
            NetDeviceBondingNodeWorker.getWorker(
                netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode.monitoringPoint,
                new File(netDeviceNode.getPersistenceDirectory(), "bonding"),
                netDeviceNode.getNetDevice()
            ),
            port,
            csf,
            ssf
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
