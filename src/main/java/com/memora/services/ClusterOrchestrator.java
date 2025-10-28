package com.memora.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;

import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.memora.core.MemoraClient;
import com.memora.core.MemoraNode;
import com.memora.model.CacheEntry;
import com.memora.model.ClusterMap;
import com.memora.model.NodeBase;
import com.memora.model.NodeInfo;

import lombok.extern.slf4j.Slf4j;

import com.memora.enums.NodeType;
import com.memora.enums.ThreadPool;
import com.memora.exceptions.MemoraException;
import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.messages.RpcStatus;
import com.memora.utils.Parser;
import com.memora.utils.ResponseFactory;

@Slf4j
public final class ClusterOrchestrator {

    private final NodeInfo currentNode;
    private final ReplicationManager replicationManager;
    private final ClientManager clientManager;
    private final ThreadPoolService threadPoolService;

    private final ClusterMap clusterMap;

    private final Set<String> inReplication;

    @Inject
    public ClusterOrchestrator(ReplicationManager replicationManager, ClientManager clientManager,
            ThreadPoolService threadPoolService, ClusterMap clusterMap) {
        log.info("Starting cluster orchestrator...");
        this.currentNode = MemoraNode.getInfo();
        this.replicationManager = replicationManager;
        this.threadPoolService = threadPoolService;
        this.clientManager = clientManager;
        this.clusterMap = clusterMap;
        this.inReplication = new ConcurrentSkipListSet<>();
        buildCluster();
    }

    public void clearInSyncReplicas() {
        replicationManager.clearInSyncReplicas();
    }

    public ClusterMap getMap() {
        return clusterMap;
    }

