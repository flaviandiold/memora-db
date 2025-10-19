# Scale Up

Scale up happens in two cases, one INTERNALLY and two EXTERNALLY.

When the end-user gives the cluster nodes to scale up. The end user connects with any node in the cluster, if the connected node is a replica the request is simply forwarded to its primary by the replica. After connecting to any node the user sends 

CLUSTER ADD NODES host1@port1 host2@port2 .... [PRIMARY | REPLICA] 

to the node.

The default property of this command is to make the added nodes as replicas. But this can be overriden using PRIMARY (but even with this argument only one primary will be added to the cluster). 

A thread will be scheduled in the lexicographically sorted first node in the cluster to run after 10 minutes (this is done for primaries to delete their unnecessary keys and catch up to the current epoch) of a redistribution event to check if there are enough replicas, if yes then another scaling up event takes place.

## External Scale Up
When a node is added the priority is to make the current cluster healthy. Also the recommendation while adding new nodes is to make sure the number of nodes added makes the cluster healthy. 

Now when the user sends the ADD NODES command, the primary (orchestrator) that handles it does these steps.

1. Marks itself as unavailable for any other REDISTRIBUTION, this helps when any other node tries to begin another scaling event. REDISTRIBUTION_LOCKED.
2. Figures out the expectation of the new nodes.
    1.1 If the command has no modifier (or REPLICA as the modifier) it means that the all the new nodes are added as REPLICAs and the scaling up is left to the scaling up thread.
    1.2 If the command has PRIMARY as the modifier it means that bringing up a new primary is the priority, then what happens is a new primary is exposed to the cluster and the new nodes are added as much as the replication factor demands, and if there are any more new nodes after this, they are all evenly distributed (or UNHEALTHY primaries are prioritized).

3. Now if there is a primary formed among the new nodes. This data is gathered and only the new primary info is transmitted to all existing primaries by the orchestrator, saying CLUSTER JOINING new_primary@port ....
4. When an existing primary gets the packet, it knows how to reach the new primary. The existing primary sends the new primary its own information about itself, which includes its replica hosts and its bucket information, the new primary constructs the bucket map and cluster map using these data and marks its epoch as the latest epoch that comes from the primaries.
5. After doing all this, the new primary says to each existing primary "CLUSTER AWARE" which means it is now aware of the cluster, you guys can start data transmission, this AWARE status is now also gossiped among primaries. After this all primaries starts a thread and transfers keys that now belong to the new primary. "REDISTRIBUTION"
6. During this process any new write that comes and is hashed to the new node as well, is written to the current node first and then sent to the new node.
7. After writing all moving keys to the new primary, the existing primary updates its bucket map, increases its epoch and adds the new primaries, and changes its status to HEALTHY/UNHEALTHY according to its replica information, this needs no co-operation due to epochs, when the current primary gets a read or write based on an older epoch of the cluster map it simply forwards the request if the request is meant for the new primary by calculting the bucket based on the new epoch, this forwarding only happens until all the bucket maps are updated. And the bucket map's epoch is gossiped in heartbeats so even if a primary is catching up, it can get the correct epoch and data.

During this entire process a node will be in REDISTRIBUTION state, and a ADD NODES command is only allowed by the orchestrator if no node is in REDISTRIBUTION state, because of this an orchestrator initially gets permission from every primary directly instead of trusting it's gossip data to start REDISTRIBUTION.

## Internal Scale Up

Internal scale up is also simple, during gossips when the lexicographically sorted first primary finds that the replica count of primaries that are in excess can form a new primary (ie., 1.5 * REPLICATION_FACTOR replicas are extra, 1.5 is configured to give a buffer that even after taking replicas, a primary will not go UNHEALTHY when a replica fails), it starts this process. It gets the replicas of all the primaries that are in excess, sorts them and gets the newly added nodes (last node ids, since Node IDs are ULIDs, also they signify the newly added nodes) from these sets and only gets enough that the primary neither goes UNHEALTHY and no primary's replica is overfetched. There will be an even snatch. Then the first sorted replica will be called a primary. It will clean its slate and create new buckets. And all the other replicas will follow it, and the handler will send JOINING new_primary@port. And the rest will follow as External Scale Up.


# Scale Down

Scale down is simple, with the following commands

- KICK OUT n PRIMARY
- KICK OUT n REPLICA [EACH]
- KICK OUT n REPLICA FROM host@port

is fired to any node.

Here the first command marks the last n primaries as going to be removed. This is told to all the primaries and the last n nodes start to distribute their keys to the remaining buckets. After doing this, they go out of the cluster. This also removes the replicas of those primaries.

The second command removes n REPLICAs from all primaries, if they have n, else only the primary remains in that slot.

The third command removes n REPLICAs from the specified primary alone.

Removing Primaries (Command 1)
This is a critical operation that involves migrating all data from the departing nodes. It must be handled with a protocol that is just as robust as the scale-up process to ensure safety and consistency.

1. Initiation and Lock
An orchestrator receives the KICK OUT n PRIMARY command.

It locks the cluster by getting permission from all primaries and broadcasting a REDISTRIBUTING state.

It identifies the last n primaries in the current bucket map and gossips their new state as DECOMMISSIONING.

2. Announce the New Topology
The orchestrator calculates the new, smaller cluster map (with N-n primaries) and a new, future epoch.

It broadcasts this "future map" to all nodes. Critically, all nodes continue to route traffic using the old N-node map for now.

3. Data Migration
Each of the n DECOMMISSIONING primaries is now responsible for offloading its data.

It scans its local keys. For each key, it calculates the key's new home using the future map (JCH(key, N-n)) and pushes the key-value pair to the correct surviving primary.

4. Handle Live Traffic
While in the DECOMMISSIONING state, nodes must handle incoming requests carefully:

Reads: Can be served normally from local data.

Writes: The safest and simplest strategy is to reject new writes. The node can respond with a PARTITION_DECOMMISSIONING error, prompting the client to retry shortly. This prevents writing new data that would immediately need to be migrated or might be lost if the node crashes mid-migration.

5. Atomic Cutover
Once a DECOMMISSIONING primary has successfully migrated all of its data, it reports back to the orchestrator.

When all n departing nodes have confirmed completion, the orchestrator initiates the atomic cutover using a two-phase commit (PREPARE/COMMIT).

All surviving nodes switch to the new N-n map and epoch at the same time.

6. Shutdown
After the cluster has successfully switched to the new epoch, the orchestrator sends a final SHUTDOWN command to the now-empty DECOMMISSIONING primaries and all of their associated replicas.

Removing Replicas (Commands 2 & 3)
These are relatively low-risk operations as they only reduce the cluster's redundancy, not its data.

Process:

An orchestrator node receives the command.

It identifies the target primary/primaries.

For each target, it selects n replicas to remove. A good selection strategy would be to remove the newest replicas first (based on their ULID), as they are likely to have the least historical usage.

The orchestrator sends a DECOMMISSION command to the chosen replicas.

The replicas gracefully shut down.

The affected primaries update their replica sets and gossip the new, slightly smaller cluster map with an incremented epoch.


# Other things to note

- When we are redistributing, we keep a note of all the keys that are moving, since when a mutating command comes for these moved keys should be handled properly. They are put in a queue and are handled until an ACK from the new node is gotten. Likewise this also helps in deleting the moved keys from the current primary after incrementing the node size.