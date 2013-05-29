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
 * The node for the 3ware monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class MdRaidNode extends SingleResultNodeImpl {

    private static final long serialVersionUID = 1L;

    MdRaidNode(RaidNode raidNode) throws IOException {
        super(
            raidNode.serverNode.serversNode.rootNode,
            raidNode,
            MdRaidNodeWorker.getWorker(
                raidNode.serverNode.serversNode.rootNode.monitoringPoint,
                new File(raidNode.getPersistenceDirectory(), "mdstat"),
                raidNode.getAOServer()
            )
        );
    }

    @Override
    public String getId() {
        return "md";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*rootNode.locale,*/ "MdRaidNode.label");
    }
}
