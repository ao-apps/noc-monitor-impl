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
 * The node for the DRBD monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class DrbdNode extends TableResultNodeImpl {

    private static final long serialVersionUID = 1L;

    DrbdNode(RaidNode raidNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
        super(
            raidNode.serverNode.serversNode.rootNode,
            raidNode,
            DrbdNodeWorker.getWorker(
                raidNode.serverNode.serversNode.rootNode.monitoringPoint,
                new File(raidNode.getPersistenceDirectory(), "drbdstatus"),
                raidNode.getAOServer()
            ),
            port,
            csf,
            ssf
        );
    }

    @Override
    public String getId() {
        return "drbd";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*rootNode.locale,*/ "DrbdNode.label");
    }
}
