/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.NetDeviceBitRateResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author  AO Industries, Inc.
 */
public class NetDeviceBitRateNode extends TableMultiResultNodeImpl<NetDeviceBitRateResult> {

    NetDeviceBitRateNode(NetDeviceNode netDeviceNode) throws IOException {
        super(
            netDeviceNode._netDevicesNode.serverNode.serversNode.rootNode,
            netDeviceNode,
            NetDeviceBitRateNodeWorker.getWorker(
                netDeviceNode._netDevicesNode.serverNode.serversNode.rootNode.monitoringPoint,
                netDeviceNode.getPersistenceDirectory(),
                netDeviceNode.getNetDevice()
            )
        );
    }

    @Override
    public String getId() {
        return "bit_rate";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*rootNode.locale,*/ "NetDeviceBitRateNode.label");
    }

    @Override
    public List<?> getColumnHeaders() {
        List<String> headers = new ArrayList<String>(5);
        headers.add(accessor.getMessage(/*locale,*/ "NetDeviceBitRateNode.columnHeader.txBitRate"));
        headers.add(accessor.getMessage(/*locale,*/ "NetDeviceBitRateNode.columnHeader.rxBitRate"));
        headers.add(accessor.getMessage(/*locale,*/ "NetDeviceBitRateNode.columnHeader.txPacketRate"));
        headers.add(accessor.getMessage(/*locale,*/ "NetDeviceBitRateNode.columnHeader.rxPacketRate"));
        headers.add(accessor.getMessage(/*locale,*/ "NetDeviceBitRateNode.columnHeader.alertThresholds"));
        return Collections.unmodifiableList(headers);
    }
}
