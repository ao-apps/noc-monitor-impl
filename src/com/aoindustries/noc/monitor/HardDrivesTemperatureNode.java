/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import java.io.File;
import java.io.IOException;

/**
 * The node for the hard drive temperature monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class HardDrivesTemperatureNode extends TableResultNodeImpl {

    HardDrivesTemperatureNode(HardDrivesNode hardDrivesNode) throws IOException {
        super(
            hardDrivesNode.serverNode.serversNode.rootNode,
            hardDrivesNode,
            HardDrivesTemperatureNodeWorker.getWorker(
                hardDrivesNode.serverNode.serversNode.rootNode.monitoringPoint,
                new File(hardDrivesNode.getPersistenceDirectory(), "hddtemp"),
                hardDrivesNode.getAOServer()
            )
        );
    }

    @Override
    public String getId() {
        return "hard_drives";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*rootNode.locale,*/ "HardDrivesTemperatureNode.label");
    }
}
