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
 * The node for the 3ware monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class ThreeWareRaidNode extends SingleResultNodeImpl {

    ThreeWareRaidNode(RaidNode raidNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
        super(
            raidNode.serverNode.serversNode.rootNode,
            raidNode,
            ThreeWareRaidNodeWorker.getWorker(
                raidNode.serverNode.serversNode.rootNode.monitoringPoint,
                new File(raidNode.getPersistenceDirectory(), "3ware"),
                raidNode.getAOServer()
            ),
            port,
            csf,
            ssf
        );
    }

    @Override
    public String getId() {
        return "3ware";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*rootNode.locale,*/ "ThreeWareRaidNode.label");
    }
}
