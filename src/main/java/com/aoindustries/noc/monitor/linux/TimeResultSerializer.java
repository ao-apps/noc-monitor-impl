/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2009, 2016, 2018, 2019, 2020  AO Industries, Inc.
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
package com.aoindustries.noc.monitor.linux;

import com.aoindustries.io.stream.StreamableInput;
import com.aoindustries.io.stream.StreamableOutput;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TimeResult;
import com.aoindustries.persistence.BufferedSerializer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author  AO Industries, Inc.
 */
public class TimeResultSerializer extends BufferedSerializer<TimeResult> {

	private static final int VERSION = 1;

	@Override
	protected void serialize(TimeResult value, ByteArrayOutputStream buffer) throws IOException {
		try (StreamableOutput out = new StreamableOutput(buffer)) {
			out.writeCompressedInt(VERSION);
			out.writeLong(value.getTime());
			out.writeLong(value.getLatency());
			out.writeByte(value.getAlertLevel().ordinal());
			String error = value.getError();
			out.writeNullUTF(error);
			if(error==null) {
				out.writeLong(value.getSkew());
			}
		}
	}

	@Override
	public TimeResult deserialize(InputStream rawIn) throws IOException {
		try (StreamableInput in = new StreamableInput(rawIn)) {
			int version = in.readCompressedInt();
			if(version==1) {
				long time = in.readLong();
				long latency = in.readLong();
				AlertLevel alertLevel = AlertLevel.fromOrdinal(in.readByte());
				String error = in.readNullUTF();
				if(error!=null) return new TimeResult(time, latency, alertLevel, error);
				return new TimeResult(
					time,
					latency,
					alertLevel,
					in.readLong()
				);
			} else throw new IOException("Unsupported object version: "+version);
		}
	}
}
