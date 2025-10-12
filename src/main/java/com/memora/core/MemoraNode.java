package com.memora.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.memora.enums.ThreadPool;
import com.memora.model.BucketInfo;
import com.memora.model.CacheEntry;
import com.memora.model.ClusterMap;
import com.memora.model.NodeInfo;
import com.memora.model.NodeBase;
import com.memora.services.BucketManager;
import com.memora.services.ClusterOrchestrator;
import com.memora.services.ReplicationManager;
import com.memora.services.ThreadPoolService;
import com.memora.utils.QPS;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoraNode {

    private final NodeInfo info;
    private final Version version;
    private final BucketManager bucketManager;
    private final ThreadPoolService threadPoolService;
    private final List<NodeBase> myReplicas;
    private final Provider<ClusterOrchestrator> clusterOrchestratorProvider;
    private final Provider<ReplicationManager> replicationManagerProvider;

    private ClusterOrchestrator clusterOrchestrator;
    private ReplicationManager replicationManager;

    @Inject
    public MemoraNode(
            final NodeInfo nodeInfo,
            final Version version,
            final List<NodeBase> myReplicas,
            final ThreadPoolService threadPoolService,
            final BucketManager bucketManager,
            final Provider<ClusterOrchestrator> clusterOrchestratorProvider,
            final Provider<ReplicationManager> replicationManagerProvider
    ) {
        this.info = nodeInfo;
        this.version = version;
        this.bucketManager = bucketManager;
        this.threadPoolService = threadPoolService;
        this.clusterOrchestratorProvider = clusterOrchestratorProvider;
        this.replicationManagerProvider = replicationManagerProvider;
        this.myReplicas = myReplicas;

        log.info("Node initialized with ID: {}, Host: {}, Port: {}", info.getNodeId(), info.getHost(), info.getPort());
    }

    public void start() {
        threadPoolService.submitAfter(ThreadPool.GENERAL_THREAD_POOL, () -> {
            QPS qps = new QPS(threadPoolService);
            qps.initialize();
            if (!myReplicas.isEmpty()) {
                log.info("Registering replicas...");
                myReplicas.forEach(replica -> {
                    log.info("Registering replica: {}:{}", replica.getHost(), replica.getPort());
                    this.primarize(replica.getHost(), replica.getPort());
                });
            }
        }, 2);

        log.info("Node started successfully.");
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

    // public RpcResponse forwardToPrimary(RpcRequest request) {
    //     return getClusterOrchestrator().forwardToPrimary(request);
    // }

    public void put(String key, CacheEntry value) {
        increaseQPS();
        bucketManager.put(key, value);
        version.increment();

        if (info.isPrimary()) {
            getReplicationManager().put(key, value);
        }
    }

    public void putAll(Map<String, CacheEntry> entries) {
        increaseQPS();
        bucketManager.putAll(entries);
        version.increment();

        if (info.isPrimary()) {
            getReplicationManager().putAll(entries);
        }

    }

    public void delete(String key) {
        increaseQPS();
        bucketManager.delete(key);
        version.increment();

        if (info.isPrimary()) {
            getReplicationManager().delete(key);
        }
    }

    public CacheEntry get(String key) {
        increaseQPS();
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

    private void increaseQPS() {
        threadPoolService.submit(ThreadPool.GENERAL_THREAD_POOL, QPS.getInstance()::increase);
    }
}
