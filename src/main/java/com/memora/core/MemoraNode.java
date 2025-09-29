package com.memora.core;

import com.google.inject.Inject;
import com.memora.enums.NodeType;
import com.memora.model.NodeInfo;
import com.memora.services.ClusterOrchestrator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoraNode {

    private final NodeInfo info;

    private NodeType nodeType;
    private ClusterOrchestrator clusterOrchestrator;

    @Inject
    public MemoraNode(
            NodeInfo nodeInfo
    ) {
        this.info = nodeInfo;
        log.info("Node initialized with ID: {}, Host: {}, Port: {}", info.getNodeId(), info.getHost(), info.getPort());
    }

    public void start() {
        log.info("Starting Memora Node...");
        nodeType = NodeType.STANDALONE;
    }

    public void stop() {
        log.info("Stopping Memora Node");
    }

    public void clearInSyncReplicas() {
        if (!NodeType.STANDALONE.equals(nodeType)) {
            clusterOrchestrator.clearInSyncReplicas();
        }
    }

    public NodeType getNodeType() {
        return nodeType;
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
        if (!NodeType.STANDALONE.equals(nodeType)) {
            return;
        }
        clusterOrchestrator = new ClusterOrchestrator(info);
    }
}
