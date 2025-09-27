package com.memora.store;

import com.memora.model.CacheEntry;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Simple thread-safe in-memory key-value store.
 */
@Slf4j
public class Bucket {

    private final String bucketId;
    private final ConcurrentHashMap<String, CacheEntry> store;
    private final ConcurrentLinkedDeque<String> insertionOrder;
    private final CacheEntry nullEntry = CacheEntry.builder().value("null").ttl(-1).build();

    public Bucket(String bucketId, int capacity) {
        this.bucketId = bucketId;
        // Set initial capacity and load factor to prevent rehashes
        this.store = new ConcurrentHashMap<>();
        this.insertionOrder = new ConcurrentLinkedDeque<>();
    }

    public void put(String key, CacheEntry value) {
        if (store.put(key, value) == null) {
            // Only add to insertion queue if it's a new key
            insertionOrder.addLast(key);
        }
        log.info("Store {}", store);
    }
    
    public CacheEntry get(String key) {
        log.info("Store {}", store);
        CacheEntry entry = store.get(key);
        System.out.println("Bucket " + bucketId + ": Accessed key " + key + " with value " + entry);
        if (entry != null && entry.ttl() != -1 && System.currentTimeMillis() > entry.ttl()) {
            // Lazy eviction for expired keys
            store.remove(key);
            insertionOrder.remove(key); // This is an O(n) operation, can be slow
            return nullEntry;
        }
        if (entry == null) {
            return nullEntry;
        }
        return entry;
    }

    public void delete(String key) {
        store.remove(key);
        insertionOrder.remove(key);
    }

    private void evict() {
        String keyToEvict = insertionOrder.pollLast();
        if (keyToEvict != null) {
            store.remove(keyToEvict);
            System.out.println("Bucket " + bucketId + ": Evicted key " + keyToEvict);
        }
    }
}
