/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.noc.monitor.common.TimeResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author  AO Industries, Inc.
 */
public class TimeNode extends TableMultiResultNodeImpl<TimeResult> {

    private final AOServer _aoServer;

    TimeNode(ServerNode serverNode, AOServer aoServer) throws IOException {
        super(
            serverNode.serversNode.rootNode,
            serverNode,
            TimeNodeWorker.getWorker(
                serverNode.serversNode.rootNode.monitoringPoint,
                serverNode.getPersistenceDirectory(),
                aoServer
            )
        );
        this._aoServer = aoServer;
    }

    @Override
    public String getId() {
        return "time";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*rootNode.locale,*/ "TimeNode.label");
    }

    @Override
    public List<?> getColumnHeaders() {
        List<String> headers = new ArrayList<String>(1);
        headers.add(accessor.getMessage(/*locale,*/ "TimeNode.columnHeader.clockSkew"));
        return Collections.unmodifiableList(headers);
    }
}
