package com.memora.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.inject.Inject;
import com.memora.core.MemoraClient;
import com.memora.model.ClusterMap;
import com.memora.model.NodeInfo;
import com.memora.model.RpcResponse;

import lombok.extern.slf4j.Slf4j;

import com.memora.enums.NodeType;

@Slf4j
public class ClusterOrchestrator {

    private final NodeInfo currentNode;
    private final List<String> inSyncReplicas;
    private final ClusterMap clusterMap;
    private final Map<String, MemoraClient> clientMap;

    @Inject
    public ClusterOrchestrator(NodeInfo currentNode) {
        this.currentNode = currentNode;
        this.inSyncReplicas = new ArrayList<>();
        this.clusterMap = new ClusterMap(0);
        this.clientMap = new HashMap<>();
    }

    public void clearInSyncReplicas() {
        inSyncReplicas.clear();
    }

    public void addPrimary(NodeInfo nodeInfo) {
        clusterMap.addPrimary(nodeInfo);
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
                        NodeInfo myPrimary = clusterMap.getMyPrimary(currentNode);
                        clientMap.get(myPrimary.getNodeId()).primarize(host, port);
                        return;
                    }
                }
            }
            final RpcResponse response = client.getNodeId();
            final String replicaId = response.getResponse();

            /**
             * Removing current node as replica
             * And adding current node as primary to the recieved replicaId
             */
            NodeInfo replica = NodeInfo.create(replicaId, host, port);
            clusterMap.removePrimary(replica);
            clientMap.put(replicaId, client);
            clusterMap.addReplica(currentNode.getNodeId(), replica);
            clusterMap.incrementEpoch();
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
                    List<String> replicas = clusterMap.getReplicas(currentNode.getNodeId());
                    replicas.forEach(replica -> {
                        clientMap.get(replica).replicate(host, port);
                    });
                }
            }
            currentNode.setNodeType(NodeType.REPLICA);
            final RpcResponse response = client.getNodeId();
            final String primaryId = response.getResponse();
            final NodeInfo primary = NodeInfo.create(primaryId, host, port);
            /**
             * Removing current node as primary
             * And adding current node as replica to the recieved primaryId
             */
            clusterMap.removePrimary(currentNode);
            clientMap.put(primaryId, client);
            clusterMap.addReplica(primaryId, currentNode);

            client.primarize(currentNode.getHost(), currentNode.getPort());
            addPrimary(primary);
        } catch (IOException | RuntimeException e) {
            log.error("Failed to replicate to {}:{}", host, port);
            throw new RuntimeException(e);
        }
        
    }
}