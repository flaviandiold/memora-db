package com.memora.model;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.PriorityBlockingQueue;

import lombok.Data;

/**
 * Immutable class representing the cluster mapping of nodes.
 */
@Data
public class ClusterMap {

    private final long epoch;
    private final PriorityBlockingQueue<NodeInfo> primaries;
    private final Map<String, PriorityBlockingQueue<NodeInfo>> replicaSets;

    public ClusterMap(long epoch, PriorityBlockingQueue<NodeInfo> primaries,
            Map<String, PriorityBlockingQueue<NodeInfo>> replicaSets) {
        this.epoch = epoch;
        this.primaries = Objects.requireNonNull(primaries, "primaries cannot be null");
        this.replicaSets = Objects.requireNonNull(replicaSets, "replicaSets cannot be null");
    }

    public ClusterMap(long epoch) {
        this(epoch, new PriorityBlockingQueue<>(60, getComparator()), new HashMap<>());
    }

    public void addPrimary(NodeInfo primary) {
        primaries.add(primary);
    }

    public void removePrimary(NodeInfo primary) {
        primaries.remove(primary);
    }

    public void addReplica(String primaryId, NodeInfo replica) {
        replicaSets.getOrDefault(primaryId, new PriorityBlockingQueue<>(60, getComparator())).add(replica);
    }

    private static Comparator<NodeInfo> getComparator() {
        return (a, b) -> a.getNodeId().compareTo(b.getNodeId());
    }
}
