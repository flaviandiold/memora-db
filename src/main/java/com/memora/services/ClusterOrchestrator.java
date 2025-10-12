package com.memora.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.inject.Inject;
import com.memora.core.MemoraClient;
import com.memora.model.CacheEntry;
import com.memora.model.ClusterMap;
import com.memora.model.NodeInfo;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;

import lombok.extern.slf4j.Slf4j;

import com.memora.enums.NodeType;
import com.memora.enums.ThreadPool;
import com.memora.utils.Parser;

@Slf4j
public final class ClusterOrchestrator {

    private final NodeInfo currentNode;
    private final ReplicationManager replicationManager;
    private final RoutingService routingService;
    private final ThreadPoolService threadPoolService;

    private final ClusterMap clusterMap;

    @Inject
    public ClusterOrchestrator(NodeInfo currentNode, ReplicationManager replicationManager, RoutingService routingService, ThreadPoolService threadPoolService, ClusterMap clusterMap) {
        log.info("Starting cluster orchestrator...");
        this.currentNode = currentNode;
        this.replicationManager = replicationManager;
        this.threadPoolService = threadPoolService;
        this.routingService = routingService;
        this.clusterMap = clusterMap;
        buildCluster();
    }

    public void clearInSyncReplicas() {
        replicationManager.clearInSyncReplicas();
    }

    public ClusterMap getMap() {
        return clusterMap;
    }

    // public RpcResponse forwardToPrimary(RpcRequest request) {

    // }

    /**
     * Function that will make the given node at host and port
     * as a replica, and will start streaming data to it.
     * 
     * @param host
     * @param port
     */
    public void primarize(String host, int port) {
        try {
            final MemoraClient client = new MemoraClient(host, port);
            if (currentNode.equals(client.getHost(), client.getPort())) {
                throw new RuntimeException("Cannot replicate to self");
            }
            if (!NodeType.PRIMARY.equals(currentNode.getNodeType())) {
                switch (currentNode.getNodeType()) {
                    case STANDALONE -> {
                        currentNode.setNodeType(NodeType.PRIMARY);
                        clusterMap.addPrimary(currentNode);
                    }
                    case REPLICA -> {
                        NodeInfo myPrimary = clusterMap.getMyPrimary(currentNode.getNodeId());
                        routingService.getOrCreate(myPrimary).primarize(host, port);
                        return;
                    }
                }
            }
            final RpcResponse response = client.getNodeInfo();
            final NodeInfo replicaInfo = Parser.fromJson(response.getResponse(), NodeInfo.class);
            if (replicaInfo.getNodeType().equals(NodeType.PRIMARY)) {
                throw new RuntimeException("Cannot primarize to a primary node");
            }

            log.info("Primarizing to replica: {}:{}", replicaInfo.getHost(), replicaInfo.getPort());
            final String replicaId = replicaInfo.getNodeId();

            /**
             * Removing current node as replica
             * And adding current node as primary to the recieved replicaId
             */
            NodeInfo replica = NodeInfo.create(replicaId, host, port);
            clusterMap.removePrimary(replica);
            routingService.addClient(replicaId, client);
            clusterMap.incrementEpoch();
            client.replicate(currentNode.getHost(), currentNode.getPort());
            replicationManager.replicateDataTo(replica);
        } catch (IOException | RuntimeException e) {
            log.error("Failed to primarize to {}:{}", host, port);
            throw new RuntimeException(e);
        }
    }
    
    
    /**
     * Function that will initiate replication of the data
     * from the node at given host and port.
     * 
     * @param host
     * @param port
     */
    public void replicate(String host, int port) {
        try {
            final MemoraClient client = new MemoraClient(host, port);
            if (currentNode.equals(client.getHost(), client.getPort())) {
                throw new RuntimeException("Cannot replicate to self");
            }
            switch (currentNode.getNodeType()) {
                case PRIMARY -> {
                    List<String> replicas = clusterMap.getReplicaIds(currentNode.getNodeId());
                    replicas.forEach(replica -> {
                        routingService.getClient(replica).replicate(host, port);
                    });
                }
            }
            currentNode.setNodeType(NodeType.REPLICA);
            final RpcResponse response = client.getNodeInfo();
            final NodeInfo nodeInfo = Parser.fromJson(response.getResponse(), NodeInfo.class);
            // if 
            final String primaryId = nodeInfo.getNodeId();
            final String replicaId = currentNode.getNodeId();
            if (clusterMap.isPrimaryOf(replicaId, primaryId)) return;
            final NodeInfo primary = NodeInfo.create(primaryId, host, port);
            /**
             * Removing current node as primary
             * And adding current node as replica to the recieved primaryId
             */
            clusterMap.removePrimary(currentNode);
            routingService.addClient(primaryId, client);
            clusterMap.addReplica(primaryId, currentNode);
            
            replicationManager.initiateReplicationOf(primary);
            clusterMap.addPrimary(primary);
            String primaryMap = client.call("INFO CLUSTER MAP").getResponse();
            ClusterMap map = Parser.fromJson(primaryMap, ClusterMap.class);
            clusterMap.merge(map);
        } catch (IOException | RuntimeException e) {
            log.error("Failed to replicate to {}:{} {}", host, port, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public RpcResponse forwardPut(Map<String, List<CacheEntry>> entriesByNode) {
        for (String nodeId : entriesByNode.keySet()) {
            if (!clusterMap.containsNode(nodeId)) {
                return RpcResponse.BAD_REQUEST("Node " + nodeId + " not found in cluster");
            }
        }
        List<String> failedKeys = new ArrayList<>();
        for (String nodeId : entriesByNode.keySet()) {
            List<CacheEntry> entries = entriesByNode.get(nodeId);
            boolean allDone = routingService.getClient(nodeId).put(entries);
            if (!allDone) {
                failedKeys.addAll(entries.stream().map(CacheEntry::getKey).toList());
            }
        }
        if (failedKeys.isEmpty()) {
            return RpcResponse.OK;
        } else {
            return RpcResponse.PARTIAL_FULFILLMENT("Failed to put keys: " + String.join(", ", failedKeys));
        }
    }

    public RpcResponse forwardToPrimary(String request) {
        String primaryId = clusterMap.getMyPrimary(currentNode.getNodeId()).getNodeId();
        return routingService.getClient(primaryId).call(request);
    }

    public void buildCluster() {
        if (!NodeType.STANDALONE.equals(currentNode.getNodeType())) {
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