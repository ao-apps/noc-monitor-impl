/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2009, 2014, 2017, 2018, 2019, 2020, 2021  AO Industries, Inc.
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
package com.aoindustries.noc.monitor.net;

import com.aoapps.collections.AoCollections;
import com.aoapps.hodgepodge.io.stream.StreamableInput;
import com.aoapps.hodgepodge.io.stream.StreamableOutput;
import com.aoapps.persistence.BufferedSerializer;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.NetBindResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author  AO Industries, Inc.
 */
public class BindResultSerializer extends BufferedSerializer<NetBindResult> {

	private static final Logger logger = Logger.getLogger(BindResultSerializer.class.getName());

	private static final int VERSION = 2;

	/**
	 * Commonly used strings are written as an index into this array.
	 * Only add new items to the end of this array.
	 * If items must be removed, then VERSION must be incremented and code must perform necessary adjustments when reading objects.
	 */
	private static final String[] commonResults = {
		"Connected successfully",
		"SSH-2.0-OpenSSH_4.3",
		"SSH-1.99-OpenSSH_4.3",
		"SSH-1.99-OpenSSH_3.9p1",
		"1",
		"Login successful.",
		"User logged in",
		"Mailbox locked and ready",
		"[IN-USE] Unable to lock maildrop: Mailbox is locked by POP server",
		"SSH-2.0-OpenSSH_5.3",
		"SSH-2.0-OpenSSH_3.8.1p1 Debian-8.sarge.6",
		"SSH-2.0-dropbear_2012.55",
		"SSH-2.0-OpenSSH_6.6.1",
		"SSH-2.0-dropbear_2013.60",
		"SSH-2.0-OpenSSH_7.4",
		"Connected successfully (SSL disabled)",
		"Connected successfully over SSL"
	};
	private static final Map<String, Integer> commonResultsMap = AoCollections.newHashMap(commonResults.length);
	static {
		for(int c = 0; c < commonResults.length; c++) {
			commonResultsMap.put(commonResults[c], c);
		}
	}

	private static final String MESSAGE_ACCEPTED_SUFFIX = " Message accepted for delivery";

	/**
	 * The different encoding types.
	 */
	private static final byte
		ERROR = 0,
		NULL_RESULT = 1,
		COMMON_RESULTS = 2,
		MESSAGE_ACCEPTED = 3,
		RAW = 4
	;

	private static final ConcurrentMap<String, Boolean> commonResultsSuggested = new ConcurrentHashMap<>();

	@Override
	protected void serialize(NetBindResult value, ByteArrayOutputStream buffer) throws IOException {
		try (StreamableOutput out = new StreamableOutput(buffer)) {
			out.writeCompressedInt(VERSION);
			out.writeLong(value.getTime());
			out.writeLong(value.getLatency());
			out.writeByte(value.getAlertLevel().ordinal());
			String error = value.getError();
			if(error!=null) {
				out.writeByte(ERROR);
				out.writeUTF(error);
			} else {
				String result = value.getResult();
				if(result==null) {
					out.writeByte(NULL_RESULT);
				} else {
					Integer index = commonResultsMap.get(result);
					if(index!=null) {
						out.writeByte(COMMON_RESULTS);
						out.writeCompressedInt(index);
					} else {
						if(result.endsWith(MESSAGE_ACCEPTED_SUFFIX)) {
							out.writeByte(MESSAGE_ACCEPTED);
							out.writeUTF(result.substring(0, result.length()-MESSAGE_ACCEPTED_SUFFIX.length()));
						} else {
							if(
								logger.isLoggable(Level.INFO)
								&& !result.startsWith("User logged in SESSIONID=")
								&& !result.startsWith("Mailbox locked and ready SESSIONID=")
								&& commonResultsSuggested.putIfAbsent(result, Boolean.TRUE) == null
							) {
								logger.info("Suggested value for commonResultsMap: \""+result+"\"");
							}
							out.writeByte(RAW);
							out.writeUTF(result);
						}
					}
				}
			}
		}
	}

	@Override
	public NetBindResult deserialize(InputStream rawIn) throws IOException {
		try (StreamableInput in = new StreamableInput(rawIn)) {
			int version = in.readCompressedInt();
			if(version==2) {
				long time = in.readLong();
				long latency = in.readLong();
				AlertLevel alertLevel = AlertLevel.fromOrdinal(in.readByte());
				byte encodingType = in.readByte();
				switch(encodingType) {
					case ERROR : return new NetBindResult(time, latency, alertLevel, in.readUTF(), null);
					case NULL_RESULT : return new NetBindResult(time, latency, alertLevel, null, null);
					case COMMON_RESULTS : return new NetBindResult(time, latency, alertLevel, null, commonResults[in.readCompressedInt()]);
					case MESSAGE_ACCEPTED : return new NetBindResult(time, latency, alertLevel, null, in.readUTF()+MESSAGE_ACCEPTED_SUFFIX);
					case RAW : return new NetBindResult(time, latency, alertLevel, null, in.readUTF());
					default : throw new IOException("Unexpected value for encodingType: "+encodingType);
				}
			} else if(version==1) {
				long time = in.readLong();
				long latency = in.readLong();
				AlertLevel alertLevel = AlertLevel.fromOrdinal(in.readByte());
				String error = in.readNullUTF();
				if(error!=null) return new NetBindResult(time, latency, alertLevel, error, null);
				return new NetBindResult(
					time,
					latency,
					alertLevel,
					null,
					in.readNullUTF()
				);
			} else {
				throw new IOException("Unsupported object version: "+version);
			}
		}
	}
}
