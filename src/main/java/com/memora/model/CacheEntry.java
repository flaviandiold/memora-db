package com.memora.model;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Immutable class representing a cache entry.
 */
@Builder
@Data
@RequiredArgsConstructor
public class CacheEntry {

    @NonNull private final String key;
    @NonNull private final String value;
    private final long ttl;

    public boolean isExpired() {
        return this.getTtl() != -1 && System.currentTimeMillis() > this.getTtl();
    }
}
