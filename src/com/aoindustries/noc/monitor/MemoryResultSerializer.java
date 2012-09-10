/*
 * Copyright 2009-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.io.CompressedDataInputStream;
import com.aoindustries.io.CompressedDataOutputStream;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.MemoryResult;
import com.aoindustries.noc.monitor.common.MonitoringPoint;
import com.aoindustries.util.persistent.BufferedSerializer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author  AO Industries, Inc.
 */
public class MemoryResultSerializer extends BufferedSerializer<MemoryResult> {

    private static final int VERSION = 1;

    private final MonitoringPoint monitoringPoint;

    public MemoryResultSerializer(MonitoringPoint monitoringPoint) {
        this.monitoringPoint = monitoringPoint;
    }

    @Override
    protected void serialize(MemoryResult value, ByteArrayOutputStream buffer) throws IOException {
        CompressedDataOutputStream out = new CompressedDataOutputStream(buffer);
        try {
            out.writeCompressedInt(VERSION);
            out.writeLong(value.getTime());
            out.writeLong(value.getLatency());
            out.writeByte(value.getAlertLevel().ordinal());
            String error = value.getError();
            out.writeNullUTF(error);
            if(error==null) {
                out.writeLong(value.getMemTotal());
                out.writeLong(value.getMemFree());
                out.writeLong(value.getBuffers());
                out.writeLong(value.getCached());
                out.writeLong(value.getSwapTotal());
                out.writeLong(value.getSwapFree());
            }
        } finally {
            out.close();
        }
    }

    @Override
    public MemoryResult deserialize(InputStream rawIn) throws IOException {
        CompressedDataInputStream in = new CompressedDataInputStream(rawIn);
        try {
            int version = in.readCompressedInt();
            if(version==1) {
                long time = in.readLong();
                long latency = in.readLong();
                AlertLevel alertLevel = AlertLevel.fromOrdinal(in.readByte());
                String error = in.readNullUTF();
                if(error!=null) return new MemoryResult(monitoringPoint, time, latency, alertLevel, error);
                return new MemoryResult(
                    monitoringPoint,
                    time,
                    latency,
                    alertLevel,
                    in.readLong(),
                    in.readLong(),
                    in.readLong(),
                    in.readLong(),
                    in.readLong(),
                    in.readLong()
                );
            } else throw new IOException("Unsupported object version: "+version);
        } finally {
            in.close();
        }
    }
}
