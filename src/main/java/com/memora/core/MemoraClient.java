package com.memora.core;

import com.memora.enums.NodeType;
import com.memora.exceptions.MemoraException;
import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.messages.RpcStatus;
import com.memora.utils.Parser;
import com.memora.utils.RequestFactory;
import com.memora.utils.ResponseFactory;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import com.memora.model.CacheEntry;
import com.memora.model.ClusterMap;
import com.memora.model.NodeBase;
import com.memora.model.NodeInfo;
import com.memora.services.ClientManager;

/**
 * Simple blocking TCP client for cache RPC calls. Keeps a persistent connection
 * to the server.
 */
@Slf4j
public class MemoraClient implements Closeable {

    private final Channel channel;
    private final NodeBase base;
    private final ClusterMap clusterMap;
    private final ClientManager clientManager;
    
    private final ReentrantLock lock = new ReentrantLock();
    private final int PUT_BATCH_SIZE = 50;
    private final int MAX_RETRIES = 3;
    
    private boolean closed = false;

    public MemoraClient(Channel channel, NodeBase base, ClusterMap clusterMap, ClientManager clientManager) throws IOException {
        this.channel = channel;
        this.base = base;
        this.clusterMap = clusterMap;
        this.clientManager = clientManager;

        if (new InetSocketAddress(base.getHost(), base.getPort()).isUnresolved()) {
            throw new IOException("Unable to resolve host: " + base.getHost());
        }
    }

    private CompletableFuture<RpcResponse> send(RpcRequest request) throws MemoraException {
        if (closed) {
            throw new MemoraException("Client is closed.");
        }
        if (!this.isActive()) {
            return CompletableFuture.failedFuture(new MemoraException("Client not connected."));
        }

        // 1. Generate a unique ID and create the future
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();

        // 2. Store the future so the response handler can find it
        if (request.getCorrelationId() == null || request.getCorrelationId().isEmpty()) {
            throw new MemoraException("Request must have a correlation ID.");
        }

        clientManager.addRequest(request.getCorrelationId(), future);
        
        // 3. Send the request
        // log.info("Sending request: {} to {}", request, this.base);
        channel.writeAndFlush(request);

        // 4. Return the future immediately
        return future;

    }

    public CompletableFuture<RpcResponse> call(RpcRequest request) throws MemoraException {
        return send(request);
    }


    public CompletableFuture<RpcResponse> call(String command) throws MemoraException {

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


    public CompletableFuture<RpcResponse> call(String command, Object ...args) throws MemoraException {
        return call(String.format(command, args));
    }

    public CompletableFuture<RpcResponse> callWithoutError(String request) {
        try {
            return call(request);
        } catch (MemoraException e) {
            return  CompletableFuture.supplyAsync(() -> ResponseFactory.create(RpcStatus.ERROR));
        }
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

    public CompletableFuture<RpcResponse> getBucketMap() {
        return call("INFO BUCKET MAP");
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

    public CompletableFuture<RpcResponse> clusterLock() {
        return call("CLUSTER NODE LOCK");
    }
    
    public CompletableFuture<RpcResponse> clusterUnlock() {
        return call("CLUSTER NODE UNLOCK");
    }

    public CompletableFuture<Boolean> forgetWithoutFailure(String nodeId) {
        return isSuccessAsync(String.format("CLUSTER NODE FORGET %s", nodeId));
    }

    public CompletableFuture<RpcResponse> forget(String nodeId) {
        return call("CLUSTER NODE FORGET %s", nodeId);
    }

    public CompletableFuture<RpcResponse> forget(String nodeId, boolean isHard) {
        return call("CLUSTER NODE FORGET %s %s", nodeId, isHard ? "HARD" : "SOFT");
    }

    public CompletableFuture<RpcResponse> forget(List<String> nodeIds) {
        return call("CLUSTER NODE FORGET %s", String.join(" ", nodeIds));
    }

    public CompletableFuture<Boolean> put(String key, String value, long ttl) {
        String request = String.format("PUT %s '%s' EXAT %d", key, value, ttl);
        return isSuccessAsync(request);
    }

    public CompletableFuture<Boolean> put(String key, String value) {
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
     * This is fully non-blocking.
     *
     * @param request      The command string to send.
     * @param retriesLeft  The number of retries remaining.
     * @param pool         The thread pool to execute the retry logic on.
     * @return A CompletableFuture that completes with the success status.
     */
    private CompletableFuture<Boolean> attemptWithRetries(String request, int retriesLeft, ExecutorService pool) {
        // 1. Call the async method. It returns a future immediately.
        return isSuccessAsync(request)
            .thenComposeAsync(success -> { // 2. When the future completes, run this logic
                
                if (success) {
                    // If successful, we are done.
                    return CompletableFuture.completedFuture(true);
                }
                
                if (retriesLeft > 0) {
                    // If failed, recursively call this method again.
                    // This is non-blocking recursion.
                    log.info("Request failed, retrying... ({} retries left)", retriesLeft);
                    return attemptWithRetries(request, retriesLeft - 1, pool);
                }
                
                // If failed and no retries are left, return final failure.
                log.error("Request failed after all retries: {}", request);
                return CompletableFuture.completedFuture(false);

            }, pool); // 3. IMPORTANT: Run this composition logic on your worker pool
    }

    public CompletableFuture<RpcResponse> get(String key) {
        return call("GET %s", key);
    }

    public CompletableFuture<RpcResponse> get(Collection<String> keys) {
        return call("GET %s", String.join(" ", keys));
    }

    public CompletableFuture<Boolean> delete(String key) {
        String request = String.format("DELETE %s", key);
        return isSuccessAsync(request);
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

    /**
     * Asynchronously checks if a request was successful.
     * This method is non-blocking.
     *
     * @param request The command string to send.
     * @return A CompletableFuture that will complete with 'true' if the
     * response status is OK, and 'false' otherwise.
     */
    private CompletableFuture<Boolean> isSuccessAsync(String request) {
        return callWithoutError(request)
            .thenApply(response -> RpcStatus.OK.equals(response.getStatus()));
    }


    public boolean matches(NodeBase base) {
        return base.equals(base.getHost(), base.getPort());
    }

    public NodeBase getBase() {
        return base;
    }

    @Override
    public void close() throws IOException {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            closeQuietly();
        } finally {
            lock.unlock();
        }
    }

    private void closeQuietly() {
        if (channel != null) {
            channel.close();
        }
    }


    public boolean isActive() {
        return channel != null && channel.isActive();
    }
}
