package com.memora.core;

import com.memora.model.NodeInfo;
import com.memora.modules.EnvironmentModule;
import com.memora.services.BucketManager;
import com.memora.utils.ULID;

public class MemoraNode {
    private final NodeInfo nodeInfo;
    private final BucketManager bucketManager;

    public MemoraNode() {
        final String host = EnvironmentModule.getHost();
        final int port = EnvironmentModule.getPort();
        nodeInfo = NodeInfo.create(ULID.generate(), host, port);
        bucketManager = new BucketManager();
    }
    
    public void start() {
        System.out.println("MemoraNode started.");
        try (MemoraServer server = new MemoraServer(this.nodeInfo.port())) {
            server.start();
        } catch (Exception e) {
            System.err.println("Error starting MemoraServer: " + e.getMessage());
        }
    }
    
    public void stop() {
        System.out.println("MemoraNode stopped.");
    }
}