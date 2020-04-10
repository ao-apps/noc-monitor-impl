/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2012, 2013, 2016, 2018, 2019, 2020  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of noc-monitor-impl.
 *
 * noc-monitor-impl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * noc-monitor-impl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with noc-monitor-impl.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.noc.monitor.infrastructure;

import com.aoindustries.io.stream.StreamableInput;
import com.aoindustries.io.stream.StreamableOutput;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.UpsResult;
import com.aoindustries.persistence.BufferedSerializer;
import com.aoindustries.sql.MilliInterval;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author  AO Industries, Inc.
 */
public class UpsResultSerializer extends BufferedSerializer<UpsResult> {

	private static final int VERSION = 2;

	private static void writeTimeSpan(MilliInterval value, DataOutput out) throws IOException {
		out.writeLong(value==null ? Long.MIN_VALUE : value.getIntervalMillis());
	}

	private static MilliInterval readTimeSpan(DataInput in) throws IOException {
		long value = in.readLong();
		return value==Long.MIN_VALUE ? null : new MilliInterval(value);
	}

	@Override
	protected void serialize(UpsResult value, ByteArrayOutputStream buffer) throws IOException {
		try (StreamableOutput out = new StreamableOutput(buffer)) {
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
				writeTimeSpan(value.getCumonbatt(), out);
				writeTimeSpan(value.getTimeleft(), out);
				out.writeFloat(value.getItemp());
			}
		}
	}

	@Override
	public UpsResult deserialize(InputStream rawIn) throws IOException {
		try (StreamableInput in = new StreamableInput(rawIn)) {
			int version = in.readCompressedInt();
			if(version==1 || version==2) {
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
					version==1 ? null : readTimeSpan(in), // cumonbatt added in version 2
					readTimeSpan(in),
					in.readFloat()
				);
			} else throw new IOException("Unsupported object version: "+version);
		}
	}
}
