/*
 * Copyright 2009, 2016 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.io.CompressedDataInputStream;
import com.aoindustries.io.CompressedDataOutputStream;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.NetDeviceBitRateResult;
import com.aoindustries.util.persistent.BufferedSerializer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author  AO Industries, Inc.
 */
public class NetDeviceBitRateResultSerializer extends BufferedSerializer<NetDeviceBitRateResult> {

	private static final int VERSION = 1;

	@Override
	protected void serialize(NetDeviceBitRateResult value, ByteArrayOutputStream buffer) throws IOException {
		try (CompressedDataOutputStream out = new CompressedDataOutputStream(buffer)) {
			out.writeCompressedInt(VERSION);
			out.writeLong(value.getTime());
			out.writeLong(value.getLatency());
			out.writeByte(value.getAlertLevel().ordinal());
			String error = value.getError();
			out.writeNullUTF(error);
			if(error==null) {
				out.writeLong(value.getTxBitsPerSecond());
				out.writeLong(value.getRxBitsPerSecond());
				out.writeLong(value.getTxPacketsPerSecond());
				out.writeLong(value.getRxPacketsPerSecond());
				out.writeLong(value.getBpsLow());
				out.writeLong(value.getBpsMedium());
				out.writeLong(value.getBpsHigh());
				out.writeLong(value.getBpsCritical());
			}
		}
	}

	@Override
	public NetDeviceBitRateResult deserialize(InputStream rawIn) throws IOException {
		try (CompressedDataInputStream in = new CompressedDataInputStream(rawIn)) {
			int version = in.readCompressedInt();
			if(version==1) {
				long time = in.readLong();
				long latency = in.readLong();
				AlertLevel alertLevel = AlertLevel.fromOrdinal(in.readByte());
				String error = in.readNullUTF();
				if(error!=null) return new NetDeviceBitRateResult(time, latency, alertLevel, error);
				return new NetDeviceBitRateResult(
					time,
					latency,
					alertLevel,
					in.readLong(),
					in.readLong(),
					in.readLong(),
					in.readLong(),
					in.readLong(),
					in.readLong(),
					in.readLong(),
					in.readLong()
				);
			} else throw new IOException("Unsupported object version: "+version);
		}
	}
}
