# Cluster

A cluster is a group of primaries holding a subset of buckets and replicas replicating these buckets.

There is a cluster map and this map has an epoch associated with it. Whenever a mutation happens to this map, the epoch increases. This epoch is attached to the heartbeat among primaries.

A cluster map is like this

{
  "epoch": 1,
  "primaries": [
    {
      "nodeId": "01H8X2J5K0P5M4Q3R2S1T0V9W8",
      "address": "10.0.1.10:9090"
    },
    {
      "nodeId": "01H8X2J5K1A2B3C4D5E6F7G8H9",
      "address": "10.0.1.11:9090"
    },
    {
      "nodeId": "01H8X2J5K2Z9Y8X7W6V5U4T3S2",
      "address": "10.0.1.12:9090"
    }
  ],
  "replicaSets": {
    "01H8X2J5K0P5M4Q3R2S1T0V9W8": [
      {
        "nodeId": "01H8X2J5K3ABCDEFGHJKLMNPQR",
        "address": "10.0.2.20:9090"
      },
      {
        "nodeId": "01H8X2J5K4STUVWXYZ12345678",
        "address": "10.0.2.21:9090"
      }
    ],
    "01H8X2J5K1A2B3C4D5E6F7G8H9": [
      {
        "nodeId": "01H8X2J5K5QWERTYUIOPASDFGH",
        "address": "10.0.2.22:9090"
      }
    ],
    "01H8X2J5K2Z9Y8X7W6V5U4T3S2": [
      {
        "nodeId": "01H8X2J5K6ZXCVBNM123456789",
        "address": "10.0.2.23:9090"
      },
      {
        "nodeId": "01H8X2J5K7ASDFGHJKLQWERTYU",
        "address": "10.0.2.24:9090"
      }
    ]
  }
}

When a cluster map's epoch is changed, it will recalculate the bucket map

# Configurations

- REPLICATION_FACTOR -> Number of replicas a primary must have to be consider HEALTHY. (default 1)
- ALLOWED_MEMORY -> The memory allocated for the node