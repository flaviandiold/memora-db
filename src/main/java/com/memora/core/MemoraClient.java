package com.memora.core;

import com.memora.enums.NodeType;
import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.messages.RpcStatus;
import com.memora.service.MemoraServerGrpc;
import com.memora.utils.Parser;
import com.memora.utils.RequestFactory;
import com.memora.utils.ResponseFactory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.memora.model.CacheEntry;
import com.memora.model.ClusterMap;
import com.memora.model.NodeBase;
import com.memora.model.NodeInfo;

/**
 * gRPC client for MemoraDB.
 */
@Slf4j
public class MemoraClient implements Closeable {

    private final ManagedChannel channel;
    private final MemoraServerGrpc.MemoraServerStub asyncStub;
    private final NodeBase base;
    private final ClusterMap clusterMap;

    private final int PUT_BATCH_SIZE = 50;
    private final int MAX_RETRIES = 3;

    public MemoraClient(NodeBase base, ClusterMap clusterMap) {
        this.base = base;
        this.clusterMap = clusterMap;
        this.channel = ManagedChannelBuilder.forAddress(base.getHost(), base.getPort())
                .build();
        this.asyncStub = MemoraServerGrpc.newStub(channel);
    }

    public CompletableFuture<RpcResponse> call(RpcRequest request) {
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        asyncStub.execute(request, new StreamObserver<>() {
            private RpcResponse lastResponse;

            @Override
            public void onNext(RpcResponse response) {
                lastResponse = response;
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                future.complete(lastResponse);
            }
        });
        return future;
    }

    public CompletableFuture<RpcResponse> call(String command) {

        NodeInfo info = MemoraNode.getInfo();
        long clusterEpoch = clusterMap.getEpoch();

        RpcRequest.Builder request = RequestFactory
            .createRequest(command)
            .setNodeVersion(-1L)
            .setClusterEpoch(clusterEpoch);

        if (Objects.nonNull(info) && info.getType().equals(NodeType.PRIMARY) ) {
            request.setNodeVersion(Version.get());
        }
        
        return call(request.build());
    }

    public CompletableFuture<RpcResponse> call(String command, Object... args) {
        return call(String.format(command, args));
    }

    public CompletableFuture<RpcResponse> callWithoutError(String request) {
        return call(request).exceptionally(e -> ResponseFactory.create(RpcStatus.ERROR));
    }

    public CompletableFuture<RpcResponse> getNodeId() {
        return call("INFO NODE ID");
    }


    public CompletableFuture<RpcResponse> getNodeType() {
        return call("INFO NODE TYPE");
    }

    public CompletableFuture<RpcResponse> getPrimariesCount() {
        return call("INFO NODE PRIMARIES");
    }
    
    public CompletableFuture<RpcResponse> getReplicas() {
        return call("INFO NODE REPLICAS");
    }

    public CompletableFuture<RpcResponse> getBucketIds() {
        return call("INFO BUCKET IDS");
    }

    public CompletableFuture<RpcResponse> getNodeInfo() {
        return call("INFO NODE ALL");
    }

    public CompletableFuture<RpcResponse> primarize(String host, int port) {
        return call("NODE PRIMARIZE %s@%d", host, port);
    }

    public CompletableFuture<RpcResponse> join(NodeBase base) {
        return call("CLUSTER NODE JOIN %s@%d", base.getHost(), base.getPort());
    }

    public CompletableFuture<RpcResponse> replicate(String host, int port) {
        return call("NODE REPLICATE SOURCE %s@%d", host, port);
    }

    public CompletableFuture<RpcResponse> replicate(NodeBase base) {
        return replicate(base.getHost(), base.getPort());
    }

    public CompletableFuture<RpcResponse> replicateClusterMap(ClusterMap map) {
        return call("NODE REPLICATE CLUSTER %s", Parser.toJson(map));
    }
    
    public CompletableFuture<RpcResponse> replicateBucketIds(String nodeId, List<String> bucketIds) {
        return call("NODE REPLICATE BUCKET %s %s", nodeId, String.join(" ", bucketIds));
    }

    public CompletableFuture<RpcResponse> behave(NodeType type) {
        return call("NODE BEHAVE %s", type);
    }

    public boolean put(String key, String value, long ttl) {
        String request = String.format("PUT %s '%s' EXAT %d", key, value, ttl);
        return isSuccess(request);
    }

    public boolean put(String key, String value) {
        return put(key, value, -1);
    }

