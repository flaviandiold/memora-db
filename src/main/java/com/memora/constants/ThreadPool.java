package com.memora.constants;

import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ThreadPool {
    GOSSIP_THREAD_POOL("gossip-thread-pool", 100, true),
    CLIENT_THREAD_POOL("client-thread-pool", 100, true),
    REPLICATION_THREAD_POOL("replication-thread-pool", 100, true),
    SERVER_THREAD_POOL("server-thread-pool", 100, false);

    private final String name;
    private final int size;
    private final boolean isCluster;

    public static List<ThreadPool> getThreadPool() {
        return List.of(ThreadPool.values());
    }
}
