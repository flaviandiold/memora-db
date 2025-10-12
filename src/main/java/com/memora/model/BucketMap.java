package com.memora.model;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.Set;
import java.util.TreeSet;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class BucketMap {

    private int numberOfActiveBuckets;
    private List<BucketInfo> allBuckets;
    private final ConcurrentHashMap<String, TreeSet<String>> nodeToBucketsMap;
    private final PriorityBlockingQueue<BucketInfo> bucketInfoList;

    public BucketMap() {
        this.numberOfActiveBuckets = 0;
        this.allBuckets = List.of();
        nodeToBucketsMap = new ConcurrentHashMap<>();
        bucketInfoList = new PriorityBlockingQueue<>(60, (a, b) -> {
            return a.getBucketId().compareTo(b.getBucketId());
        });
    }

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
        makeAllBuckets();
    }

    private void addBucket(BucketInfo bucket) {
        nodeToBucketsMap.computeIfAbsent(bucket.getNodeId(), k -> new TreeSet<>()).add(bucket.getBucketId());
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

    public List<BucketInfo> getAllBuckets() {
        return allBuckets;
    }

    public void clearBucketsOf(String nodeId) {
        int size = nodeToBucketsMap.get(nodeId).size();
        nodeToBucketsMap.remove(nodeId);
        bucketInfoList.removeIf((bucket) -> bucket.getNodeId().equals(nodeId));
        decreaseNumberOfActiveBuckets(size);
        makeAllBuckets();
    }

    private void makeAllBuckets() {
        allBuckets = List.copyOf(bucketInfoList);
    }
}
