package com.memora.core;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.memora.enums.NodeType;
import com.memora.model.NodeInfo;
import com.memora.services.ClusterOrchestrator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoraNode {

    private final NodeInfo info;
    private final Provider<ClusterOrchestrator> clusterOrchestratorProvider;

    private ClusterOrchestrator clusterOrchestrator;

    @Inject
    public MemoraNode(
            NodeInfo nodeInfo,
            Provider<ClusterOrchestrator> clusterOrchestratorProvider
    ) {
        this.info = nodeInfo;
        this.clusterOrchestratorProvider = clusterOrchestratorProvider;
        log.info("Node initialized with ID: {}, Host: {}, Port: {}", info.getNodeId(), info.getHost(), info.getPort());
    }

    public void start() {
        log.info("Starting Memora Node...");
    }

    public void stop() {
        log.info("Stopping Memora Node");
    }

    public void clearInSyncReplicas() {
        if (NodeType.PRIMARY.equals(info.getNodeType())) {
            clusterOrchestrator.clearInSyncReplicas();
        }
    }

    public void primarize(String host, int port) {
        buildCluster();
        clusterOrchestrator.primarize(host, port);
    }

    public void replicate(String host, int port) {
        buildCluster();
        clusterOrchestrator.replicate(host, port);
    }

    private void buildCluster() {
        if (!NodeType.STANDALONE.equals(info.getNodeType())) {
            log.info("Cluster already built.");
            return;
        }
        log.info("Building cluster...");
        clusterOrchestrator = clusterOrchestratorProvider.get();
    }
}
