package com.memora.model;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.Set;
import java.util.TreeSet;

import lombok.Data;

@Data
public final class BucketMap {

    private int numberOfActiveBuckets;
    private final ConcurrentHashMap<String, TreeSet<String>> nodeToBucketsMap = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<BucketInfo> bucketInfoList = new PriorityBlockingQueue<>(60, (a, b) -> {
        return a.getBucketId().compareTo(b.getBucketId());
    });
    private List<BucketInfo> allBuckets;

    public void increaseNumberOfActiveBuckets(int incrementBy) {
        numberOfActiveBuckets += incrementBy;
    }

    public void decreaseNumberOfActiveBuckets(int decrementBy) {
        numberOfActiveBuckets -= decrementBy;
    }

    public int getNumberOfActiveBuckets() {
        return numberOfActiveBuckets;
    }

    public boolean isBucketInNode(String nodeId, String bucketId) {
        return nodeToBucketsMap.containsKey(nodeId) && nodeToBucketsMap.get(nodeId).contains(bucketId);
    }

    public void addBuckets(List<BucketInfo> buckets) {
        buckets.forEach(bucket -> addBucket(bucket));
        increaseNumberOfActiveBuckets(buckets.size());
        allBuckets = List.copyOf(bucketInfoList);
    }

    private void addBucket(BucketInfo bucket) {
        nodeToBucketsMap.putIfAbsent(bucket.getNodeId(), new TreeSet<>());
        nodeToBucketsMap.get(bucket.getNodeId()).add(bucket.getBucketId());
        bucketInfoList.add(bucket);
    }

    public void removeBuckets(List<BucketInfo> buckets) {
        buckets.forEach(bucket -> removeBucket(bucket));
        decreaseNumberOfActiveBuckets(buckets.size());
    }

    private void removeBucket(BucketInfo bucket) {
        nodeToBucketsMap.get(bucket.getNodeId()).remove(bucket.getBucketId());
        bucketInfoList.remove(bucket);
    }

    public Set<String> getBucketsByNodeId(String nodeId) {
        return nodeToBucketsMap.get(nodeId);
    }

    public BucketInfo getBucketInfo(int index) {
        return allBuckets.get(index);
    }
}
