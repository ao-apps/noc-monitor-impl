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
 * along with noc-monitor-impl.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.aoindustries.noc.monitor.mysql;

import com.aoapps.hodgepodge.io.stream.StreamableInput;
import com.aoapps.hodgepodge.io.stream.StreamableOutput;
import com.aoapps.persistence.BufferedSerializer;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.MySQLReplicationResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author  AO Industries, Inc.
 */
public class ReplicationResultSerializer extends BufferedSerializer<MySQLReplicationResult> {

	private static final int VERSION = 1;

	@Override
	protected void serialize(MySQLReplicationResult value, ByteArrayOutputStream buffer) throws IOException {
		try (StreamableOutput out = new StreamableOutput(buffer)) {
			out.writeCompressedInt(VERSION);
			out.writeLong(value.getTime());
			out.writeLong(value.getLatency());
			out.writeByte(value.getAlertLevel().ordinal());
			String error = value.getError();
			out.writeNullUTF(error);
			if(error==null) {
				out.writeNullUTF(value.getSecondsBehindMaster());
				out.writeNullUTF(value.getFile());
				out.writeNullUTF(value.getPosition());
				out.writeNullUTF(value.getSlaveIOState());
				out.writeNullUTF(value.getMasterLogFile());
				out.writeNullUTF(value.getReadMasterLogPos());
				out.writeNullUTF(value.getSlaveIORunning());
				out.writeNullUTF(value.getSlaveSQLRunning());
				out.writeNullUTF(value.getLastErrno());
				out.writeNullUTF(value.getLastError());
				out.writeNullUTF(value.getAlertThresholds());
			}
		}
	}

	@Override
	public MySQLReplicationResult deserialize(InputStream rawIn) throws IOException {
		try (StreamableInput in = new StreamableInput(rawIn)) {
			int version = in.readCompressedInt();
			if(version==1) {
				long time = in.readLong();
				long latency = in.readLong();
				AlertLevel alertLevel = AlertLevel.fromOrdinal(in.readByte());
				String error = in.readNullUTF();
				if(error!=null) return new MySQLReplicationResult(time, latency, alertLevel, error);
				return new MySQLReplicationResult(
					time,
					latency,
					alertLevel,
					in.readNullUTF(),
					in.readNullUTF(),
					in.readNullUTF(),
					in.readNullUTF(),
					in.readNullUTF(),
					in.readNullUTF(),
					in.readNullUTF(),
					in.readNullUTF(),
					in.readNullUTF(),
					in.readNullUTF(),
					in.readNullUTF()
				);
			} else throw new IOException("Unsupported object version: "+version);
		}
	}
}
