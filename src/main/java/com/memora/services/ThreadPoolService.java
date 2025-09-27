package com.memora.services;

import com.memora.constants.ThreadPool;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ThreadPoolService {

    private final static ConcurrentHashMap<String, ExecutorService> threadPoolMap;

    static {
        threadPoolMap = new ConcurrentHashMap<>();
    }

    public static void createThreadPool(ThreadPool pool) {
        createThreadPool(pool.getName(), pool.getSize());
    }

    private static ExecutorService createThreadPool(String name, int size) {
        return threadPoolMap.put(name, Executors.newFixedThreadPool(size));
    }

    public static <T> Future<T> submit(String threadPoolName, Callable<T> task) {
        ExecutorService threadPool = threadPoolMap.get(threadPoolName);
        if (threadPool == null) {
            throw new IllegalStateException("Thread pool not found: " + threadPoolName);
        }
        return threadPool.submit(task);
    }

    public static void submit(String threadPoolName, Runnable task) {
        ExecutorService threadPool = threadPoolMap.get(threadPoolName);
        if (threadPool == null) {
            throw new IllegalStateException("Thread pool not found: " + threadPoolName);
        }
        threadPool.submit(task);
    }

    public static ExecutorService getThreadPool(ThreadPool pool) {
        return threadPoolMap.computeIfAbsent(pool.getName(), k -> createThreadPool(pool.getName(), pool.getSize()));
    }

    public static void shutdown() {
        for (ExecutorService threadPool : threadPoolMap.values()) {
            threadPool.shutdown();
        }
    }
}
