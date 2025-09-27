# Node:
A node will have buckets directly proportional to its allocated memory. A single bucket will be of size approx 500MB. Assuming that there is an allocated space of 2.6 GB in the heap for this node. We will do a Math.floor((heap_size) / 500). And we will create these many buckets in the node. Node and its buckets will have an ULID as their unique identifier.

A bucket will be like this

{
    "buckets": [
        {
            "bucketId": "01H8X2J5K0P5M4Q3R2S1T0V9W8", 
            "nodeId": "01H8X2J5K0P5M4Q3R2S1T0V9W8"
            "bucketIndex": 0,
        },
        {
            "bucketId": "01H8X2J5K1A2B3C4D5E6F7G8H9",
            "nodeId": "01H8X2J5K0P5M4Q3R2S1T0V9W8"
            "bucketIndex": 1,
        },
        {
            "bucketId": "01H8X2J5K2Z9Y8X7W6V5U4T3S2",
            "nodeId": "01H8X2J5K0P5M4Q3R2S1T0V9W8"
            "bucketIndex": 2,
        },
        {
            "bucketId": "01H8X2J5K0P5M4Q3R2D1T0V9W8",
            "nodeId": "01H8X2J5K1A2B3C4D5E6F7G8H9"
            "bucketIndex": 0,
        },
        {
            "bucketId": "01H8X2J5K1A2B3C4DGE6F7G8H9",
            "nodeId": "01H8X2J5K1A2B3C4D5E6F7G8H9"
            "bucketIndex": 1,
        },
        {
            "bucketId": "01H8X2J5K2Z9Y8XRW6V5U4T3S2",
            "nodeId": "01H8X2J5K1A2B3C4D5E6F7G8H9"
            "bucketIndex": 2,
        }
    ],
    "numberOfActiveBuckets": 6
}

bucketId -> Globally unique sortable identifier ULID
bucketIndex -> The index of the bucket in the specific node, this need not be shared, since it is simply the bucketIds sorted by nodeIds.

There will be a bucket manager which will hold instances of buckets, also the router instance will be here.

When a node goes out of memory, the last entered key gets evicted from that bucket.

# PRIMARY
A primary is the master of a subset of buckets in the cluster.

# REPLICA
A replica is a copy of the primary in all sense and means.