package com.memora.core;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.memora.enums.NodeType;
import com.memora.enums.ThreadPool;
import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.model.BucketInfo;
import com.memora.model.CacheEntry;
import com.memora.model.ClusterMap;
import com.memora.model.NodeInfo;
import com.memora.model.NodeBase;
import com.memora.services.BucketManager;
import com.memora.services.ClusterOrchestrator;
import com.memora.services.ForwarderService;
import com.memora.services.ThreadPoolService;
import com.memora.store.Bucket;
import com.memora.utils.QPS;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoraNode {

    private static NodeInfo info = null;

    private final BucketManager bucketManager;
    private final ThreadPoolService threadPoolService;
    private final Provider<ForwarderService> forwarderServiceProvider;
    private final Provider<ClusterOrchestrator> clusterOrchestratorProvider;

    private ForwarderService forwarderService;
    private ClusterOrchestrator clusterOrchestrator;

    @Inject
    public MemoraNode(
            final NodeInfo nodeInfo,
            final ThreadPoolService threadPoolService,
            final BucketManager bucketManager,
            final Provider<ForwarderService> forwarderServiceProvider,
            final Provider<ClusterOrchestrator> clusterOrchestratorProvider
    ) {
        info = nodeInfo;
        this.bucketManager = bucketManager;
        this.threadPoolService = threadPoolService;
        this.forwarderServiceProvider = forwarderServiceProvider;
        this.clusterOrchestratorProvider = clusterOrchestratorProvider;

        log.info("Node initialized with ID: {}, Host: {}, Port: {}", info.getNodeId(), info.getHost(), info.getPort());
    }

    public void start() {
        QPS qps = new QPS(threadPoolService);
        qps.initialize();

        log.info("Node started successfully.");
    }

    public static String getNodeId() {
        return info.getNodeId();
    }

    public static NodeInfo getInfo() {
        return info;
    }

    public int countOfKnownPrimaries() {
        return getClusterOrchestrator().getMap().getPrimaries().size();
    }

    public void handleMutation() {
        if (info.isPrimary()) {
            getClusterOrchestrator().clearInSyncReplicas();
        }
    }

    public List<BucketInfo> getAllBuckets() {
        return bucketManager.getAllBuckets();
    }

    public List<String> getSelfBuckets() {
        return bucketManager.getSelfBuckets().stream().map(Bucket::getId).toList();
    }

    public RpcResponse.Builder forwardToPrimary(RpcRequest request) {
        return getForwarderService().forwardToPrimary(request);
    }

    public RpcResponse.Builder forwardPut(Map<String, List<CacheEntry>> entriesByNode) {
        return getForwarderService().forwardPut(entriesByNode);
    }

    public RpcResponse.Builder forwardGet(Map<String, List<String>> nodeToKeysMap) {
        return getForwarderService().forwardGet(nodeToKeysMap);
    }

        
    public RpcResponse.Builder forwardToNode(RpcRequest request, String nodeId) {
        return getForwarderService().forwardToNode(request, nodeId);
    }

    public void addNodes(List<NodeBase> nodes, boolean addPrimary) {
        getClusterOrchestrator().handleAddNodes(nodes, addPrimary);
    }

    public List<String> getSelfKeys() {
        return bucketManager.getSelfKeys();
    }

    public void put(CacheEntry entry) {
        increaseQPS();
        bucketManager.put(entry);
        handleMutation();
    }

    public void putAll(final Collection<CacheEntry> entries) {
        increaseQPS();
        bucketManager.putAll(entries);
        handleMutation();
    }

    public void delete(String key) {
        increaseQPS();
        bucketManager.delete(key);
        handleMutation();
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


    public void join(NodeBase base) {
        getClusterOrchestrator().join(base, bucketManager.getSelfBucketIds());
    }

    public void copyBucketIds(String nodeId, List<String> bucketIds) {
        bucketManager.copyBucketIds(nodeId, bucketIds);
    }

    public void copyClusterMap(ClusterMap clusterMap) {
        getClusterOrchestrator().mergeClusterMap(clusterMap);
    }

    public void behave(NodeType type) {
        behave(type, true);
    }

    public void behave(NodeType type, boolean wipe) {
        getClusterOrchestrator().behave(type);
        if (wipe && type.equals(NodeType.PRIMARY)) {
            handleMutation();
            bucketManager.clear();
        }
    }

    public void remove(String nodeId) {
        getClusterOrchestrator().removeNode(nodeId);
    }

    public void forget(String nodeId, boolean force) {
        getClusterOrchestrator().forgetNode(nodeId, force);
    }

    public void buildCluster() {
        getClusterOrchestrator();
    }

    public String getClusterLeader() {
        return getClusterOrchestrator().getClusterLeader();
    }

    public ClusterMap getClusterMap() {
        if (Objects.isNull(clusterOrchestrator)) {
            return null;
        }
        return getClusterOrchestrator().getMap();
    }

    public Map<String, List<String>> getKeyToNodeMap(List<String> keys) {
        return bucketManager.getKeyToNodeMap(keys);
    }

    private ClusterOrchestrator getClusterOrchestrator() {
        if (Objects.isNull(clusterOrchestrator)) {
            clusterOrchestrator = clusterOrchestratorProvider.get();
        }
        return clusterOrchestrator;
    }

    private ForwarderService getForwarderService() {
        if (Objects.isNull(forwarderService)) {
            forwarderService = forwarderServiceProvider.get();
        }
        return forwarderService;
    }

    private void increaseQPS() {
        threadPoolService.submit(ThreadPool.LOWER_THREAD_POOL, QPS.getInstance()::increase);
    }
}
