package com.memora.services;

import com.memora.store.Bucket;

public class BucketManager {

    Bucket bucket;
    public BucketManager() {
        this.bucket = new Bucket("default", 1000);
    }

    public Bucket getBucket() {
        return bucket;
    }
}