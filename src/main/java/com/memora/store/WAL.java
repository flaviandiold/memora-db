package com.memora.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.protobuf.InvalidProtocolBufferException;
import com.memora.core.Version;
import com.memora.exceptions.MemoraException;
import com.memora.messages.RpcRequest;

public class WAL {
    // Write-Ahead Log implementation
    private static final Map<Long, byte[]> wal = new ConcurrentHashMap<>();

    private WAL() {
    }

    public static void log(RpcRequest request) {
        long version = Version.increment();
        wal.put(version, request.toByteArray());
    }

    public static RpcRequest get(long version) {
        try {
            if (!wal.containsKey(version)) {
                throw new MemoraException("WAL entry not found for version " + version);
            }
            return RpcRequest.parseFrom(wal.get(version));
        } catch (InvalidProtocolBufferException e) {
            throw new MemoraException("Failed to parse WAL entry", e);
        }
    }

}