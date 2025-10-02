package com.memora.core;

import java.util.concurrent.atomic.AtomicLong;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.memora.model.NodeInfo;
import com.memora.services.ClusterOrchestrator;

public class Version {
    private final AtomicLong version = new AtomicLong(1);
    private final NodeInfo nodeInfo;
    private final Provider<ClusterOrchestrator> clusterOrchestratorProvider;
    private ClusterOrchestrator clusterOrchestrator;

    @Inject
    public Version(NodeInfo info, Provider<ClusterOrchestrator> clusterOrchestratorProvider) {
        this.nodeInfo = info;
        this.clusterOrchestratorProvider = clusterOrchestratorProvider;
    }

    public void increment() {
        version.incrementAndGet();
        if (nodeInfo.isPrimary()) {
            initialze();
            clusterOrchestrator.clearInSyncReplicas();
        }
    }
    
    public long get() {
        return version.get();
    }

    public void initialze() {
        if (clusterOrchestrator == null) {
            clusterOrchestrator = clusterOrchestratorProvider.get();
        }
    }
    
}
