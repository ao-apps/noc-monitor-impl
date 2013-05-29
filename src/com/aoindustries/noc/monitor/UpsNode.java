/*
 * Copyright 2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.noc.monitor.common.UpsResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Monitors UPS status for an AOServer.
 *
 * @author  AO Industries, Inc.
 */
public class UpsNode extends TableMultiResultNodeImpl<UpsResult> {

    private final AOServer _aoServer;

    UpsNode(ServerNode serverNode, AOServer aoServer) throws IOException {
        super(
            serverNode.serversNode.rootNode,
            serverNode,
            UpsNodeWorker.getWorker(
                serverNode.serversNode.rootNode.monitoringPoint,
                serverNode.getPersistenceDirectory(),
                aoServer
            )
        );
        this._aoServer = aoServer;
    }

    @Override
    public String getId() {
        return "ups";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*rootNode.locale,*/ "UpsNode.label");
    }

    @Override
    public List<?> getColumnHeaders(/*Locale locale*/) {
        List<String> headers = new ArrayList<String>(11);
        headers.add(accessor.getMessage(/*locale,*/ "UpsNode.columnHeader.upsname"));
        headers.add(accessor.getMessage(/*locale,*/ "UpsNode.columnHeader.status"));
        headers.add(accessor.getMessage(/*locale,*/ "UpsNode.columnHeader.linev"));
        headers.add(accessor.getMessage(/*locale,*/ "UpsNode.columnHeader.outputv"));
        headers.add(accessor.getMessage(/*locale,*/ "UpsNode.columnHeader.loadpct"));
        headers.add(accessor.getMessage(/*locale,*/ "UpsNode.columnHeader.bcharge"));
        headers.add(accessor.getMessage(/*locale,*/ "UpsNode.columnHeader.battv"));
        headers.add(accessor.getMessage(/*locale,*/ "UpsNode.columnHeader.badbatts"));
        headers.add(accessor.getMessage(/*locale,*/ "UpsNode.columnHeader.tonbatt"));
        headers.add(accessor.getMessage(/*locale,*/ "UpsNode.columnHeader.timeleft"));
        headers.add(accessor.getMessage(/*locale,*/ "UpsNode.columnHeader.itemp"));
        return Collections.unmodifiableList(headers);
    }
}
