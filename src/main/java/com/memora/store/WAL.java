package com.memora.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.memora.core.Version;
import com.memora.model.RpcRequest;

public class WAL {
    // Write-Ahead Log implementation
    private static final Map<Long, String> wal = new ConcurrentHashMap<>();

    private WAL() {
    }

    public static void log(RpcRequest request) {
        long version = Version.increment();
        wal.put(version, request.getCommand());
    }

    public static String get(long version) {
        return wal.get(version);
    }

}