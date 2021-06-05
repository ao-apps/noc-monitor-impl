/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2009, 2016, 2018, 2019, 2020, 2021  AO Industries, Inc.
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

import com.aoapps.hodgepodge.io.stream.StreamableInput;
import com.aoapps.hodgepodge.io.stream.StreamableOutput;
import com.aoapps.persistence.BufferedSerializer;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.LoadAverageResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author  AO Industries, Inc.
 */
public class LoadAverageResultSerializer extends BufferedSerializer<LoadAverageResult> {

	private static final int VERSION = 1;

	@Override
	protected void serialize(LoadAverageResult value, ByteArrayOutputStream buffer) throws IOException {
		try (StreamableOutput out = new StreamableOutput(buffer)) {
			out.writeCompressedInt(VERSION);
			out.writeLong(value.getTime());
			out.writeLong(value.getLatency());
			out.writeByte(value.getAlertLevel().ordinal());
			String error = value.getError();
			out.writeNullUTF(error);
			if(error==null) {
				out.writeFloat(value.getOneMinute());
				out.writeFloat(value.getFiveMinute());
				out.writeFloat(value.getTenMinute());
				out.writeCompressedInt(value.getRunningProcesses());
				out.writeCompressedInt(value.getTotalProcesses());
				out.writeCompressedInt(value.getLastPID());
				out.writeFloat(value.getLoadLow());
				out.writeFloat(value.getLoadMedium());
				out.writeFloat(value.getLoadHigh());
				out.writeFloat(value.getLoadCritical());
			}
		}
	}

	@Override
	public LoadAverageResult deserialize(InputStream rawIn) throws IOException {
		try (StreamableInput in = new StreamableInput(rawIn)) {
			int version = in.readCompressedInt();
			if(version==1) {
				long time = in.readLong();
				long latency = in.readLong();
				AlertLevel alertLevel = AlertLevel.fromOrdinal(in.readByte());
				String error = in.readNullUTF();
				if(error!=null) return new LoadAverageResult(time, latency, alertLevel, error);
				return new LoadAverageResult(
					time,
					latency,
					alertLevel,
					in.readFloat(),
					in.readFloat(),
					in.readFloat(),
					in.readCompressedInt(),
					in.readCompressedInt(),
					in.readCompressedInt(),
					in.readFloat(),
					in.readFloat(),
					in.readFloat(),
					in.readFloat()
				);
			} else throw new IOException("Unsupported object version: "+version);
		}
	}
}
