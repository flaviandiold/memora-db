package com.memora.model;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Immutable class representing a cache entry.
 */
@Builder
@Data
@RequiredArgsConstructor
public class CacheEntry {

    private final String value;
    private final long ttl;
}
