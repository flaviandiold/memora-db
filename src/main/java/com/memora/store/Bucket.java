package com.memora.store;

import java.util.Map;

import com.memora.model.CacheEntry;
import com.memora.utils.InsertionOrderMap;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import com.memora.core.MemoraClient;

/**
 * Simple thread-safe in-memory key-value store.
 */
@Slf4j
public class Bucket {

    private final String bucketId;
    private final ConcurrentHashMap<String, CacheEntry> store;
    private final InsertionOrderMap<String> insertionOrder;

    public Bucket(String bucketId) {
        this.bucketId = bucketId;
        // Set initial capacity and load factor to prevent rehashes
        this.store = new ConcurrentHashMap<>(1000, 0.8f);
        this.insertionOrder = new InsertionOrderMap<>();
    }

    public String getId() {
        return bucketId;
    }

    public void put(final String key, final CacheEntry value) {
        if (value.getTtl() != -1 && System.currentTimeMillis() > value.getTtl()) {
            return;
        }
        store.compute(key, (k, v) -> {
            insertionOrder.put(key);
            return value;
        });
    }

    public void putAll(final Map<String, CacheEntry> entries) {
        store.putAll(entries);
        insertionOrder.putAll(entries.keySet());
    }

    public CacheEntry get(String key) {
        return store.compute(key, (k, v) -> {
            if (v != null && v.getTtl() != -1 && System.currentTimeMillis() > v.getTtl()) {
                // Lazy eviction for expired keys
                insertionOrder.remove(key);
                return null;
            }
            return v;
        });
    }

    public void delete(String key) {
        store.remove(key);
        insertionOrder.remove(key);
    }

    public boolean stream(final MemoraClient client, final ExecutorService executor) {
        return client.put(store, executor);
    }

    private void evict() {
        String keyToEvict = insertionOrder.getMostRecentKey();
        if (keyToEvict != null) {
            store.remove(keyToEvict);
        }
    }
}
