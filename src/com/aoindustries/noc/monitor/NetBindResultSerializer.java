/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.io.CompressedDataInputStream;
import com.aoindustries.io.CompressedDataOutputStream;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.NetBindResult;
import com.aoindustries.util.persistent.BufferedSerializer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author  AO Industries, Inc.
 */
public class NetBindResultSerializer extends BufferedSerializer<NetBindResult> {

    private static final Logger logger = Logger.getLogger(NetBindResultSerializer.class.getName());

    private static final int VERSION = 2;

    private static final String[] commonResults = {
        "Connected successfully",
        "SSH-2.0-OpenSSH_4.3",
        "SSH-1.99-OpenSSH_4.3",
        "SSH-1.99-OpenSSH_3.9p1",
        "1",
        "Login successful.",
        "User logged in",
        "Mailbox locked and ready",
        "[IN-USE] Unable to lock maildrop: Mailbox is locked by POP server"
    };
    private static final Map<String,Integer> commonResultsMap = new HashMap<String,Integer>(commonResults.length*4/3+1);
    static {
        for(int c=0;c<commonResults.length;c++) commonResultsMap.put(commonResults[c], c);
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

    private static final ConcurrentMap<String,Boolean> commonResultsSuggested = new ConcurrentHashMap<String,Boolean>();

    @Override
    protected void serialize(NetBindResult value, ByteArrayOutputStream buffer) throws IOException {
        CompressedDataOutputStream out = new CompressedDataOutputStream(buffer);
        try {
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
                            if(logger.isLoggable(Level.INFO) && commonResultsSuggested.putIfAbsent(result, Boolean.TRUE)==null) {
                                logger.info("Suggested value for commonResultsMap: \""+result+"\"");
                            }
                            out.writeByte(RAW);
                            out.writeUTF(result);
                        }
                    }
                }
            }
        } finally {
            out.close();
        }
    }

    @Override
    public NetBindResult deserialize(InputStream rawIn) throws IOException {
        CompressedDataInputStream in = new CompressedDataInputStream(rawIn);
        try {
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
        } finally {
            in.close();
        }
    }
}
