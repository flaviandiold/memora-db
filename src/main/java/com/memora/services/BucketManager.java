package com.memora.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.stream.IntStream;

import com.google.inject.Inject;
import com.memora.model.BucketInfo;
import com.memora.model.BucketMap;
import com.memora.model.CacheEntry;
import com.memora.store.Bucket;
import com.memora.utils.ULID;

public class BucketManager {

    private final BucketMap bucketMap; // Contains data of buckets of all nodes
    private final Map<String, Bucket> buckets; // Contains buckets of current node

    private final String nodeId;
    private final RoutingService routingService;

    @Inject
    public BucketManager(
        String nodeId,
        int numberOfBuckets,
        RoutingService routingService
    ) {
        this.nodeId = nodeId;
        this.bucketMap = new BucketMap();
        this.buckets = new HashMap<>();
        this.routingService = routingService;
        addNewBuckets(numberOfBuckets);
    }

    public List<BucketInfo> getAllBuckets() {
        return bucketMap.getAllBuckets();
    }

    public List<Bucket> getSelfBuckets() {
        return List.copyOf(buckets.values());
    }

    public boolean isKeyInSelf(String key) {
        return bucketMap.isBucketInNode(nodeId, getBucketIdByKey(key).getBucketId());
    }

    public Bucket getBucket(String key) {
        String bucketId = getBucketIdByKey(key).getBucketId();
        Bucket bucket = buckets.get(bucketId);
        if (Objects.isNull(bucket)) {
            throw new IllegalStateException("Bucket not found for key: " + key);
        }
        return bucket;
    }

    private BucketInfo getBucketIdByKey(String key) {
        int index = routingService.getBucketIndex(key, bucketMap.getNumberOfActiveBuckets());
        return getBucketInfo(index);
    }

    private BucketInfo getBucketInfo(int index) {
        return bucketMap.getBucketInfo(index);
    }

    private void addNewBuckets(int numberOfBuckets) {
        List<BucketInfo> bucketInfo = new ArrayList<>();
        IntStream.range(0, numberOfBuckets).forEach(i -> {
            String bucketId = ULID.generate();
            addBucket(bucketId);
            bucketInfo.add(BucketInfo.builder().bucketId(bucketId).nodeId(nodeId).build());
        });
        bucketMap.addBuckets(bucketInfo);
    }

    public CacheEntry get(final String key) {
        Bucket bucket = getBucket(key);
        return bucket.get(key);
    }

    public void put(final CacheEntry entry) {
        Bucket bucket = getBucket(entry.getKey());
        bucket.put(entry);
    }

    public void putAll(final Collection<CacheEntry> entries) {
        Map<Bucket, List<CacheEntry>> entriesOrderedByBuckets = new HashMap<>();
        for (CacheEntry entry : entries) {
            String key = entry.getKey();
            Bucket bucket = getBucket(key);
            entriesOrderedByBuckets.computeIfAbsent(bucket, k -> new ArrayList<>()).add(entry);
        }
        entriesOrderedByBuckets.forEach(
            (bucket, entriesForBucket) -> bucket.putAll(entriesForBucket)
        );
    }

    public void delete(final String key) {
        Bucket bucket = getBucket(key);
        bucket.delete(key);
    }

    private void addBucket(String bucketId) {
        buckets.putIfAbsent(bucketId, new Bucket(bucketId));
    }

    public void createFromPrimary(List<BucketInfo> primaryBucketInfo) {
        buckets.clear();
        bucketMap.clearBucketsOf(nodeId);
        bucketMap.addBuckets(primaryBucketInfo);
        primaryBucketInfo.forEach(bucketInfo -> {
            String bucketId = bucketInfo.getBucketId();
            addBucket(bucketId);
        });
    }

    public Map<String, List<String>> getKeyToNodeMap(List<String> keys) {
        Map<String, List<String>> keyToNodeMap = new HashMap<>();
        for (String key: keys) {
            BucketInfo bucketInfo = getBucketIdByKey(key);
            keyToNodeMap.computeIfAbsent(bucketInfo.getNodeId(), v -> new ArrayList<>()).add(key);
        }
        return keyToNodeMap;
    }
}
