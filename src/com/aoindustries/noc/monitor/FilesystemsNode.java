/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;

/**
 * The node for the filesystem monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class FilesystemsNode extends TableResultNodeImpl {

    private final AOServer _aoServer;
    
    FilesystemsNode(ServerNode serverNode, AOServer aoServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
        super(
            serverNode.serversNode.rootNode,
            serverNode,
            FilesystemsNodeWorker.getWorker(
                serverNode.serversNode.rootNode.conn.getErrorHandler(),
                new File(serverNode.getPersistenceDirectory(), "filesystems"),
                aoServer
            ),
            port,
            csf,
            ssf
        );
        this._aoServer = aoServer;
    }

    @Override
    public String getLabel() {
        return ApplicationResourcesAccessor.getMessage(rootNode.locale, "FilesystemsNode.label");
    }
}
