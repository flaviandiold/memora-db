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

import static java.lang.String.format;

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

    public void primarize(String host, int port) {
        log.info("PRIMARIZING host {} at port {}", host, port);
    }


    public void replicate(String host, int port) {
        try {
            final MemoraClient client = new MemoraClient(host, port);
            final RpcResponse response = client.getNodeId();
            final String primaryId = response.getResponse();

            /**
             * Removing current node as primary
             * And adding current node as replica to the recieved primaryId
             */
            clusterMap.removePrimary(currentNode);
            clientMap.put(primaryId, client);
            clusterMap.addReplica(primaryId, currentNode);
            
            client.call(format("NODE PRIMARIZE %s@%d", currentNode.getHost(), currentNode.getPort()));
        } catch (IOException | RuntimeException e) {
            log.error("Failed to replicate to {}:{}", host, port);
            throw new RuntimeException(e);
        }
        
    }
}