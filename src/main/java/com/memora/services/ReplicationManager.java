package com.memora.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.memora.core.MemoraClient;
import com.memora.enums.ThreadPool;
import com.memora.model.BucketInfo;
import com.memora.model.CacheEntry;
import com.memora.model.ClusterMap;
import com.memora.model.NodeInfo;
import com.memora.model.RpcResponse;
import com.memora.store.Bucket;
import com.memora.utils.Parser;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReplicationManager {

    private final NodeInfo currentNode;
    private final BucketManager bucketManager;
    private final RoutingService routingService;
    private final ThreadPoolService threadPoolService;
    private final ClusterMap clusterMap;
    private final ArrayList<NodeInfo> inSyncReplicas;

    private final ThreadPool pool = ThreadPool.REPLICATION_THREAD_POOL;

    @Inject
    public ReplicationManager(NodeInfo currentNode, BucketManager bucketManager, RoutingService routingService, ThreadPoolService threadPoolService, ClusterMap clusterMap) {
        this.currentNode = currentNode;
        this.bucketManager = bucketManager;
        this.routingService = routingService;
        this.threadPoolService = threadPoolService;
        this.clusterMap = clusterMap;
        this.inSyncReplicas = new ArrayList<>();
    }

    public void put(String key, CacheEntry value) {
        threadPoolService.submit(pool, () -> {
            List<NodeInfo> replicas = clusterMap.getReplicas(currentNode.getNodeId());
            replicas.stream().map(replica -> {
                return threadPoolService.submit(pool, () -> {
                    return routingService.getOrCreate(replica).put(key, value.getValue(), value.getTtl());
                });
            });
        });
    }

    public void putAll(Map<String, CacheEntry> entries) {
        threadPoolService.submit(pool, () -> {
            List<NodeInfo> replicas = clusterMap.getReplicas(currentNode.getNodeId());
            replicas.stream().map(replica -> {
                return threadPoolService.submit(pool, () -> {
                    return routingService.getOrCreate(replica).put(entries);
                });
            });
        });
    }

    public void delete(String key) {
        threadPoolService.submit(pool, () -> {
            List<NodeInfo> replicas = clusterMap.getReplicas(currentNode.getNodeId());
            List<Future<Boolean>> futures = replicas.stream().map(replica -> {
                return threadPoolService.submit(pool, () -> {
                    return routingService.getOrCreate(replica).delete(key);
                });
            })
                    .toList();
        });
    }

    public void replicateDataTo(NodeInfo replica) throws IOException {
        // This call can throw an exception, so it's handled synchronously before the async part.
        final MemoraClient client = routingService.getOrCreate(replica);

        List<Bucket> buckets = bucketManager.getSelfBuckets();

        List<CompletableFuture<Boolean>> futures = buckets.stream()
                .map(bucket -> CompletableFuture.supplyAsync(() -> {
            log.info("Replicating bucket {}", bucket.getId());
            return bucket.stream(client, threadPoolService.getThreadPool(pool));
        }, threadPoolService.getThreadPool(pool))).toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenRun(() -> {
                // This block runs only after all bucket streams are done.
                // We check if any of them failed or returned false.
                    boolean allBucketsSynced = futures.stream()
                            .allMatch(future -> {
                // Check for exceptions first to avoid re-throwing with join()
                                if (future.isCompletedExceptionally()) {
                                    return false;
                                }
                // Get the result of the completed future.
                                return future.join();
                            });

                    if (allBucketsSynced) {
                        clusterMap.addReplica(currentNode.getNodeId(), replica);
                        log.info("Replication to {} completed successfully.", replica.getNodeId());
                    } else {
                        log.error("Replication to {} failed for one or more buckets.", replica.getNodeId());
                    }
                }).exceptionally(ex -> {
            // This will catch any exceptions from the async pipeline itself.
            log.error("An unexpected error occurred during replication: {}", ex.getMessage());
            return null;
        });
    }

    public void initiateReplicationOf(NodeInfo primary) throws IOException {
        MemoraClient client = routingService.getOrCreate(primary);

        RpcResponse response = client.call("INFO BUCKET MAP");
        List<BucketInfo> bucketInfo = Parser.fromJson(response.getResponse(), new TypeToken<List<BucketInfo>>() {
        }.getType());
        bucketManager.createFromPrimary(bucketInfo);
        client.primarize(currentNode.getHost(), currentNode.getPort());
    }

    public void clearInSyncReplicas() {
        inSyncReplicas.clear();
    }
}
