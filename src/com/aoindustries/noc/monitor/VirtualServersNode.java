/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.aoserv.client.Server;
import java.io.IOException;
import java.sql.SQLException;

/**
 * The top-level node has one child for each of the servers.
 *
 * @author  AO Industries, Inc.
 */
public class VirtualServersNode extends ServersNode {

    private static final long serialVersionUID = 1L;

    VirtualServersNode(RootNodeImpl rootNode) {
        super(rootNode);
    }

    @Override
    public String getId() {
        return "virtual_servers";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*rootNode.locale,*/ "VirtualServersNode.label");
    }

    @Override
    boolean includeServer(Server server) throws SQLException, IOException {
        AOServer aoServer = server.getAOServer();
        return
            // Is Xen dom0
            server.getVirtualServer()!=null
            || (
                // Is ao-box in fail-over
                aoServer!=null && aoServer.getFailoverServer()!=null
            )
        ;
    }
}
