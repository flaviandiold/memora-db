package com.memora.model;

import java.util.Objects;

import lombok.Builder;

/**
 * Immutable class representing a cache entry.
 */
@Builder
public final class CacheEntry {

    private final String value;
    private final long version;
    private final long ttl;

    public CacheEntry(String value, long version, long ttl) {
        this.value = Objects.requireNonNull(value, "value cannot be null");
        this.version = version;
        this.ttl = ttl;
    }

    public CacheEntry(String value, long version) {
        this(value, version, 0);
    }

    public String value() {
        return value;
    }

    public long version() {
        return version;
    }

    public long ttl() {
        return ttl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CacheEntry)) return false;
        CacheEntry that = (CacheEntry) o;
        return version == that.version &&
               ttl == that.ttl &&
               Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, version, ttl);
    }

    @Override
    public String toString() {
        return String.format("CacheEntry[value=%s, version=%d, ttl=%d]", value, version, ttl);
    }
}
