package com.memora.model;

import java.util.Objects;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Immutable class representing a cache entry.
 */
@Builder
@Data
@RequiredArgsConstructor
public final class CacheEntry {

    private final String value;
    private final long version;
    private final long ttl;
}
