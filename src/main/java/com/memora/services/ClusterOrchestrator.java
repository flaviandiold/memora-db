package com.memora.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.google.inject.Inject;
import com.memora.core.MemoraClient;
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

    @Inject
    public ClusterOrchestrator(NodeInfo currentNode, ReplicationManager replicationManager, ClientManager clientManager, ThreadPoolService threadPoolService, ClusterMap clusterMap) {
        log.info("Starting cluster orchestrator...");
        this.currentNode = currentNode;
        this.replicationManager = replicationManager;
        this.threadPoolService = threadPoolService;
        this.clientManager = clientManager;
        this.clusterMap = clusterMap;
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
     * @param host
     * @param port
     */
    public void primarize(String host, int port) {
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
            final MemoraClient client = clientManager.create(base);
            final RpcResponse response = client.getNodeInfo().get();
            final NodeInfo replicaInfo = Parser.fromJson(response.getResponse(), NodeInfo.class);
            if (replicaInfo.getType().equals(NodeType.PRIMARY)) {
                throw new MemoraException("Cannot primarize to a primary node");
            }

            log.info("Primarizing to replica: {}:{}", replicaInfo.getHost(), replicaInfo.getPort());
            final String replicaId = replicaInfo.getNodeId();

            /**
             * Removing current node as replica
             * And adding current node as primary to the recieved replicaId
             */
            NodeInfo replica = NodeInfo.create(replicaId, host, port);
            clusterMap.removePrimary(replica);
            clientManager.addClient(replicaId, client);
            clusterMap.incrementEpoch();
            client.replicate(currentNode.getHost(), currentNode.getPort());
            replicationManager.replicateDataTo(replica);
        } catch (IOException | InterruptedException | ExecutionException | RuntimeException e) {
            log.error("Failed to primarize to {}:{}", host, port);
            throw new RuntimeException(e.getMessage());
        }
    }
    
    
    /**
     * Function that will initiate replication of the data
     * from the node at given host and port.
     * 
     * @param host
     * @param port
     */
    public void replicate(String host, int port, long clusterEpoch) {
        try {
            final NodeBase base = clientManager.getAddress(host, port);
            if (currentNode.equals(base.getHost(), base.getPort())) {
                throw new MemoraException("Cannot replicate to self");
            }
            switch (currentNode.getType()) {
                case PRIMARY -> {
                    List<String> replicas = clusterMap.getReplicaIds(currentNode.getNodeId());
                    replicas.forEach(replica -> {
                        clientManager.getClient(replica).replicate(host, port);
                    });
                }
            }
            final MemoraClient client = clientManager.create(base);
            currentNode.setType(NodeType.REPLICA);
            final RpcResponse response = client.getNodeInfo().get();
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
            clientManager.addClient(primaryId, client);
            clusterMap.addReplica(primaryId, currentNode);
            
            replicationManager.initiateReplicationOf(primary);
            clusterMap.addPrimary(primary);
            String primaryMap = client.call("INFO CLUSTER MAP").get().getResponse();
            ClusterMap map = Parser.fromJson(primaryMap, ClusterMap.class);
            clusterMap.merge(map, clusterEpoch);
        } catch (IOException | InterruptedException | ExecutionException | RuntimeException e) {
            log.error("Failed to replicate to {}:{} {}", host, port, e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    public RpcResponse.Builder forwardPut(Map<String, List<CacheEntry>> entriesByNode) {
        for (String nodeId : entriesByNode.keySet()) {
            if (!clusterMap.containsNode(nodeId)) {
                return ResponseFactory.builder().setStatus(RpcStatus.BAD_REQUEST).setResponse("Node " + nodeId + " not found in cluster");
            }
        }
        List<String> failedKeys = new ArrayList<>();
        for (String nodeId : entriesByNode.keySet()) {
            List<CacheEntry> entries = entriesByNode.get(nodeId);
            boolean allDone = clientManager.getClient(nodeId).put(entries);
            if (!allDone) {
                failedKeys.addAll(entries.stream().map(CacheEntry::getKey).toList());
            }
        }
        if (failedKeys.isEmpty()) {
            return ResponseFactory.builder().setStatus(RpcStatus.OK);
        } else {
            return ResponseFactory.builder().setStatus(RpcStatus.PARTIAL_FULFILLMENT).setResponse("Failed to put keys: " + String.join(", ", failedKeys));
        }
    }

    public RpcResponse.Builder forwardToPrimary(RpcRequest request) {
        String primaryId = clusterMap.getMyPrimary(currentNode.getNodeId()).getNodeId();
        try {
            return RpcResponse.newBuilder(clientManager.getClient(primaryId).call(request).get());
        } catch (MemoraException | InterruptedException | ExecutionException e) {
            log.error("Failed to forward request to primary {}: {}", primaryId, e.getMessage());
            return ResponseFactory.builder().setStatus(RpcStatus.ERROR).setResponse("Failed to forward request to primary");
        }
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