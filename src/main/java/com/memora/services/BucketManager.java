package com.memora.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import com.google.inject.Inject;
import com.memora.model.BucketInfo;
import com.memora.model.BucketMap;
import com.memora.model.CacheEntry;
import com.memora.store.Bucket;
import com.memora.utils.Router;
import com.memora.utils.ULID;

public class BucketManager {

    private final BucketMap bucketMap; // Contains data of buckets of all nodes
    private final Map<String, Bucket> buckets; // Contains buckets of current node
    private final int numberOfBuckets;

    private final String nodeId;

    @Inject
    public BucketManager(
            String nodeId,
            int numberOfBuckets) {
        this.nodeId = nodeId;
        this.bucketMap = new BucketMap();
        this.buckets = new ConcurrentHashMap<>();
        this.numberOfBuckets = numberOfBuckets;
        addNewBuckets(numberOfBuckets);
    }

    public List<BucketInfo> getAllBuckets() {
        return bucketMap.getAllBuckets();
    }

    public List<Bucket> getSelfBuckets() {
        return List.copyOf(buckets.values());
    }

    public List<String> getSelfBucketIds() {
        return List.copyOf(buckets.keySet());
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
        int index = Router.getBucketIndex(key, bucketMap.getNumberOfActiveBuckets());
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
                (bucket, entriesForBucket) -> bucket.putAll(entriesForBucket));
    }

    public void delete(final String key) {
        Bucket bucket = getBucket(key);
        bucket.delete(key);
    }

    public void delete(final List<String> keys) {
        keys.forEach(this::delete);
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

    public void copyBucketIds(String nodeId, List<String> bucketIds) {
        copyBucketIds(nodeId, bucketIds, true);
    }

    public void copyBucketIds(String nodeId, List<String> bucketIds, boolean scale) {
        bucketMap.clearBucketsOf(nodeId);
        List<BucketInfo> bucketInfo = bucketIds.stream().map(bucketId -> {
            return BucketInfo.builder().bucketId(bucketId).nodeId(nodeId).build();
        }).toList();

        bucketMap.addBuckets(bucketInfo, scale);
    }

    public Map<String, List<String>> getKeyToNodeMap(List<String> keys) {
        Map<String, List<String>> keyToNodeMap = new HashMap<>();
        for (String key : keys) {
            BucketInfo bucketInfo = getBucketIdByKey(key);
            keyToNodeMap.computeIfAbsent(bucketInfo.getNodeId(), v -> new ArrayList<>()).add(key);
        }
        return keyToNodeMap;
    }

    public void scale(int newBucketCount) {
        bucketMap.scale(newBucketCount);
    }

    public List<CacheEntry> getMigratingKeys(String newNodeId, int newBucketCount) {
        // Calculate these counts *once* outside the stream for efficiency
        final int currentBucketCount = bucketMap.getNumberOfActiveBuckets();
        final int increasedBucketCount = currentBucketCount + newBucketCount;

        return buckets.values() // Get the Collection<Bucket>
                .parallelStream() // Process the buckets in parallel
                .filter(bucket -> !bucket.isEmpty()) // Ignore empty buckets
                .flatMap(bucket -> // Turn each bucket into a stream of *its* migrating keys

                    // Create a sequential stream for the *contents* of this single bucket
                    // The parallelism is already handled at the bucket level.
                    StreamSupport.stream(bucket.spliterator(), false)
                        .filter(cacheEntry -> {
                            int bucketIndex = Router.getBucketIndex(cacheEntry.getKey(), increasedBucketCount);
                            BucketInfo bucketInfo = getBucketInfo(bucketIndex);
                            if (bucketInfo == null) throw new IllegalStateException("Bucket Info not found for key: " + cacheEntry.getKey());
                            return bucketInfo.getNodeId().equals(newNodeId);
                        })
                )
                .collect(Collectors.toList()); // Collect all keys into a single list
    }

    public List<String> getSelfKeys() {
        return buckets.values() // Get the Collection<Bucket>
                .parallelStream() // Process the buckets in parallel
                .filter(bucket -> !bucket.isEmpty()) // Ignore empty buckets
                .flatMap(bucket -> // Turn each bucket into a stream of *its* migrating keys

                    // Create a sequential stream for the *contents* of this single bucket
                    // The parallelism is already handled at the bucket level.
                    StreamSupport.stream(bucket.spliterator(), false)
                        .map(CacheEntry::getKey)
                )
                .collect(Collectors.toList()); // Collect all keys into a single list
    }

    public void clear() {
        buckets.values().forEach(Bucket::clear);
        bucketMap.clearAll();
        addNewBuckets(numberOfBuckets);
    }
}
