/*
 * Copyright 2009, 2016, 2018, 2019, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.mysql;

import com.aoindustries.io.stream.StreamableInput;
import com.aoindustries.io.stream.StreamableOutput;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.MySQLReplicationResult;
import com.aoindustries.persistence.BufferedSerializer;
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
