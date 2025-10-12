package com.memora.utils;

import java.util.concurrent.atomic.AtomicInteger;

import com.memora.enums.ThreadPool;
import com.memora.services.ThreadPoolService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class QPS {

    private static QPS INSTANCE;

    private final AtomicInteger qps = new AtomicInteger(0);
    private volatile int lastSecondQps = 0;
    private volatile int maxQps = 0;
    
    private final ThreadPoolService threadPoolService;

    public QPS (ThreadPoolService threadPoolService) {
        this.threadPoolService = threadPoolService;
    }
    
    public void initialize() {
        INSTANCE = this;
        threadPoolService.submitEvery(ThreadPool.GENERAL_THREAD_POOL, QPS.INSTANCE::rotate, 1);
    }

    public static QPS getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("QPS not initialized. Call initialize() first.");
        }
        return INSTANCE;
    }

    private void rotate() {
        lastSecondQps = qps.getAndSet(0);
        maxQps = Math.max(maxQps, lastSecondQps);
        log.debug("QPS: {}", lastSecondQps); // Log the stable QPS value here
    }

    public void increase() {
        qps.incrementAndGet();
    }

    public int get() {
        return lastSecondQps;
    }

    public int getMax() {
        return maxQps;
    }
}
