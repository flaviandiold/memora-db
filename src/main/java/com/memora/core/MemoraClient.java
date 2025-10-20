package com.memora.core;

import com.memora.enums.NodeType;
import com.memora.exceptions.MemoraException;
import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.messages.RpcStatus;
import com.memora.utils.RequestFactory;
import com.memora.utils.ResponseFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import com.memora.model.CacheEntry;
import com.memora.model.ClusterInfo;
import com.memora.model.NodeInfo;
import com.memora.services.ClientManager;

/**
 * Simple blocking TCP client for cache RPC calls. Keeps a persistent connection
 * to the server.
 */
@Slf4j
public class MemoraClient implements Closeable {

    private final Channel channel;
    
    private final ReentrantLock lock = new ReentrantLock();
    private final int PUT_BATCH_SIZE = 50;
    private final int MAX_RETRIES = 3;
    
    private boolean closed = false;

    public MemoraClient(String host, int port, Channel channel) throws IOException {
        this.channel = channel;
        if (new InetSocketAddress(host, port).isUnresolved()) {
            throw new IOException("Unable to resolve host: " + host);
        }
    }

    private CompletableFuture<RpcResponse> send(RpcRequest request) throws MemoraException {
        if (closed) {
            throw new MemoraException("Client is closed.");
        }
        if (channel == null || !channel.isActive()) {
            return CompletableFuture.failedFuture(new MemoraException("Client not connected."));
        }

        // 1. Generate a unique ID and create the future
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();

        // 2. Store the future so the response handler can find it
        if (request.getCorrelationId() == null || request.getCorrelationId().isEmpty()) {
            throw new MemoraException("Request must have a correlation ID.");
        }

        ClientManager.addRequest(request.getCorrelationId(), future);
        
        // 3. Send the request
        channel.writeAndFlush(request);

        // 4. Return the future immediately
        return future;

    }

    public CompletableFuture<RpcResponse> call(RpcRequest request) throws MemoraException {
        return send(request);
    }


    public CompletableFuture<RpcResponse> call(String command) throws MemoraException {

        NodeInfo info = MemoraNode.getInfo();
        long clusterEpoch = ClusterInfo.getEpoch();

        RpcRequest.Builder request = RequestFactory.createRequest(command).setClusterEpoch(clusterEpoch);
        if (Objects.nonNull(info) && info.getType().equals(NodeType.PRIMARY) ) {
            request.setNodeVersion(Version.get());
        }
        
        return call(request.build());
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

    public CompletableFuture<RpcResponse> getNodeInfo() {
        return call("INFO NODE ALL");
    }

    public CompletableFuture<RpcResponse> primarize(String host, int port) {
        return call(String.format("NODE PRIMARIZE %s@%d", host, port));
    }

    public CompletableFuture<RpcResponse> replicate(String host, int port) {
        return call(String.format("NODE REPLICATE %s@%d", host, port));
    }

    public boolean put(String key, String value, long ttl) {
        String request = String.format("PUT %s %s EXAT %d", key, value, ttl);
        return isSuccess(request);
    }

    public boolean put(String key, String value) {
        return put(key, value, -1);
    }

    public boolean put(List<CacheEntry> entries) {
        if (entries.isEmpty()) {
            return true;
        }

        List<String> failedQueries = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        builder.append("PUT");
        int soFar = 0;
        boolean itemAdded = false;
        for (CacheEntry item : entries) {
            builder.append(String.format(" %s %s EXAT %d", item.getKey(), item.getValue(), item.getTtl()));
            itemAdded = true;
            if (++soFar >= PUT_BATCH_SIZE) {
                String request = builder.toString();
                if (!isSuccess(request)) {
                    failedQueries.add(request);
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

        return failedQueries.isEmpty();
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
            builder.append(String.format(" %s %s EXAT %d", item.getKey(), item.getValue(), item.getTtl()));
            
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
        return call(String.format("GET %s", key));
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
}
