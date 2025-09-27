Core Architecture
It's a low-latency, highly available, in-memory caching system designed for scalability and consistency, intentionally trading durability for performance. The system is composed of primary nodes and their corresponding replicas, operating in a sharded, decentralized cluster.

Sharding and Routing
Partitioning: Data is sharded across primary nodes using Jump Consistent Hashing (JCH) over a sorted list of primaries. This provides deterministic key mapping and automatic rebalancing when the cluster size changes.

Client Interaction: Primaries handle client requests, routing them to the correct node if necessary. Reads can be offloaded to replicas to manage high load.

Replication and Consistency
Write Path: Writes go to the primary, are logged in an in-memory WAL for consistency, and are then asynchronously replicated to a set of replicas. The client receives an ACK early for low latency.

Replica Sync: Replicas use the WAL to catch up on missed writes. A primary maintains an in-sync set of replicas for read offloading.

Failure Detection and Recovery
Failure Detection: A gossip protocol is used for decentralized health monitoring. Nodes use heartbeats and epochs to mark peers as pdead and eventually dead.

Primary Failure:

With Replicas: The replicas of the failed primary conduct a leader election to promote the one with the highest version.

Without Replicas: The node is removed from the cluster map. The data is considered lost, and the keys are remapped via JCH. The cache is repopulated for these keys via cache misses to the source of truth.

Returning Nodes: An old replica that comes back online will detect the new leader via its higher term/epoch and become a replica.

Scalability and Healing
Scaling Up: New nodes are added through an orchestrated, multi-phase process led by a deterministic coordinator. This involves a background data migration from existing nodes, followed by an atomic cutover to the new cluster topology.

Self-Healing: The cluster can automatically rebalance replicas. A healthy "benevolent primary" can donate a replica to an unhealthy one. This requires the donated replica to wipe its old data and perform a full data sync from its new primary.