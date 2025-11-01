package com.memora.model;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

import com.memora.core.MemoraNode;
import com.memora.exceptions.MemoraException;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Immutable class representing the cluster mapping of nodes.
 */
@Data
@Slf4j
public class ClusterMap {

    private final Map<String, NodeInfo> allNodes;
    private final ConcurrentSkipListSet<String> primaries;
    private final Map<String, ConcurrentSkipListSet<String>> primaryToReplicasMap;
    private final Map<String, String> replicaToPrimaryMap;
    private AtomicLong epoch;

    public ClusterMap(long epoch) {
        this.epoch = new AtomicLong(epoch);
        this.allNodes = new ConcurrentHashMap<>();
        this.primaries = new ConcurrentSkipListSet<>(getComparator());
        this.primaryToReplicasMap = new ConcurrentHashMap<>();
        this.replicaToPrimaryMap = new ConcurrentHashMap<>();
    }

    public void addPrimary(NodeInfo primary) {
        if (primaries.contains(primary.getNodeId())) {
            return;
        }
        addNode(primary);
        primaries.add(primary.getNodeId());
    }

    public void removePrimary(String primaryId) {
        primaries.remove(primaryId);
        removeNode(primaryId);
        primaryToReplicasMap.remove(primaryId);
    }

    public long getEpoch() {
        return epoch.get();
    }

    public void setEpoch(long newEpoch) {
        epoch.set(newEpoch);
    }

    public void addReplica(String primaryId, NodeInfo replica) {
        // 1. Add the node to the cluster's main list (this seems fine)
        addNode(replica); 
        
        String replicaId = replica.getNodeId();

        // 2. Atomically update the replica-to-primary mapping.
        //    'put' returns the *previous* primary ID this replica was mapped to.
        String prevPrimaryId = replicaToPrimaryMap.put(replicaId, primaryId);

        // 3. Atomically add the replica to the *new* primary's set of replicas.
        //    This is safe and will create the set if it doesn't exist.
        primaryToReplicasMap
            .computeIfAbsent(primaryId, id -> new ConcurrentSkipListSet<>(getComparator()))
            .add(replicaId);
        
        // 4. If there was an old primary, and it's not the same as the new one,
        //    atomically remove the replica from the *old* primary's set.
        if (prevPrimaryId != null && !prevPrimaryId.equals(primaryId)) {
            
            // computeIfPresent is atomic. It finds the set, lets you modify it,
            // and handles the case where the set doesn't exist (it does nothing).
            primaryToReplicasMap.computeIfPresent(prevPrimaryId, (key, replicaSet) -> {
                replicaSet.remove(replicaId);
                
                // Best Practice: If the set is now empty, return null
                // to remove the old primary's entry from the map.
                return replicaSet.isEmpty() ? null : replicaSet;
            });
        }
    }

    public void removeReplica(String replicaId) {
        String primaryId = replicaToPrimaryMap.get(replicaId);
        if (Objects.nonNull(primaryToReplicasMap.get(primaryId))) {
            primaryToReplicasMap.get(primaryId).remove(replicaId);
        }
        replicaToPrimaryMap.remove(replicaId);
        removeNode(replicaId);
    }

    public boolean isPrimaryOf(String replicaId, String primaryId) {
        if (!replicaToPrimaryMap.containsKey(replicaId)) return false;
        return replicaToPrimaryMap.get(replicaId).equals(primaryId);
    }

    public boolean isPrimary(String nodeId) {
        return primaries.contains(nodeId);
    }

    public NodeInfo getMyPrimary(String replicaId) {
        String primaryId = replicaToPrimaryMap.get(replicaId);
        if (primaryId == null) return null;
        return getNode(primaryId);
    }

    public NodeInfo getNode(String nodeId) {
        return allNodes.get(nodeId);
    }

    public List<String> getReplicaIds(String primaryId) {
        ConcurrentSkipListSet<String> replicas = primaryToReplicasMap.get(primaryId);
        if (replicas == null) return List.of();
        return List.copyOf(replicas);
    }

    public List<NodeInfo> getReplicas(String primaryId) {
        return getReplicaIds(primaryId).stream().map(allNodes::get).toList();
    }

    public boolean containsNode(String nodeId) {
        return allNodes.containsKey(nodeId);
    }

    public String getClusterLeader() {
        return primaries.first();
    }

    public boolean isNodeAfter(String second, String first) {
        return getComparator().compare(second, first) > 0;
    }

    public void incrementEpoch() {
        if (MemoraNode.getInfo().isPrimary()) epoch.incrementAndGet();
    }

    private void addNode(NodeInfo node) {
        Objects.requireNonNull(node, "node cannot be null");
        allNodes.put(node.getNodeId(), node);
        incrementEpoch();
    }

    private void removeNode(String nodeId) {
        allNodes.remove(nodeId);
        incrementEpoch();
    }

    private Comparator<String> getComparator() {
        return (a, b) -> a.compareTo(b);
    }

    public synchronized void merge(ClusterMap other) {
        if (other == null) {
            return;
        }

        // Merge nodes
        other.getAllNodes().forEach((id, node) -> this.allNodes.putIfAbsent(id, node));
        
        // Merge primaries
        this.primaries.addAll(other.getPrimaries());
        
        // Merge replica mappings
        other.getPrimaryToReplicasMap().forEach((primary, replicas) -> {
            this.primaryToReplicasMap
                .computeIfAbsent(primary, id -> new ConcurrentSkipListSet<>(getComparator()))
                .addAll(replicas);
        });
        
        // Merge reverse mapping
        other.getReplicaToPrimaryMap().forEach((replica, primary) -> {
            this.replicaToPrimaryMap.putIfAbsent(replica, primary);
        });

        long epoch = other.getEpoch();
        if (epoch > this.getEpoch()) this.setEpoch(epoch);

    }

    public List<String> sort(List<String> nodeIds) {
        return nodeIds.stream().sorted(getComparator()).toList();
    }

    public void validateNewPrimary(String newPrimary) {
        String lastPrimary = primaries.last();
        if (isNodeAfter(lastPrimary, newPrimary)) {
            throw new MemoraException("New Primary ID cannot be smaller than the last Primary ID");
        }
    }

    public void validateNewNodes(Collection<String> newNodes) {
        newNodes.stream()
                .filter(allNodes::containsKey)
                .findFirst()
                .ifPresent(nodeId -> {
                    throw new MemoraException("Node with ID " + nodeId + " already exists");
                });
    }
    
    public void clear() {
        this.setEpoch(0);
        allNodes.clear();
        primaries.clear();
        primaryToReplicasMap.clear();
        replicaToPrimaryMap.clear();
    }
}