    public List<String> put(List<CacheEntry> entries) {
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> failedQueries = new ArrayList<>();
        List<Integer> failedIndices = new ArrayList<>();
        int failureStart = 0;
        StringBuilder builder = new StringBuilder();
        builder.append("PUT");
        int soFar = 0;
        boolean itemAdded = false;
        for (CacheEntry item : entries) {
            builder.append(String.format(" %s '%s' EXAT %d", item.getKey(), item.getValue(), item.getTtl()));
            failedIndices.add(failedIndices.size());
            itemAdded = true;
            if (++soFar >= PUT_BATCH_SIZE) {
                String request = builder.toString();
                if (!isSuccess(request)) {
                    failedQueries.add(request);
                    failureStart = failedQueries.size();
                } else {
                    int removeAfter = failureStart;
                    failedIndices.removeIf(index -> {
                        return index >= removeAfter;
                    });
                }
                builder.setLength(0);
                builder.append("PUT");
                soFar = 0;
                itemAdded = false;
            }
        }
        if (itemAdded) {
            String request = builder.toString();
            if (!isSuccess(request)) {
                failedQueries.add(request);
            }
        }

        int retries = MAX_RETRIES;
        while (!failedQueries.isEmpty() && retries >= 0) {
            for (int i = failedQueries.size() - 1; i >= 0; i--) {
                String request = failedQueries.get(i);
                if (isSuccess(request)) {
                    failedQueries.remove(i);
                }
            }
            retries--;
        }

        List<String> failedItems = new ArrayList<>();

        for (int failedIndex: failedIndices) {
            failedItems.add(entries.get(failedIndex).getKey());
        }

        return failedItems;
    }

    // This is the original, blocking method.
    public boolean put(Collection<CacheEntry> entries, ExecutorService threadPool) {
        // For simple blocking behavior, we can call the async version and wait for its result.
        return putAsync(entries, threadPool).join();
    }

    /**
     * Puts multiple entries into the cache by sending them in parallel batches.
     * This method is fully asynchronous and non-blocking.
     *
     * @param entries The map of entries to put in the cache.
     * @param pool    The executor service (thread pool) to run the parallel tasks on.
     * @return A CompletableFuture that will complete with 'true' if all batches
     * (including retries) were successful, and 'false' otherwise.
     */
    public CompletableFuture<Boolean> putAsync(Collection<CacheEntry> entries, ExecutorService pool) {
        if (entries == null || entries.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        List<String> batchCommands = new ArrayList<>();
        StringBuilder builder = new StringBuilder("PUT");
        int soFar = 0;

        for (CacheEntry item: entries) {
            builder.append(String.format(" %s '%s' EXAT %d", item.getKey(), item.getValue(), item.getTtl()));
            
            if (++soFar >= PUT_BATCH_SIZE) {
                batchCommands.add(builder.toString());
                builder.setLength(0);
                builder.append("PUT");
                soFar = 0;
            }
        }
        // Add the last, partially filled batch if it exists
        if (soFar > 0) {
            batchCommands.add(builder.toString());
        }
        
        if (batchCommands.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        List<CompletableFuture<Boolean>> futures = batchCommands.stream()
            .map(command -> attemptWithRetries(command, MAX_RETRIES, pool))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .thenApply(v -> futures.stream().allMatch(CompletableFuture::join)
        );
    }

    /**
     * A helper method that attempts to send a request and retries on failure.
     *
     * @param request      The command string to send.
     * @param retriesLeft  The number of retries remaining.
     * @param pool         The thread pool to execute on.
     * @return A CompletableFuture that completes with the success status.
     */
    private CompletableFuture<Boolean> attemptWithRetries(String request, int retriesLeft, ExecutorService pool) {
        // Run the network call asynchronously on the thread pool
        CompletableFuture<Boolean> attempt = CompletableFuture.supplyAsync(() -> isSuccess(request), pool);

        return attempt.thenComposeAsync(success -> {
            if (success) {
                // If successful, we are done.
                return CompletableFuture.completedFuture(true);
            }
            if (retriesLeft > 0) {
                // If failed and we have retries left, try again.
                log.info("Request failed, retrying... (" + retriesLeft + " retries left)");
                return attemptWithRetries(request, retriesLeft - 1, pool);
            }
            // If failed and no retries are left, return final failure.
            return CompletableFuture.completedFuture(false);
        }, pool);
    }

    public CompletableFuture<RpcResponse> get(String key) {
        return call("GET %s", key);
    }

    public CompletableFuture<RpcResponse> get(Collection<String> keys) {
        return call("GET %s", String.join(" ", keys));
    }

    public boolean delete(String key) {
        String request = String.format("DELETE %s", key);
        return isSuccess(request);
    }

    private boolean isSuccess(String request) {
        RpcResponse response;
        try {
            response = callWithoutError(request).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("RPC call failed for request '{}': {}", request, e.getMessage());
            return false;
        }
        return RpcStatus.OK.equals(response.getStatus());
    }


    public boolean matches(NodeBase base) {
        return base.equals(base.getHost(), base.getPort());
    }

    public NodeBase getBase() {
        return base;
    }

    @Override
    public void close() throws IOException {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while shutting down gRPC client", e);
        }
    }

    public boolean isActive() {
        return channel != null && !channel.isShutdown() && !channel.isTerminated();
    }
}
