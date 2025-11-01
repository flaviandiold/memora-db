package com.memora.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import com.google.inject.Inject;
import com.memora.core.MemoraClient;
import com.memora.enums.ThreadPool;
import com.memora.messages.RpcResponse;
import com.memora.model.CacheEntry;
import com.memora.model.ClusterMap;
import com.memora.model.NodeInfo;
import com.memora.store.Bucket;

import lombok.extern.slf4j.Slf4j;

import static com.memora.utils.CommonUtils.resolveFutures;
import static com.memora.core.MemoraNode.getNodeId;;

@Slf4j
public class ReplicationManager {

    private final ThreadPoolService threadPoolService;
    private final ClientManager clientManager;
    private final ClusterMap clusterMap;
    private final int replicationFactor;
    private final List<NodeInfo> inSyncReplicas;

    private final ThreadPool pool = ThreadPool.REPLICATION_THREAD_POOL;

    @Inject
    public ReplicationManager(ClientManager clientManager,
        ThreadPoolService threadPoolService, ClusterMap clusterMap, int replicationFactor) {
        this.clientManager = clientManager;
        this.threadPoolService = threadPoolService;
        this.clusterMap = clusterMap;
        this.replicationFactor = replicationFactor;
        this.inSyncReplicas = new ArrayList<>();
    }


    public void put(CacheEntry entry) {
        List<NodeInfo> replicas = clusterMap.getReplicas(getNodeId());
        executeAsync(replicas, replica -> {
            try {
                return clientManager.getOrCreate(replica)
                        .put(entry.getKey(), entry.getValue(), entry.getTtl());
            } catch (Exception e) {
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    public void putAll(Collection<CacheEntry> entries) {
        List<NodeInfo> replicas = clusterMap.getReplicas(getNodeId());
        executeAsync(replicas, replica -> {
            try {
                return clientManager.getOrCreate(replica).putAsync(entries, threadPoolService.getThreadPool(pool));
            } catch (Exception e) {
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    public void delete(String key) {
        List<NodeInfo> replicas = clusterMap.getReplicas(getNodeId());
        executeAsync(replicas, replica -> {
            try {
                return clientManager.getOrCreate(replica).delete(key);
            } catch (Exception e) {
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    public void replicateDataTo(NodeInfo replica, List<Bucket> buckets) throws IOException, InterruptedException {
        // This call can throw an exception, so it's handled synchronously before the async part.
        final MemoraClient client = clientManager.getOrCreate(replica);

        executeAsync(buckets, bucket -> {
            log.info("Replicating bucket {}", bucket.getId());
            return bucket.stream(client, threadPoolService.getThreadPool(pool));
        }).exceptionally(ex -> {
            log.error("Replication failed with an exception. {}", ex);
            throw new RuntimeException(ex.getMessage());
        });
    }

    /**
     * Executes a list of NON-BLOCKING tasks in parallel.
     *
     * @param data The list of items to process.
     * @param task A function that takes an item and returns a CompletableFuture<Boolean>.
     * @return A single CompletableFuture that completes with 'true' only if all
     * tasks completed successfully.
     */
    private <T> CompletableFuture<Boolean> executeAsync(List<T> data, Function<T, CompletableFuture<Boolean>> task) {
        if (data == null || data.isEmpty()) {
            log.warn("No data provided for async execution, completing as success.");
            return CompletableFuture.completedFuture(true);
        }

        // Map each item to its asynchronous task
        List<CompletableFuture<Boolean>> futures = data.stream()
            .map(item -> task.apply(item))
            .toList();

        // Return a future that completes when all tasks are done
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .handle((v, ex) -> { // Use .handle to process both success and failure
                if (ex != null) {
                    log.error("Replication failed with an exception.", ex);
                    return false; // Hard failure (exception)
                }

                // Check for soft failures (tasks that returned false)
                boolean allSucceeded = futures.stream().allMatch(CompletableFuture::join);
                if (allSucceeded) {
                    log.info("Async execution succeeded for all targets.");
                } else {
                    log.error("Async execution failed for one or more targets.");
                }
                return allSucceeded;
            });
    }

    public void replicateClusterMap(ClusterMap clusterMap) {
        clusterMap.getReplicas(currentNode.getNodeId()).forEach(replica -> {
            clientManager.getClient(replica.getNodeId()).replicateClusterMap(clusterMap);
        });
    }

    public void migrate(String newPrimaryId, List<String> newBuckets) {
        bucketManager.copyBucketIds(newPrimaryId, newBuckets, false);
        List<CacheEntry> migratingKeys = bucketManager.getMigratingKeys(newPrimaryId, newBuckets.size());
        MemoraClient client = clientManager.getClient(newPrimaryId);
        client.put(migratingKeys);
        List<String> keys = migratingKeys.stream().map(CacheEntry::getKey).toList();
        bucketManager.delete(keys);
        keys.forEach(this::delete);
        bucketManager.scale(newBuckets.size());
    }

    public void clearInSyncReplicas() {
        inSyncReplicas.clear();
    }

    public int getDesiredReplicaCount() {
        return replicationFactor;
    }
}
