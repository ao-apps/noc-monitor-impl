/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.IPAddress;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;

/**
 * The node for the reverse DNS monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class ReverseDnsNode extends TableResultNodeImpl {

    private static final long serialVersionUID = 1L;

    private final IPAddress ipAddress;
    
    ReverseDnsNode(IPAddressNode ipAddressNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException, SQLException {
        super(
            ipAddressNode.ipAddressesNode.netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode,
            ipAddressNode,
            ReverseDnsNodeWorker.getWorker(
                new File(ipAddressNode.getPersistenceDirectory(), "rdns"),
                ipAddressNode.getIPAddress()
            ),
            port,
            csf,
            ssf
        );
        this.ipAddress = ipAddressNode.getIPAddress();
    }

    @Override
    public String getLabel() {
        return ApplicationResourcesAccessor.getMessage(rootNode.locale, "ReverseDnsNode.label");
    }
}