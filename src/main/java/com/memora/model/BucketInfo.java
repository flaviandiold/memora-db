package com.memora.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Immutable class representing bucket information in Memora DB.
 */
@Data
@AllArgsConstructor
@Builder
public class BucketInfo {

    private final String bucketId;
    private final String nodeId;
}
