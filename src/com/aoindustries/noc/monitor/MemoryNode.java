/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * @author  AO Industries, Inc.
 */
public class MemoryNode extends TableMultiResultNodeImpl {

    private final AOServer _aoServer;

    MemoryNode(ServerNode serverNode, AOServer aoServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
        super(
            serverNode.serversNode.rootNode,
            serverNode,
            MemoryNodeWorker.getWorker(
                serverNode.serversNode.rootNode.conn.getErrorHandler(),
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
        return ApplicationResourcesAccessor.getMessage(rootNode.locale, "MemoryNode.label");
    }

    @Override
    public List<?> getColumnHeaders(Locale locale) {
        List<String> headers = new ArrayList<String>(6);
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MemoryNode.columnHeader.memTotal"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MemoryNode.columnHeader.memFree"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MemoryNode.columnHeader.buffers"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MemoryNode.columnHeader.cached"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MemoryNode.columnHeader.swapTotal"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "MemoryNode.columnHeader.swapFree"));
        return Collections.unmodifiableList(headers);
    }
}
