/*
 * Copyright 2012-2013 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.io.CompressedDataInputStream;
import com.aoindustries.io.CompressedDataOutputStream;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.UpsResult;
import com.aoindustries.sql.MilliInterval;
import com.aoindustries.util.persistent.BufferedSerializer;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author  AO Industries, Inc.
 */
public class UpsResultSerializer extends BufferedSerializer<UpsResult> {

    private static final int VERSION = 1;

    private static void writeTimeSpan(MilliInterval value, DataOutput out) throws IOException {
        out.writeLong(value==null ? Long.MIN_VALUE : value.getIntervalMillis());
    }

    private static MilliInterval readTimeSpan(DataInput in) throws IOException {
        long value = in.readLong();
        return value==Long.MIN_VALUE ? null : new MilliInterval(value);
    }

    @Override
    protected void serialize(UpsResult value, ByteArrayOutputStream buffer) throws IOException {
        CompressedDataOutputStream out = new CompressedDataOutputStream(buffer);
        try {
            out.writeCompressedInt(VERSION);
            out.writeLong(value.getTime());
            out.writeLong(value.getLatency());
            out.writeByte(value.getAlertLevel().ordinal());
            String error = value.getError();
            out.writeNullUTF(error);
            if(error==null) {
                out.writeNullUTF(value.getUpsname());
                out.writeNullUTF(value.getStatus());
                out.writeFloat(value.getLinev());
                out.writeFloat(value.getLotrans());
                out.writeFloat(value.getHitrans());
                out.writeFloat(value.getLinefreq());
                out.writeFloat(value.getOutputv());
                out.writeFloat(value.getNomoutv());
                out.writeFloat(value.getLoadpct());
                out.writeFloat(value.getBcharge());
                out.writeFloat(value.getBattv());
                out.writeFloat(value.getNombattv());
                out.writeCompressedInt(value.getExtbatts());
                out.writeCompressedInt(value.getBadbatts());
                writeTimeSpan(value.getTonbatt(), out);
                writeTimeSpan(value.getTimeleft(), out);
                out.writeFloat(value.getItemp());
            }
        } finally {
            out.close();
        }
    }

    @Override
    public UpsResult deserialize(InputStream rawIn) throws IOException {
        CompressedDataInputStream in = new CompressedDataInputStream(rawIn);
        try {
            int version = in.readCompressedInt();
            if(version==1) {
                long time = in.readLong();
                long latency = in.readLong();
                AlertLevel alertLevel = AlertLevel.fromOrdinal(in.readByte());
                String error = in.readNullUTF();
                if(error!=null) return new UpsResult(time, latency, alertLevel, error);
                return new UpsResult(
                    time,
                    latency,
                    alertLevel,
                    in.readNullUTF(),
                    in.readNullUTF(),
                    in.readFloat(),
                    in.readFloat(),
                    in.readFloat(),
                    in.readFloat(),
                    in.readFloat(),
                    in.readFloat(),
                    in.readFloat(),
                    in.readFloat(),
                    in.readFloat(),
                    in.readFloat(),
                    in.readCompressedInt(),
                    in.readCompressedInt(),
                    readTimeSpan(in),
                    readTimeSpan(in),
                    in.readFloat()
                );
            } else throw new IOException("Unsupported object version: "+version);
        } finally {
            in.close();
        }
    }
}
