package com.memora.core;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.memora.constants.NodeType;
import com.memora.model.NodeInfo;
import com.memora.modules.EnvironmentModule;
import com.memora.services.BucketManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoraNode {
    private final NodeInfo info;
    private final MemoraServer server;

    private static final AtomicLong nodeVersion = new AtomicLong(1);
    private static final MemoraNode INSTANCE = new MemoraNode();

    private NodeType nodeType;
    private List<NodeInfo> replicaNodes;
    private List<NodeInfo> inSyncReplicas;

    private MemoraNode() {
        String nodeId = EnvironmentModule.getNodeId();
        String host = EnvironmentModule.getHost();
        int port = EnvironmentModule.getPort();
        this.info = NodeInfo.create(nodeId, host, port);
        log.info("Node initialized with ID: {}, Host: {}, Port: {}", nodeId, host, port);
    
        int numberOfBuckets = EnvironmentModule.getNumberOfBuckets();
        BucketManager.buildBuckets(numberOfBuckets);
        server = new MemoraServer(host, port);

        nodeType = NodeType.STANDALONE;
    }
    
    public static void start() {
        try (INSTANCE.server) {
            INSTANCE.server.start();
        } catch (Exception e) {
            log.error("Error starting MemoraServer: {}", e.getMessage());
        }
    }
    
    public static void stop() {
        try {
            INSTANCE.server.close();
        } catch (IOException e) {

        }
    }

    public static void clearInSyncReplicas() {
        if (INSTANCE.nodeType == NodeType.PRIMARY) INSTANCE.inSyncReplicas.clear();
    }

    public static void incrementVersion() {
        nodeVersion.incrementAndGet();
        clearInSyncReplicas();
    }

    public static long getVersion() {
        return nodeVersion.get();
    }
}