package com.memora.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.memora.model.RpcRequest;

public class WAL {
    // Write-Ahead Log implementation
    private static final Map<Long, String> wal = new ConcurrentHashMap<>();

    private WAL() {
    }

    public static void log(RpcRequest request) {
        wal.put(request.version(), request.command());
    }

    public static String get(long version) {
        return wal.get(version);
    }

}