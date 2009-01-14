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
 * The node for the signups monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class SignupsNode extends TableResultNodeImpl {

    SignupsNode(RootNodeImpl rootNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
        super(
            rootNode,
            rootNode,
            SignupsNodeWorker.getWorker(
                rootNode.conn.getErrorHandler(),
                new File(rootNode.getPersistenceDirectory(), "signups"),
                rootNode.conn
            ),
            port,
            csf,
            ssf
        );
    }

    @Override
    public String getLabel() {
        return ApplicationResourcesAccessor.getMessage(rootNode.locale, "SignupsNode.label");
    }
}
