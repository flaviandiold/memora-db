package com.memora.services;

import com.google.inject.Inject;
import com.memora.enums.ThreadPool;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ThreadPoolService {

    private final ConcurrentHashMap<String, ExecutorService> threadPoolMap;

    @Inject
    public ThreadPoolService() {
        threadPoolMap = new ConcurrentHashMap<>();
    }

    public void createThreadPool(ThreadPool pool) {
        createThreadPool(pool.getName(), pool.getSize());
    }

    private ExecutorService createThreadPool(String name, int size) {
        return threadPoolMap.put(name, Executors.newFixedThreadPool(size));
    }

    public <T> Future<T> submit(String threadPoolName, Callable<T> task) {
        ExecutorService threadPool = threadPoolMap.get(threadPoolName);
        if (threadPool == null) {
            throw new IllegalStateException("Thread pool not found: " + threadPoolName);
        }
        return threadPool.submit(task);
    }

    public void submit(String threadPoolName, Runnable task) {
        ExecutorService threadPool = threadPoolMap.get(threadPoolName);
        if (threadPool == null) {
            throw new IllegalStateException("Thread pool not found: " + threadPoolName);
        }
        threadPool.submit(task);
    }

    public ExecutorService getThreadPool(ThreadPool pool) {
        return threadPoolMap.computeIfAbsent(pool.getName(), k -> createThreadPool(pool.getName(), pool.getSize()));
    }

    public void shutdown() {
        for (ExecutorService threadPool : threadPoolMap.values()) {
            threadPool.shutdown();
        }
    }
}
