package com.memora.model;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.PriorityBlockingQueue;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Immutable class representing the cluster mapping of nodes.
 */
@Data
@Slf4j
public class ClusterMap {

    private long epoch;
    private final Map<String, NodeInfo> allNodes;
    private final PriorityBlockingQueue<String> primaries;
    private final Map<String, PriorityBlockingQueue<String>> primaryToReplicasMap;
    private final Map<String, String> replicaToPrimaryMap;

    public ClusterMap(long epoch) {
        this.epoch = epoch;
        this.allNodes = new HashMap<>();
        this.primaries = new PriorityBlockingQueue<>(60, getComparator());
        this.primaryToReplicasMap = new HashMap<>();
        this.replicaToPrimaryMap = new HashMap<>();
    }

    public void addPrimary(NodeInfo primary) {
        if (allNodes.containsKey(primary.getNodeId())) {
            return;
        }
        addNode(primary);
        primaries.add(primary.getNodeId());
    }

    public void removePrimary(NodeInfo primary) {
        String primaryId = primary.getNodeId();
        primaries.remove(primaryId);
        removeNode(primaryId);
        primaryToReplicasMap.remove(primaryId);
        replicaToPrimaryMap.remove(primaryId);
    }

    public void addReplica(String primaryId, NodeInfo replica) {
        addNode(replica);
        String replicaId = replica.getNodeId();
        replicaToPrimaryMap.compute(replicaId, (k, v) -> {
            if (!Objects.isNull(v)) primaryToReplicasMap.get(v).remove(k);
            
            primaryToReplicasMap
                .computeIfAbsent(primaryId, id -> new PriorityBlockingQueue<>(60, getComparator()))
                .add(k);
            return primaryId;
        });
    }

    public boolean isPrimaryOf(String replicaId, String primaryId) {
        if (!replicaToPrimaryMap.containsKey(replicaId)) return false;
        return replicaToPrimaryMap.get(replicaId).equals(primaryId);
    }

    public NodeInfo getMyPrimary(String replicaId) {
        String primaryId = replicaToPrimaryMap.get(replicaId);
        return allNodes.get(primaryId);
    }

    public List<String> getReplicaIds(String primaryId) {
        return List.copyOf(primaryToReplicasMap.get(primaryId));
    }

    public List<NodeInfo> getReplicas(String primaryId) {
        PriorityBlockingQueue<String> replicas = primaryToReplicasMap.get(primaryId);
        if (replicas == null) return List.of();
        return replicas.stream().map(allNodes::get).toList();
    }

    public void incrementEpoch() {
        epoch++;
    }

    private void addNode(NodeInfo node) {
        Objects.requireNonNull(node, "node cannot be null");
        allNodes.put(node.getNodeId(), node);
    }

    private void removeNode(String nodeId) {
        allNodes.remove(nodeId);
    }

    private Comparator<String> getComparator() {
        return (a, b) -> allNodes.get(a).getNodeId().compareTo(allNodes.get(b).getNodeId());
    }

    public void merge(ClusterMap other) {
        if (other == null) {
            return;
        }

        // If other map is newer → adopt completely
        if (other.getEpoch() > this.epoch) {
            this.epoch = other.getEpoch();

            // Deep copy
            this.allNodes.clear();
            this.allNodes.putAll(other.getAllNodes());

            this.primaries.clear();
            this.primaries.addAll(other.getPrimaries());

            this.primaryToReplicasMap.clear();
            other.getPrimaryToReplicasMap().forEach((k, v) ->
                this.primaryToReplicasMap.put(k, new PriorityBlockingQueue<>(v))
            );

            this.replicaToPrimaryMap.clear();
            this.replicaToPrimaryMap.putAll(other.getReplicaToPrimaryMap());

            return;
        }

        // If epochs are equal, merge conservatively
        if (other.getEpoch() == this.epoch) {
            // Merge nodes
            other.getAllNodes().forEach((id, node) -> this.allNodes.putIfAbsent(id, node));

            // Merge primaries
            this.primaries.addAll(other.getPrimaries());

            // Merge replica mappings
            other.getPrimaryToReplicasMap().forEach((primary, replicas) -> {
                this.primaryToReplicasMap
                    .computeIfAbsent(primary, id -> new PriorityBlockingQueue<>(60, getComparator()))
                    .addAll(replicas);
            });

            // Merge reverse mapping
            other.getReplicaToPrimaryMap().forEach((replica, primary) -> {
                this.replicaToPrimaryMap.putIfAbsent(replica, primary);
            });
        }

        // If other epoch is older, do nothing
    }


}
