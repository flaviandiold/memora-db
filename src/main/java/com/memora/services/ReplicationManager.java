package com.memora.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

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

    public void put(CacheEntry entry) {
        List<NodeInfo> replicas = clusterMap.getReplicas(currentNode.getNodeId());
        executeAsync(replicas, replica -> {
            try {
                return routingService.getOrCreate(replica)
                        .put(entry.getKey(), entry.getValue(), entry.getTtl());
            } catch (Exception e) {
                return false;
            }
        });
    }

    public void putAll(Collection<CacheEntry> entries) {
        List<NodeInfo> replicas = clusterMap.getReplicas(currentNode.getNodeId());
        executeAsync(replicas, replica -> {
            try {
                return routingService.getOrCreate(replica).put(entries, threadPoolService.getThreadPool(pool));
            } catch (Exception e) {
                return false;
            }
        });
    }

    public void delete(String key) {
        List<NodeInfo> replicas = clusterMap.getReplicas(currentNode.getNodeId());
        executeAsync(replicas, replica -> {
            try {
                return routingService.getOrCreate(replica).delete(key);
            } catch (Exception e) {
                return false;
            }
        });
    }

    public void replicateDataTo(NodeInfo replica) throws IOException {
        // This call can throw an exception, so it's handled synchronously before the async part.
        final MemoraClient client = routingService.getOrCreate(replica);

        List<Bucket> buckets = bucketManager.getSelfBuckets();

        executeAsync(buckets, bucket -> {
            log.info("Replicating bucket {}", bucket.getId());
            return bucket.stream(client, threadPoolService.getThreadPool(pool));
        }).thenAccept(success -> {
            if (success) {
                clusterMap.addReplica(currentNode.getNodeId(), replica);
            }
        })
        .exceptionally(ex -> {
            log.error("Replication failed with an exception. {}", ex);
            return null;
        });
    }

    private <T> CompletableFuture<Boolean> executeAsync(List<T> data, Function<T, Boolean> task) {
        if (data == null || data.isEmpty()) {
            log.warn("No data provided for replication operation '{}', completing as success.");
            return CompletableFuture.completedFuture(true);
        }

        List<CompletableFuture<Boolean>> futures = data.stream()
            .map(item -> CompletableFuture.supplyAsync(
                () -> task.apply(item),
                threadPoolService.getThreadPool(pool)
            ))
            .toList();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .thenApply(v -> { // Use thenApply to return the final boolean result
                boolean allSucceeded = futures.stream()
                    .allMatch(future -> {
                        if (future.isCompletedExceptionally()) return false;
                        return future.join();
                    });

                if (allSucceeded) {
                    log.info("Replication succeeded for all targets.");
                    // Potential logic: update in-sync replica state here
                } else {
                    log.error("Replication failed for one or more targets.");
                }
                return allSucceeded;
            })
            .exceptionally(ex -> {
                log.error("Replication failed with an exception.", ex);
                return false; // The operation failed
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
