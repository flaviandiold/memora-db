package com.memora.enums;

import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ThreadPool {
    GENERAL_THREAD_POOL("general-thread", 5, false, true, Thread.MIN_PRIORITY),
    SERVER_THREAD_POOL("server-thread", 7, false, Thread.MAX_PRIORITY),
    GOSSIP_THREAD_POOL("gossip-thread", 5),
    CLIENT_THREAD_POOL("client-thread", 7, true, Thread.MAX_PRIORITY),
    REPLICATION_THREAD_POOL("replication-thread", 5, Thread.MAX_PRIORITY);


    private final String threadName;
    private final int size;
    private final boolean isCluster;
    private final boolean isDaemon;
    private final int priority;

    ThreadPool(String threadName, int size) {
        this(threadName, size, true, false, Thread.NORM_PRIORITY);
    }

    ThreadPool(String threadName, int size, boolean isCluster, int priority) {
        this(threadName, size, isCluster, false, priority);
    }

    ThreadPool(String threadName, int size, int priority) {
        this(threadName, size, true, false, priority);
    }

    public static List<ThreadPool> getAllThreadPool() {
        return List.of(ThreadPool.values());
    }
}
