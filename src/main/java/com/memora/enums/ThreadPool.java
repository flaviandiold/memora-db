package com.memora.enums;

import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ThreadPool {
    LOWER_THREAD_POOL("low-priority-thread", 2, false, true, Thread.MIN_PRIORITY),
    NORMAL_THREAD_POOL("memora-thread", 3, false, true, Thread.NORM_PRIORITY),
    HIGHER_THREAD_POOL("high-priority-thread", 5, false, true, Thread.MAX_PRIORITY),

    SERVER_THREAD_POOL("server-thread", 5, false, Thread.MAX_PRIORITY),
    CLIENT_THREAD_POOL("client-thread", 5, false, Thread.MAX_PRIORITY),
    EXECUTOR_THREAD_POOL("executor-thread", 7, true, Thread.MAX_PRIORITY),

    REPLICATION_THREAD_POOL("replication-thread", 3, Thread.MAX_PRIORITY);


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
