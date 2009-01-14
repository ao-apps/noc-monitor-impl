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
 * The node for the 3ware monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class MdRaidNode extends SingleResultNodeImpl {

    MdRaidNode(RaidNode raidNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
        super(
            raidNode.serverNode.serversNode.rootNode,
            raidNode,
            MdRaidNodeWorker.getWorker(
                raidNode.serverNode.serversNode.rootNode.conn.getErrorHandler(),
                new File(raidNode.getPersistenceDirectory(), "mdstat"),
                raidNode.getAOServer()
            ),
            port,
            csf,
            ssf
        );
    }

    public String getLabel() {
        return ApplicationResourcesAccessor.getMessage(rootNode.locale, "MdRaidNode.label");
    }
}