    /**
     * Function that will make the given node at host and port
     * as a replica, and will start streaming data to it.
     * 
     * Becomes a primary
     * 
     * @param host
     * @param port
     */
    public void primarize(String host, int port) {
        String replicaId = null;
        try {
            final NodeBase base = clientManager.getAddress(host, port);
            if (currentNode.equals(base.getHost(), base.getPort())) {
                throw new MemoraException("Cannot replicate to self");
            }
            if (!NodeType.PRIMARY.equals(currentNode.getType())) {
                switch (currentNode.getType()) {
                    case STANDALONE -> {
                        currentNode.setType(NodeType.PRIMARY);
                        clusterMap.addPrimary(currentNode);
                    }
                    case REPLICA -> {
                        NodeInfo myPrimary = clusterMap.getMyPrimary(currentNode.getNodeId());
                        clientManager.getOrCreate(myPrimary).primarize(host, port);
                        return;
                    }
                }
            }
            replicaId = clientManager.createAndAdd(base);
            if (inReplication.contains(replicaId))
                return;

            inReplication.add(replicaId);
            final MemoraClient client = clientManager.getOrCreate(replicaId, base);
            final NodeType replicaType = NodeType.valueOf(client.getNodeType().get().getResponse());
            if (replicaType.equals(NodeType.PRIMARY)) {
                throw new MemoraException("Cannot primarize another primary");
            }

            log.info("Primarizing to replica: {}", replicaId);

            /**
             * Removing current node as replica
             * And adding current node as primary to the recieved replicaId
             */
            NodeInfo replica = NodeInfo.create(replicaId, host, port);
            currentNode.incrementEpoch();
            client.replicate(currentNode.getHost(), currentNode.getPort()).join();
            replicationManager.replicateDataTo(replica);
            clusterMap.addReplica(currentNode.getNodeId(), replica);
            replicateClusterMap();
            inReplication.remove(replicaId);
        } catch (IOException | InterruptedException | ExecutionException | RuntimeException e) {
            if (Objects.nonNull(replicaId)) {
                clusterMap.removeReplica(replicaId);
                inReplication.remove(replicaId);
            }
            log.error("Failed to primarize to {}:{}", host, port);
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Function that will initiate replication of the data
     * from the node at given host and port.
     * 
     * Becomes a replica
     * 
     * @param host
     * @param port
     */
    public void replicate(String host, int port) {
        try {
            final NodeBase base = clientManager.getAddress(host, port);
            if (currentNode.equals(base.getHost(), base.getPort())) {
                throw new MemoraException("Cannot replicate to self");
            }
            switch (currentNode.getType()) {
                case PRIMARY -> {
                    throw new MemoraException("A primary node should not replicate another node");
                }
            }

            final String primaryId = clientManager.createAndAdd(base);
            final String replicaId = currentNode.getNodeId();
            if (clusterMap.isPrimaryOf(replicaId, primaryId))
                return;

            final MemoraClient client = clientManager.getOrCreate(primaryId, base);
            behave(NodeType.REPLICA);
            final NodeInfo primary = NodeInfo.create(primaryId, host, port);
            /**
             * Removing current node as primary
             * And adding current node as replica to the recieved primaryId
             */
            clientManager.addClient(primaryId, client);
            clusterMap.addPrimary(primary);
            clusterMap.addReplica(primaryId, currentNode);
            replicationManager.initiateReplicationOf(primary);
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.error("Failed to replicate to {}:{} {}", host, port, e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    public void mergeClusterMap(ClusterMap clusterMap) {
        clusterMap.getPrimaries().forEach(primary -> {
            clientManager.createAndAdd(clusterMap.getAllNodes().get(primary).getNodeBase());
        });
        this.clusterMap.merge(clusterMap);
        replicateClusterMap();
    }

    public void replicateClusterMap() {
        if (MemoraNode.getInfo().isPrimary()) {
            replicationManager.replicateClusterMap(this.clusterMap);
        }
    }

    public RpcResponse.Builder forwardGet(Map<String, List<String>> nodeToKeysMap) {
        for (String nodeId : nodeToKeysMap.keySet()) {
            if (!clusterMap.containsNode(nodeId)) {
                return ResponseFactory.builder().setStatus(RpcStatus.BAD_REQUEST)
                        .setResponse("Node " + nodeId + " not found in cluster");
            }
        }
        List<String> values = new ArrayList<>();
        RpcStatus status = RpcStatus.OK;

        for (String nodeId : nodeToKeysMap.keySet()) {
            List<String> keys = nodeToKeysMap.get(nodeId);
            try {
                RpcResponse response = clientManager.getClient(nodeId).get(keys).get();
                if (!response.getStatus().equals(RpcStatus.OK) && status.equals(RpcStatus.OK)) {
                    status = RpcStatus.PARTIAL_FULFILLMENT;
                }
                if (status.equals(RpcStatus.OK)) {
                    if (keys.size() == 1) {
                        values.add(response.getResponse());
                    } else {
                        List<String> responses = Parser.fromJson(response.getResponse(), new TypeToken<List<String>>() {
                        }.getType());
                        for (String value: responses) {
                            values.add(value);
                        }
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("Failed to forward get request to node {}: {}", nodeId, e.getMessage());
                status = RpcStatus.ERROR;
            }
        }
        if (values.size() == 1) {
            if (Objects.isNull(values.get(0)))
                return ResponseFactory.builder().setStatus(RpcStatus.NOT_FOUND);
            else
                return ResponseFactory.builder().setStatus(status).setResponse(values.get(0));
        }

        return ResponseFactory.builder().setStatus(status).setResponse(values.toString());
    }

    public RpcResponse.Builder forwardPut(Map<String, List<CacheEntry>> entriesByNode) {
        for (String nodeId : entriesByNode.keySet()) {
            if (!clusterMap.containsNode(nodeId)) {
                return ResponseFactory.builder().setStatus(RpcStatus.BAD_REQUEST)
                        .setResponse("Node " + nodeId + " not found in cluster");
            }
        }
        List<String> failedKeys = new ArrayList<>();
        for (String nodeId : entriesByNode.keySet()) {
            List<CacheEntry> entries = entriesByNode.get(nodeId);
            List<String> failedPuts = clientManager.getClient(nodeId).put(entries);
            if (!failedKeys.isEmpty()) {
                failedKeys.addAll(failedPuts);
            }
        }
        if (failedKeys.isEmpty()) {
            return ResponseFactory.builder().setStatus(RpcStatus.OK);
        } else {
            return ResponseFactory.builder().setStatus(RpcStatus.PARTIAL_FULFILLMENT)
                    .setResponse("Failed to put keys: " + String.join(", ", failedKeys));
        }
    }

    public RpcResponse.Builder forwardToPrimary(RpcRequest request) {
        String primaryId = clusterMap.getMyPrimary(currentNode.getNodeId()).getNodeId();
        return forwardToNode(request, primaryId);
    }

    public RpcResponse.Builder forwardToNode(RpcRequest request, String nodeId) {
        try {
            return RpcResponse.newBuilder(clientManager.getClient(nodeId).call(request).get());
        } catch (MemoraException | InterruptedException | ExecutionException e) {
            log.error("Failed to forward request to node {}: {}", nodeId, e.getMessage());
            return ResponseFactory.builder().setStatus(RpcStatus.ERROR)
                    .setResponse("Failed to forward request to node");
        }
    }

    public void behave(NodeType type) {
        currentNode.setType(type);
        clusterMap.clear();
        if (type.equals(NodeType.PRIMARY)) {
            clusterMap.addPrimary(currentNode);
        }
    }

    public void join(NodeBase base, List<String> buckets) {
        try {
            String newPrimaryId = clientManager.createAndAdd(base);
            MemoraClient client = clientManager.getClient(newPrimaryId);

            int count = Integer.valueOf(client.getPrimariesCount().get().getResponse());
            List<String> primaries = List.copyOf(clusterMap.getPrimaries());
            if (count == primaries.size() + 1) {
                processNewPrimaryJoined(newPrimaryId);
                return;
            }

            List<CompletableFuture<RpcResponse>> futures = new ArrayList<>();
            futures.add(client.replicateClusterMap(clusterMap));
            futures.add(client.replicateBucketIds(currentNode.getNodeId(), buckets));
            resolveFutures(futures);

            int curCount = Integer.valueOf(client.getPrimariesCount().get().getResponse());
            if (curCount == primaries.size() + 1) {
                for (String primary : primaries) {
                    if (currentNode.getNodeId().equals(primary)) {
                        futures.add(
                                CompletableFuture.supplyAsync(() -> {
                                    this.processNewPrimaryJoined(newPrimaryId);
                                    return null;
                                },
                                        threadPoolService.getThreadPool(ThreadPool.HIGHER_THREAD_POOL)));
                    } else {
                        futures.add(clientManager.getClient(primary).join(base));
                    }
                }
            }

            resolveFutures(futures);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Partial scaling occured");
        }
    }

    public void processNewPrimaryJoined(String newPrimaryId) {
        try {
            MemoraClient client = clientManager.getClient(newPrimaryId);
            NodeInfo info = Parser.fromJson(client.getNodeInfo().get().getResponse(), NodeInfo.class);
            List<NodeInfo> replicas = Parser.fromJson(client.getReplicas().get().getResponse(),
                    new TypeToken<List<NodeInfo>>() {
                    }.getType());
            clusterMap.addPrimary(info);
            for (NodeInfo replica : replicas) {
                clusterMap.addReplica(newPrimaryId,
                        NodeInfo.create(replica.getNodeId(), replica.getHost(), replica.getPort()));
            }

            List<String> newBucketIds = Parser.fromJson(client.getBucketIds().get().getResponse(),
                    new TypeToken<List<String>>() {
                    }.getType());

            replicationManager.migrate(newPrimaryId, newBucketIds);
            System.out.println("WOWOWOWOWOWWOWW");
            // Bucket add logic
        } catch (Exception e) {
            throw new RuntimeException("Unable to process new primary " + e.getMessage());
        }
    }

    public void handleAddNodes(List<NodeBase> nodes, boolean addPrimary, List<String> bucketIds)
            throws InterruptedException, ExecutionException {
        List<String> nodeIds = clusterMap.sort(clientManager.createAndAddAll(nodes));
        int nodePicker = 0;
        int replicationFactor = replicationManager.getReplicationFactor();

        List<CompletableFuture<RpcResponse>> futures = new ArrayList<>();
        NodeBase newPrimary = null;

        if (addPrimary) {
            newPrimary = nodes.get(nodePicker);
            String primaryId = nodeIds.get(nodePicker);
            clusterMap.validateNewPrimary(primaryId);
            clientManager.getClient(primaryId).behave(NodeType.PRIMARY).get();
            nodePicker++;

            while (nodePicker < nodes.size() && replicationFactor-- > 0) {
                String replicaId = nodeIds.get(nodePicker++);

                futures.add(clientManager.getClient(replicaId).replicate(newPrimary));
            }
        }

        List<String> primaries = List.copyOf(clusterMap.getPrimaries());
        for (String primary : primaries) {
            NodeInfo primaryNode = clusterMap.getNode(primary);
            int replicasNeeded = replicationManager.getReplicationFactor()
                    - clusterMap.getReplicaIds(primary).size();
            while (nodePicker < nodes.size() && replicasNeeded-- > 0) {
                String replicaId = nodeIds.get(nodePicker++);
                futures.add(clientManager.getClient(replicaId).replicate(primaryNode.getHost(),
                        primaryNode.getPort()));
            }
        }

        while (nodePicker < nodes.size()) {
            NodeInfo primary = clusterMap.getNode(primaries.get(nodePicker % primaries.size()));
            futures.add(clientManager.getClient(nodeIds.get(nodePicker++)).replicate(primary.getHost(),
                    primary.getPort()));
        }

        resolveFutures(futures);

        if (Objects.nonNull(newPrimary)) {
            for (String primary : primaries) {
                if (currentNode.getNodeId().equals(primary)) {
                    NodeBase newPrimaryBase = newPrimary;
                    futures.add(
                            CompletableFuture.supplyAsync(() -> {
                                this.join(newPrimaryBase, bucketIds);
                                return null;
                            },
                                    threadPoolService.getThreadPool(ThreadPool.HIGHER_THREAD_POOL)));
                } else {
                    futures.add(clientManager.getClient(primary).join(newPrimary));
                }
            }
        }

        resolveFutures(futures);
    }

    public void resolveFutures(List<CompletableFuture<RpcResponse>> futures)
            throws InterruptedException, ExecutionException {
        for (CompletableFuture<RpcResponse> future : futures)
            future.get();
        futures.clear();
    }

    public String getClusterLeader() {
        return clusterMap.getClusterLeader();
    }

    public void buildCluster() {
        if (!NodeType.STANDALONE.equals(currentNode.getType())) {
            log.info("Cluster already built.");
            return;
        }
        for (ThreadPool pool : ThreadPool.getAllThreadPool()) {
            if (pool.isCluster()) {
                threadPoolService.createThreadPool(pool);
            }
        }
    }
}