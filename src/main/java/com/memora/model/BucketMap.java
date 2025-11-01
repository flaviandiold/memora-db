package com.memora.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.Set;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class BucketMap {

    private int numberOfActiveBuckets;
    private List<BucketInfo> allBuckets;
    private final ConcurrentHashMap<String, Set<String>> nodeToBucketsMap;
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
        if (numberOfActiveBuckets + incrementBy < 0 && numberOfActiveBuckets > allBuckets.size()) {
            throw new RuntimeException("Cannot increase number of active buckets");
        }
        numberOfActiveBuckets += incrementBy;
    }

    public void decreaseNumberOfActiveBuckets(int decrementBy) {
        if (numberOfActiveBuckets - decrementBy < 0) {
            throw new RuntimeException("Cannot decrease number of active buckets");
        }
        numberOfActiveBuckets -= decrementBy;
    }

    public int getNumberOfActiveBuckets() {
        return numberOfActiveBuckets;
    }

    public boolean isBucketInNode(String nodeId, String bucketId) {
        return nodeToBucketsMap.containsKey(nodeId) && nodeToBucketsMap.get(nodeId).contains(bucketId);
    }

    public void addBuckets(List<BucketInfo> buckets) {
        addBuckets(buckets, true);
    }

    public void addBuckets(List<BucketInfo> buckets, boolean scale) {
        buckets.forEach(bucket -> addBucket(bucket));
        makeAllBuckets();
        if (scale) increaseNumberOfActiveBuckets(buckets.size());
    }

    public void scale(int numberOfBuckets) {
        increaseNumberOfActiveBuckets(numberOfBuckets);
    }

    private void addBucket(BucketInfo bucket) {
        nodeToBucketsMap.computeIfAbsent(bucket.getNodeId(), k -> new ConcurrentSkipListSet<>()).add(bucket.getBucketId());
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
        Set<String> buckets = nodeToBucketsMap.get(nodeId);
        if (Objects.nonNull(buckets)) {
            int size = buckets.size();
            nodeToBucketsMap.remove(nodeId);
            decreaseNumberOfActiveBuckets(size);
            bucketInfoList.removeIf((bucket) -> bucket.getNodeId().equals(nodeId));
            makeAllBuckets();
        }
    }


    public void retainBucketsOf(String nodeId) {
        int size = Optional.ofNullable(nodeToBucketsMap.get(nodeId)).orElse(Set.of()).size();
        for (String otherNodeId: nodeToBucketsMap.keySet()) {
            if (!nodeId.equals(otherNodeId)) {
                nodeToBucketsMap.remove(otherNodeId);
            }
        }
        bucketInfoList.removeIf((bucket) -> !bucket.getNodeId().equals(nodeId));
        numberOfActiveBuckets = size;
        makeAllBuckets();
    }

    public void forgetPrimary(String nodeId) {
        clearBucketsOf(nodeId);
    }

    public void clearAll() {
        nodeToBucketsMap.clear();
        bucketInfoList.clear();
        numberOfActiveBuckets = 0;
        makeAllBuckets();
    }

    private void makeAllBuckets() {
        allBuckets = List.copyOf(bucketInfoList);
    }
}
