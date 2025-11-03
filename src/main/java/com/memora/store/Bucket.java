package com.memora.store;

import java.util.Iterator;
import java.util.List;

import com.memora.model.CacheEntry;
import com.memora.utils.InsertionOrderMap;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import com.memora.core.MemoraClient;

/**
 * Simple thread-safe in-memory key-value store.
 */
@Slf4j
public class Bucket implements Iterable<CacheEntry> {

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

    public void put(final CacheEntry entry) {
        if (entry.isExpired()) return;

        try {
            store.compute(entry.getKey(), (k, v) -> {
                insertionOrder.put(entry.getKey());
                return entry;
            });
        } catch (OutOfMemoryError oMemoryError) {
            this.evict();
            put(entry);
        }
    }

    public void putAll(final List<CacheEntry> entries) {
        entries.forEach(this::put);
    }

    public CacheEntry get(String key) {
        return store.compute(key, (k, v) -> {
            if (v != null && v.isExpired()) {
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

    public CompletableFuture<Boolean> stream(final MemoraClient client, final ExecutorService executor) {
        return client.putAsync(store.values(), executor);
    }

    public void clear() {
        store.clear();
        insertionOrder.clear();
    }

    public boolean isEmpty() {
        return store.isEmpty();
    }

    public Iterator<String> getKeys() {
        return store.keySet().iterator();
    }
    
    @Override
    public Iterator<CacheEntry> iterator() {
        return store.values().iterator();
    }

    private void evict() {
        String keyToEvict = insertionOrder.getMostRecentKey();
        if (keyToEvict != null) {
            store.remove(keyToEvict);
            insertionOrder.remove(keyToEvict);
        }
    }
}
