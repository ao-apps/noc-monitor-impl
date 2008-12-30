/*
 * Copyright 2008 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

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
public class NetDeviceBitRateNode extends TableMultiResultNodeImpl {

    NetDeviceBitRateNode(NetDeviceNode netDeviceNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException {
        super(
            netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode,
            netDeviceNode,
            NetDeviceBitRateNodeWorker.getWorker(
                netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode.conn.getErrorHandler(),
                netDeviceNode.getPersistenceDirectory(),
                netDeviceNode.getNetDevice()
            ),
            port,
            csf,
            ssf
        );
    }

    @Override
    public String getLabel() {
        return ApplicationResourcesAccessor.getMessage(rootNode.locale, "NetDeviceBitRateNode.label");
    }

    @Override
    public List<?> getColumnHeaders(Locale locale) {
        List<String> headers = new ArrayList<String>(5);
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "NetDeviceBitRateNode.columnHeader.txBitRate"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "NetDeviceBitRateNode.columnHeader.rxBitRate"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "NetDeviceBitRateNode.columnHeader.txPacketRate"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "NetDeviceBitRateNode.columnHeader.rxPacketRate"));
        headers.add(ApplicationResourcesAccessor.getMessage(locale, "NetDeviceBitRateNode.columnHeader.alertThresholds"));
        return Collections.unmodifiableList(headers);
    }
}
