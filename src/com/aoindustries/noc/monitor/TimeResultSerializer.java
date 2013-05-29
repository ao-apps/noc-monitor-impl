/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.io.CompressedDataInputStream;
import com.aoindustries.io.CompressedDataOutputStream;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.MonitoringPoint;
import com.aoindustries.noc.monitor.common.TimeResult;
import com.aoindustries.util.persistent.BufferedSerializer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author  AO Industries, Inc.
 */
public class TimeResultSerializer extends BufferedSerializer<TimeResult> {

    private static final int VERSION = 1;

    private final MonitoringPoint monitoringPoint;

    public TimeResultSerializer(MonitoringPoint monitoringPoint) {
        this.monitoringPoint = monitoringPoint;
    }

    @Override
    protected void serialize(TimeResult value, ByteArrayOutputStream buffer) throws IOException {
        CompressedDataOutputStream out = new CompressedDataOutputStream(buffer);
        try {
            out.writeCompressedInt(VERSION);
            out.writeLong(value.getTime());
            out.writeLong(value.getLatency());
            out.writeByte(value.getAlertLevel().ordinal());
            String error = value.getError();
            out.writeNullUTF(error);
            if(error==null) {
                out.writeLong(value.getSkew());
            }
        } finally {
            out.close();
        }
    }

    @Override
    public TimeResult deserialize(InputStream rawIn) throws IOException {
        CompressedDataInputStream in = new CompressedDataInputStream(rawIn);
        try {
            int version = in.readCompressedInt();
            if(version==1) {
                long time = in.readLong();
                long latency = in.readLong();
                AlertLevel alertLevel = AlertLevel.fromOrdinal(in.readByte());
                String error = in.readNullUTF();
                if(error!=null) return new TimeResult(monitoringPoint, time, latency, alertLevel, error);
                return new TimeResult(
                    monitoringPoint,
                    time,
                    latency,
                    alertLevel,
                    in.readLong()
                );
            } else throw new IOException("Unsupported object version: "+version);
        } finally {
            in.close();
        }
    }
}
