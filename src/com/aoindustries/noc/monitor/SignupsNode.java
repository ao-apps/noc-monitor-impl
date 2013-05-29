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
 * The node for the signups monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class SignupsNode extends TableResultNodeImpl {

    SignupsNode(RootNodeImpl rootNode) throws IOException {
        super(
            rootNode,
            rootNode,
            SignupsNodeWorker.getWorker(
                rootNode.monitoringPoint,
                new File(rootNode.getPersistenceDirectory(), "signups"),
                rootNode.conn
            )
        );
    }

    @Override
    public String getId() {
        return "signups";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*rootNode.locale,*/ "SignupsNode.label");
    }
}
