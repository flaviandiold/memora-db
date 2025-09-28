package com.memora.core;

import java.util.List;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.memora.constants.Constants;
import com.memora.constants.NodeType;
import com.memora.model.NodeInfo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoraNode {
    private final NodeInfo info;
    
    private NodeType nodeType;
    private List<NodeInfo> replicaNodes;
    private List<NodeInfo> inSyncReplicas;

    @Inject
    public MemoraNode(
        @Named(Constants.NODE_ID) String nodeId,
        @Named(Constants.NODE_HOST) String host,
        @Named(Constants.NODE_PORT) int port
    ) {
        this.info = NodeInfo.create(nodeId, host, port);
        log.info("Node initialized with ID: {}, Host: {}, Port: {}", nodeId, host, port);
    }
    
    public void start() {
        nodeType = NodeType.STANDALONE;
        log.info("Starting Memora Node...");
    }
    
    public void stop() {
        log.info("Stopping Memora Node");
    }

    public void clearInSyncReplicas() {
        if (NodeType.PRIMARY.equals(nodeType)) inSyncReplicas.clear();
    }

    public void replicate(String host, int port) {
        nodeType = NodeType.REPLICA;
    }
}