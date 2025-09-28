package com.memora.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import com.google.inject.Inject;
import com.memora.model.BucketInfo;
import com.memora.model.BucketMap;
import com.memora.model.CacheEntry;
import com.memora.store.Bucket;
import com.memora.utils.ULID;

public class BucketManager {

    // Must coordinate with routing service as well
    private final BucketMap bucketMap;
    private final Map<String, Bucket> buckets;
    private final String nodeId;

    private final RoutingService routingService;

    @Inject
    public BucketManager(String nodeId, int numberOfBuckets, RoutingService routingService) {
        this.nodeId = nodeId;
        this.bucketMap = new BucketMap();
        this.buckets = new HashMap<>();
        this.routingService = routingService;
        addNewBuckets(numberOfBuckets);
    }

    public boolean isInSelf(String key) {
        return bucketMap.isBucketInNode(nodeId, getBucketIdByKey(key));
    }

    public Bucket getBucket(String key) {
        String bucketId = getBucketIdByKey(key);
        Bucket bucket = buckets.get(bucketId);
        if (bucket == null) {
            throw new IllegalStateException("Bucket not found for key: " + key);
        }
        return bucket;
    }

    private String getBucketIdByKey(String key) {
        int index = routingService.getBucketIndex(key, bucketMap.getNumberOfActiveBuckets());
        return bucketMap.getBucketInfo(index).getBucketId();
    }

    private void addNewBuckets(int numberOfBuckets) {
        List<BucketInfo> bucketInfo = new ArrayList<>();
        IntStream.range(0, numberOfBuckets).forEach(i -> {
            String bucketId = ULID.generate();
            Bucket bucket = new Bucket(bucketId);
            buckets.put(bucketId, bucket);
            bucketInfo.add(BucketInfo.builder().bucketId(bucketId).nodeId(nodeId).build());
        });
        bucketMap.addBuckets(bucketInfo);
    }

    public CacheEntry get(final String key) {
        Bucket bucket = getBucket(key);
        return bucket.get(key);
    }

    public void put(final String key, final CacheEntry value) {
        Bucket bucket = getBucket(key);
        bucket.put(key, value);
    }

    public void delete(final String key) {
        Bucket bucket = getBucket(key);
        bucket.delete(key);
    }
}