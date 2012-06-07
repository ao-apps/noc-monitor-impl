/*
 * Copyright 2009-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.common.NetBindResult;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * The net bind monitor.
 *
 * @author  AO Industries, Inc.
 */
public class NetBindNode extends TableMultiResultNodeImpl<NetBindResult> {

    private static final long serialVersionUID = 1L;

    private final NetBindsNode.NetMonitorSetting netMonitorSetting;
    private final String label;

    NetBindNode(NetBindsNode netBindsNode, NetBindsNode.NetMonitorSetting netMonitorSetting, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException, IOException, SQLException {
        super(
            netBindsNode.ipAddressNode.ipAddressesNode.netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode,
            netBindsNode,
            NetBindNodeWorker.getWorker(
                new File(netBindsNode.getPersistenceDirectory(), netMonitorSetting.getPort()+"_"+netMonitorSetting.getNetProtocol()),
                netMonitorSetting
            ),
            port,
            csf,
            ssf
        );
        this.netMonitorSetting = netMonitorSetting;
        this.label = netMonitorSetting.getPort()+"/"+netMonitorSetting.getNetProtocol()+" ("+netMonitorSetting.getNetBind().getAppProtocol().getProtocol()+')';
    }

    NetBindsNode.NetMonitorSetting getNetMonitorSetting() {
        return netMonitorSetting;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public List<?> getColumnHeaders() {
        return Collections.singletonList(accessor.getMessage(/*locale,*/ "NetBindNode.columnHeader.result"));
    }
}
