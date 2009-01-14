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
 * The node for the hard drive temperature monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class HardDrivesTemperatureNode extends TableResultNodeImpl {

    HardDrivesTemperatureNode(HardDrivesNode hardDrivesNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
        super(
            hardDrivesNode.serverNode.serversNode.rootNode,
            hardDrivesNode,
            HardDrivesTemperatureNodeWorker.getWorker(
                hardDrivesNode.serverNode.serversNode.rootNode.conn.getErrorHandler(),
                new File(hardDrivesNode.getPersistenceDirectory(), "hddtemp"),
                hardDrivesNode.getAOServer()
            ),
            port,
            csf,
            ssf
        );
    }

    @Override
    public String getLabel() {
        return ApplicationResourcesAccessor.getMessage(rootNode.locale, "HardDrivesTemperatureNode.label");
    }
}
