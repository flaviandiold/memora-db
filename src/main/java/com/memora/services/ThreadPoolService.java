package com.memora.services;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.memora.enums.ThreadPool;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ThreadPoolService {

    private final ConcurrentHashMap<String, ExecutorService> threadPoolMap;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ThreadPoolService() {
        threadPoolMap = new ConcurrentHashMap<>();
    }

    public ExecutorService createThreadPool(ThreadPool pool) {
        String name = pool.getThreadName();
        int size = pool.getSize();

        if (threadPoolMap.containsKey(name)) {
            log.warn("Thread pool with name {} already exists. Skipping creation.", name);
            return threadPoolMap.get(name);
        }
        log.info("Creating thread pool with name {} and size {}", name, size);
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setDaemon(pool.isDaemon())
                .setPriority(pool.getPriority())
                .setNameFormat("memora-" + name + "-%d")
                .build();

        return threadPoolMap.put(name, Executors.newFixedThreadPool(size, threadFactory));
    }

    public <T> Future<T> submit(ThreadPool pool, Callable<T> task) {
        ExecutorService threadPool = threadPoolMap.get(pool.getThreadName());
        if (threadPool == null) {
            throw new IllegalStateException("Thread pool not found: " + pool.name());
        }
        return threadPool.submit(task);
    }
    
    public void submit(ThreadPool pool, Runnable task) {
        ExecutorService threadPool = threadPoolMap.get(pool.getThreadName());
        if (threadPool == null) {
            throw new IllegalStateException("Thread pool not found: " + pool.name());
        }
        threadPool.submit(task);
    }

    public void submitAfter(ThreadPool pool, Runnable task, long delaySeconds) {
        ExecutorService threadPool = threadPoolMap.get(pool.getThreadName());
        if (threadPool == null) {
            throw new IllegalStateException("Thread pool not found: " + pool.name());
        }

        scheduler.schedule(() -> threadPool.submit(task), delaySeconds, TimeUnit.SECONDS);
    }

    public void submitEvery(ThreadPool pool, Runnable task, long recurringSeconds) {
        ExecutorService threadPool = threadPoolMap.get(pool.getThreadName());
        if (threadPool == null) {
            throw new IllegalStateException("Thread pool not found: " + pool.name());
        }

        scheduler.scheduleAtFixedRate(() ->
            threadPool.submit(task), recurringSeconds, recurringSeconds, TimeUnit.SECONDS);
    }

    public ExecutorService getThreadPool(ThreadPool pool) {
        return threadPoolMap.computeIfAbsent(pool.getThreadName(), k -> createThreadPool(pool));
    }

    public void shutdown() {
        for (ExecutorService threadPool : threadPoolMap.values()) {
            threadPool.shutdown();
        }
    }
}
