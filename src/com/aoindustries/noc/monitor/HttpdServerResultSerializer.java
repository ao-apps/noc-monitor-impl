/*
 * Copyright 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.io.CompressedDataInputStream;
import com.aoindustries.io.CompressedDataOutputStream;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.HttpdServerResult;
import com.aoindustries.util.persistent.BufferedSerializer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author  AO Industries, Inc.
 */
public class HttpdServerResultSerializer extends BufferedSerializer<HttpdServerResult> {

	private static final int VERSION = 1;

	@Override
	protected void serialize(HttpdServerResult value, ByteArrayOutputStream buffer) throws IOException {
		try (CompressedDataOutputStream out = new CompressedDataOutputStream(buffer)) {
			out.writeCompressedInt(VERSION);
			out.writeLong(value.getTime());
			out.writeLong(value.getLatency());
			out.writeByte(value.getAlertLevel().ordinal());
			String error = value.getError();
			out.writeNullUTF(error);
			if(error == null) {
				out.writeCompressedInt(value.getConcurrency());
				out.writeCompressedInt(value.getMaxConcurrency());
				out.writeCompressedInt(value.getConcurrencyLow());
				out.writeCompressedInt(value.getConcurrencyMedium());
				out.writeCompressedInt(value.getConcurrencyHigh());
				out.writeCompressedInt(value.getConcurrencyCritical());
			}
		}
	}

	@Override
	public HttpdServerResult deserialize(InputStream rawIn) throws IOException {
		try (CompressedDataInputStream in = new CompressedDataInputStream(rawIn)) {
			int version = in.readCompressedInt();
			if(version == 1) {
				long time = in.readLong();
				long latency = in.readLong();
				AlertLevel alertLevel = AlertLevel.fromOrdinal(in.readByte());
				String error = in.readNullUTF();
				if(error != null) return new HttpdServerResult(time, latency, alertLevel, error);
				return new HttpdServerResult(
					time,
					latency,
					alertLevel,
					in.readCompressedInt(),
					in.readCompressedInt(),
					in.readCompressedInt(),
					in.readCompressedInt(),
					in.readCompressedInt(),
					in.readCompressedInt()
				);
			} else throw new IOException("Unsupported object version: " + version);
		}
	}
}
