# Read and Write
When a write/read comes, the request will be handled by the primary's memora server (Netty based), this will once again check if the read/write is actually intended for its bucket. It can so happen that a new node has joined the cluster and only the current node has locked in this new node in its bucket map. The jump consistent hashing ensures that the request forwarding or jump does not ever end up in a loop. 

A primary will also maintain a set of replicas that are in sync (same version) with the primary, let's call it the insync set.

And when the write is actually intended to its own bucket. The following steps will be followed

1. The node will write it to it's in memory WAL here the WAL's version would have naturally increased.
2. The write will be written to the actual memora store.
3. The primary's version will increase.
4. Clear out the insync set.
5. Send back an ACK to the client. 

After sending an ACK, the primary will pick a thread from a pool and will use it to send the write to all it's replicas when sending the write the primary will send the write's version too. When a replica gets the write it will not calculate which bucket this write will go, since the primary will send the bucket index as well, the write will go to the bucket store if the incoming write's version is greater than the version of the replica plus one (ie., say the version of the replica is 5 but the write from primary has a version 7, then it means than the version 6 didn't arrive to or is not processed by the replica), here the replica will request a read from the primary's WAL for version 6, and queue the request 7, the read from WAL will return the 6th request and when the replica gets it, it plays the 6th request and the 7th request in the queue, and here before the queue is cleared any subsequent write request that comes from the primary will be in the queue only and enqueueing doesn't increase the replica's version.

In write we can have a WAIT mechanism, the command will be like this PUT key value WAIT num_replica timeout, the PUT command will be like this PUT key value key value key value ... TTL ms WAIT num_replica timeout_ms. Here unlike redis which enforces all keys to belong to the same hashslot, we will group the requests according to buckets and will be routed to the respective primaries. Here if any primary fails to send an ACK. The failed keys alone will be returned to the client saying "FAILED key key key"

In reads the read will always come to the primary as well. The primary will check if the read is intended for it, if yes it will check the QPS it is handling currently. If it is greater than 10000 queries per second it will be routed to the in sync replica. And the primary will only behave as a mediator.

Thread pool service, this will create pools of threads, we can maintain a concurrent hashmap where for a given key a new pool with said threads will be created if does not exist, if exists a thread from that pool will be returned.