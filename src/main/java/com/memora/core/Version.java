package com.memora.core;

import java.util.concurrent.atomic.AtomicLong;

import com.google.inject.Inject;

public class Version {
    private AtomicLong version = new AtomicLong(1);
    private MemoraNode node;

    @Inject
    public Version(MemoraNode node) {
        this.node = node;
    }

    public void increment() {
        version.incrementAndGet();
        node.clearInSyncReplicas();
    }
    
    public long get() {
        return version.get();
    }
    
}
