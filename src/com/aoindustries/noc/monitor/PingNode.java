/*
 * Copyright 2008 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The ping node per server.
 *
 * @author  AO Industries, Inc.
 */
public class PingNode extends TableMultiResultNodeImpl {

    private final IPAddressNode ipAddressNode;

    PingNode(IPAddressNode ipAddressNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
        super(
            ipAddressNode.ipAddressesNode.netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode,
            ipAddressNode,
            PingNodeWorker.getWorker(
                ipAddressNode.ipAddressesNode.netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode.conn.getErrorHandler(),
                ipAddressNode.getPersistenceDirectory(),
                ipAddressNode.getIPAddress()
            ),
            port,
            csf,
            ssf
        );
        this.ipAddressNode = ipAddressNode;
    }

    @Override
    public String getLabel() {
        return ApplicationResourcesAccessor.getMessage(ipAddressNode.ipAddressesNode.netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode.locale, "PingNode.label");
    }

    @Override
    public List<?> getColumnHeaders(Locale locale) {
        return Collections.emptyList();
    }
}
