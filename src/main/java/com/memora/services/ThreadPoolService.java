package com.memora.services;

import com.memora.enums.ThreadPool;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
public class ThreadPoolService {

    private final ConcurrentHashMap<String, ExecutorService> threadPoolMap;

    public ThreadPoolService() {
        threadPoolMap = new ConcurrentHashMap<>();
    }

    public void createThreadPool(ThreadPool pool) {
        createThreadPool(pool.getName(), pool.getSize());
    }

    private ExecutorService createThreadPool(String name, int size) {
        return threadPoolMap.put(name, Executors.newFixedThreadPool(size));
    }

    public <T> Future<T> submit(ThreadPool pool, Callable<T> task) {
        ExecutorService threadPool = threadPoolMap.get(pool.getName());
        if (threadPool == null) {
            throw new IllegalStateException("Thread pool not found: " + pool.name());
        }
        return threadPool.submit(task);
    }
    
    public void submit(ThreadPool pool, Runnable task) {
        ExecutorService threadPool = threadPoolMap.get(pool.getName());
        if (threadPool == null) {
            throw new IllegalStateException("Thread pool not found: " + pool.name());
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
