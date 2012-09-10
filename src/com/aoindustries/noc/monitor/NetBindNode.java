/*
 * Copyright 2009-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.NetBindResult;
import java.io.File;
import java.io.IOException;
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
    private final String id;
    private final String label;

    NetBindNode(NetBindsNode netBindsNode, NetBindsNode.NetMonitorSetting netMonitorSetting) throws IOException, SQLException {
        super(
            netBindsNode.ipAddressNode.ipAddressesNode.netDeviceNode._netDevicesNode.serverNode.serversNode.rootNode,
            netBindsNode,
            NetBindNodeWorker.getWorker(
                netBindsNode.ipAddressNode.ipAddressesNode.netDeviceNode._netDevicesNode.serverNode.serversNode.rootNode.monitoringPoint,
                new File(netBindsNode.getPersistenceDirectory(), netMonitorSetting.getPort()+"_"+netMonitorSetting.getNetProtocol()),
                netMonitorSetting
            )
        );
        this.netMonitorSetting = netMonitorSetting;
        this.id = netMonitorSetting.getPort()+"/"+netMonitorSetting.getNetProtocol();
        this.label = id +" ("+netMonitorSetting.getNetBind().getAppProtocol().getProtocol()+')';
    }

    NetBindsNode.NetMonitorSetting getNetMonitorSetting() {
        return netMonitorSetting;
    }

    @Override
    public String getId() {
        return id;
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
