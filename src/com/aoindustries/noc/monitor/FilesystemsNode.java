/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.AOServer;
import java.io.File;
import java.io.IOException;

/**
 * The node for the filesystem monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class FilesystemsNode extends TableResultNodeImpl {

    private final AOServer _aoServer;
    
    FilesystemsNode(ServerNode serverNode, AOServer aoServer) throws IOException {
        super(
            serverNode.serversNode.rootNode,
            serverNode,
            FilesystemsNodeWorker.getWorker(
                serverNode.serversNode.rootNode.monitoringPoint,
                new File(serverNode.getPersistenceDirectory(), "filesystems"),
                aoServer
            )
        );
        this._aoServer = aoServer;
    }

    @Override
    public String getId() {
        return "filesystems";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*rootNode.locale,*/ "FilesystemsNode.label");
    }
}
