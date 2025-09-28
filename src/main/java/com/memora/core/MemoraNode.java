package com.memora.core;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import com.memora.model.NodeInfo;
import com.memora.modules.EnvironmentModule;
import com.memora.services.BucketManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoraNode {
    private final NodeInfo info;
    private final MemoraServer server;
    private static final AtomicInteger nodeVersion = new AtomicInteger(0);
    private static final MemoraNode INSTANCE = new MemoraNode();

    private MemoraNode() {
        String nodeId = EnvironmentModule.getNodeId();
        String host = EnvironmentModule.getHost();
        int port = EnvironmentModule.getPort();
        this.info = NodeInfo.create(nodeId, host, port);
        log.info("Node initialized with ID: {}, Host: {}, Port: {}", nodeId, host, port);
    
        int numberOfBuckets = EnvironmentModule.getNumberOfBuckets();
        BucketManager.buildBuckets(numberOfBuckets);
        server = new MemoraServer(port);
    }
    
    public static MemoraNode getInstance() {
        return INSTANCE;
    }
    
    public void start() {
        try (server) {
            server.start();
        } catch (Exception e) {
            log.error("Error starting MemoraServer: {}", e.getMessage());
        }
    }
    
    public void stop() {
        try {
            server.close();
        } catch (IOException e) {

        }
    }

    public static void incrementVersion() {
        nodeVersion.incrementAndGet();
    }

    public static Integer getVersion() {
        return nodeVersion.get();
    }
}