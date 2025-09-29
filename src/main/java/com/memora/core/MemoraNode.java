package com.memora.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.inject.Inject;
import com.memora.enums.NodeType;
import com.memora.model.ClusterMap;
import com.memora.model.NodeInfo;
import com.memora.model.RpcResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoraNode {
    private final NodeInfo info;
    
    private final List<String> inSyncReplicas;
    private final ClusterMap clusterMap;
    private final Map<String, MemoraClient> clientMap;
    
    private NodeType nodeType;

    @Inject
    public MemoraNode(
        NodeInfo nodeInfo
    ) {
        this.info = nodeInfo;
        this.inSyncReplicas = new ArrayList<>();
        this.clusterMap = new ClusterMap(0);
        this.clientMap = new HashMap<>();
        log.info("Node initialized with ID: {}, Host: {}, Port: {}", info.getNodeId(), info.getHost(), info.getPort());
    }
    
    public void start() {
        log.info("Starting Memora Node...");
        nodeType = NodeType.STANDALONE;
        this.clusterMap.addPrimary(info);
    }
    
    public void stop() {
        log.info("Stopping Memora Node");
    }

    public void clearInSyncReplicas() {
        if (NodeType.PRIMARY.equals(nodeType)) inSyncReplicas.clear();
    }

    public void replicate(String host, int port) {
        try {
            nodeType = NodeType.REPLICA;
            
            MemoraClient client = new MemoraClient(host, port);
            RpcResponse response = client.call("INFO NODE ID");
            log.info("Response {}", response);
            // clusterMap.removePrimary(info);
            // clientMap.put(host + ":" + port, client);
        } catch (IOException | RuntimeException e) {
            log.error("Failed to replicate to {}:{}", host, port);
            throw new RuntimeException(e);
        }
        
    }
}