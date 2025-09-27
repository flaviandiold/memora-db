package com.memora.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable class representing the cluster mapping of nodes.
 */
public final class ClusterMap {

    private final long epoch;
    private final List<NodeInfo> primaries;
    private final Map<String, List<NodeInfo>> replicaSets;

    public ClusterMap(long epoch, List<NodeInfo> primaries, Map<String, List<NodeInfo>> replicaSets) {
        this.epoch = epoch;
        this.primaries = List.copyOf(Objects.requireNonNull(primaries, "primaries cannot be null"));
        this.replicaSets = Map.copyOf(Objects.requireNonNull(replicaSets, "replicaSets cannot be null"));
    }

    public long epoch() {
        return epoch;
    }

    public List<NodeInfo> primaries() {
        return primaries;
    }

    public Map<String, List<NodeInfo>> replicaSets() {
        return replicaSets;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClusterMap)) return false;
        ClusterMap that = (ClusterMap) o;
        return epoch == that.epoch &&
               Objects.equals(primaries, that.primaries) &&
               Objects.equals(replicaSets, that.replicaSets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(epoch, primaries, replicaSets);
    }

    @Override
    public String toString() {
        return String.format("ClusterMap[epoch=%d, primaries=%s, replicaSets=%s]", epoch, primaries, replicaSets);
    }
}
