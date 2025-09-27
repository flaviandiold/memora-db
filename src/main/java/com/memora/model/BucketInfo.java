package com.memora.buckets;

import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Immutable class representing bucket information in Memora DB.
 */
@Data
@RequiredArgsConstructor
public final class BucketInfo {

    private final String bucketId;
    private final String nodeId;
    private final int bucketIndex;
}
