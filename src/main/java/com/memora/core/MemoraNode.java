package com.memora.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.memora.model.BucketInfo;
import com.memora.model.CacheEntry;
import com.memora.model.ClusterMap;
import com.memora.model.NodeInfo;
import com.memora.services.BucketManager;
import com.memora.services.ClusterOrchestrator;
import com.memora.services.ReplicationManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoraNode {

    private final NodeInfo info;
    private final Version version;
    private final BucketManager bucketManager;
    private final Provider<ClusterOrchestrator> clusterOrchestratorProvider;
    private final Provider<ReplicationManager> replicationManagerProvider;

    private ClusterOrchestrator clusterOrchestrator;
    private ReplicationManager replicationManager;

    @Inject
    public MemoraNode(
            NodeInfo nodeInfo,
            Version version,
            BucketManager bucketManager,
            Provider<ClusterOrchestrator> clusterOrchestratorProvider,
            Provider<ReplicationManager> replicationManagerProvider
    ) {
        this.info = nodeInfo;
        this.version = version;
        this.bucketManager = bucketManager;
        this.clusterOrchestratorProvider = clusterOrchestratorProvider;
        this.replicationManagerProvider = replicationManagerProvider;
        log.info("Node initialized with ID: {}, Host: {}, Port: {}", info.getNodeId(), info.getHost(), info.getPort());
    }

    public NodeInfo getInfo() {
        return info;
    }

    public void incrementVersion() {
        version.increment();
    }

    public List<BucketInfo> getAllBuckets() {
        return bucketManager.getAllBuckets();
    }

    public void put(String key, CacheEntry value) {
        bucketManager.put(key, value);
        version.increment();

        if (info.isPrimary()) {
            getReplicationManager().put(key, value);
        }
    }

    public void putAll(Map<String, CacheEntry> entries) {
        bucketManager.putAll(entries);
        version.increment();

        if (info.isPrimary()) {
            getReplicationManager().putAll(entries);
        }
    }

    public void delete(String key) {
        bucketManager.delete(key);
        version.increment();

        if (info.isPrimary()) {
            getReplicationManager().delete(key);
        }
    }

    public CacheEntry get(String key) {
        return bucketManager.get(key);
    }

    public void replicate(String host, int port) {
        getClusterOrchestrator().replicate(host, port);
    }

    public void primarize(String host, int port) {
        getClusterOrchestrator().primarize(host, port);
    }

    public ClusterMap getClusterMap() {
        if (Objects.isNull(clusterOrchestrator)) {
            return null;
        }
        return getClusterOrchestrator().getMap();
    }

    private ClusterOrchestrator getClusterOrchestrator() {
        if (Objects.isNull(clusterOrchestrator)) {
            clusterOrchestrator = clusterOrchestratorProvider.get();
        }
        return clusterOrchestrator;
    }

    private ReplicationManager getReplicationManager() {
        if (Objects.isNull(replicationManager)) {
            replicationManager = replicationManagerProvider.get();
        }
        return replicationManager;
    }
}
