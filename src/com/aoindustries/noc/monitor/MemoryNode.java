/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.noc.monitor.common.MemoryResult;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author  AO Industries, Inc.
 */
public class MemoryNode extends TableMultiResultNodeImpl<MemoryResult> {

    private static final long serialVersionUID = 1L;

    private final AOServer _aoServer;

    MemoryNode(ServerNode serverNode, AOServer aoServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
        super(
            serverNode.serversNode.rootNode,
            serverNode,
            MemoryNodeWorker.getWorker(
                serverNode.getPersistenceDirectory(),
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
        return accessor.getMessage(/*rootNode.locale,*/ "MemoryNode.label");
    }

    @Override
    public List<?> getColumnHeaders(/*Locale locale*/) {
        List<String> headers = new ArrayList<String>(6);
        headers.add(accessor.getMessage(/*locale,*/ "MemoryNode.columnHeader.memTotal"));
        headers.add(accessor.getMessage(/*locale,*/ "MemoryNode.columnHeader.memFree"));
        headers.add(accessor.getMessage(/*locale,*/ "MemoryNode.columnHeader.buffers"));
        headers.add(accessor.getMessage(/*locale,*/ "MemoryNode.columnHeader.cached"));
        headers.add(accessor.getMessage(/*locale,*/ "MemoryNode.columnHeader.swapTotal"));
        headers.add(accessor.getMessage(/*locale,*/ "MemoryNode.columnHeader.swapFree"));
        return Collections.unmodifiableList(headers);
    }
}
