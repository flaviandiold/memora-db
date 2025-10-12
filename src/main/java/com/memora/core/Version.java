package com.memora.core;

import java.util.concurrent.atomic.AtomicLong;

public final class Version {
    private final static AtomicLong version = new AtomicLong(1);

    private Version() {}

    public static Long increment() {
        return version.incrementAndGet();
    }
    
    public static long get() {
        return version.get();
    }
    
}
