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

    public NodeInfo getMyPrimary(NodeInfo replica) {
        String primaryId = replicaToPrimaryMap.get(replica.getNodeId());
        return allNodes.get(primaryId);
    }

    public List<String> getReplicas(String primaryId) {
        return List.copyOf(primaryToReplicasMap.get(primaryId));
    }

    public void incrementEpoch() {
        epoch++;
        log.info("Epoch incremented to {}", epoch);
        log.info("Primarys: {}", primaries);
        log.info("PrimaryToReplicasMap: {}", primaryToReplicasMap);
        log.info("ReplicaToPrimaryMap: {}", replicaToPrimaryMap);
        log.info("AllNodes: {}", allNodes);
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

}
