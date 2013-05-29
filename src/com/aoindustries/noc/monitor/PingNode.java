/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.PingResult;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * The ping node per server.
 *
 * @author  AO Industries, Inc.
 */
public class PingNode extends TableMultiResultNodeImpl<PingResult> {

    private final IPAddressNode ipAddressNode;

    PingNode(IPAddressNode ipAddressNode) throws IOException {
        super(
            ipAddressNode.ipAddressesNode.netDeviceNode._netDevicesNode.serverNode.serversNode.rootNode,
            ipAddressNode,
            PingNodeWorker.getWorker(
                ipAddressNode.ipAddressesNode.netDeviceNode._netDevicesNode.serverNode.serversNode.rootNode.monitoringPoint,
                ipAddressNode.getPersistenceDirectory(),
                ipAddressNode.getIPAddress()
            )
        );
        this.ipAddressNode = ipAddressNode;
    }

    @Override
    public String getId() {
        return "pings";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*ipAddressNode.ipAddressesNode.netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode.locale,*/ "PingNode.label");
    }

    @Override
    public List<?> getColumnHeaders() {
        return Collections.emptyList();
    }
}
