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
public class TimeNode extends TableMultiResultNodeImpl {

    private final AOServer _aoServer;

    TimeNode(ServerNode serverNode, AOServer aoServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
        super(
            serverNode.serversNode.rootNode,
            serverNode,
            TimeNodeWorker.getWorker(
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
        return ApplicationResourcesAccessor.getMessage(rootNode.locale, "TimeNode.label");
    }

    @Override
    public List<?> getColumnHeaders(Locale locale) {
        List<String> headers = new ArrayList<String>(1);
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "TimeNode.columnHeader.clockSkew"));
        return Collections.unmodifiableList(headers);
    }
}
