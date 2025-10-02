package com.memora.core;

import com.memora.enums.RpcRespnseStatus;
import com.memora.exceptions.RpcException;
import com.memora.utils.Parser;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import com.memora.model.CacheEntry;
import com.memora.model.RpcResponse;

/**
 * Simple blocking TCP client for cache RPC calls. Keeps a persistent connection
 * to the server.
 */
@Slf4j
public class MemoraClient implements Closeable {

    private final String host;
    private final int port;
    private final int CONNECT_TIMEOUT_MS = 2000;
    private final ReentrantLock lock = new ReentrantLock();
    private final int PUT_BATCH_SIZE = 50;
    private final int MAX_RETRIES = 3;

    private Socket socket;
    private BufferedWriter out;
    private BufferedReader in;
    private boolean closed = false;

    public MemoraClient(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        connectWithRetry();
    }

    private void connectWithRetry() throws IOException {
        if (socket == null || !socket.isConnected() || socket.isClosed()) {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                socket.setKeepAlive(true);

                // Order is important: create output stream first and flush.
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                out.flush(); // send header immediately
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                log.info("CLIENT connected to {}:{}", getHost(), getPort());
            } catch (IOException e) {
                log.error("CLIENT failed to connect to {}:{}. Error: {}", host, port, e.getMessage());
                throw e; // Propagate a checked exception
            }
        }
    }

    public RpcResponse call(String request) throws RpcException {
        lock.lock();
        try {
            if (closed) {
                throw new RpcException("Client is closed.");
            }
            try {
                connectWithRetry();

                out.write(request);
                out.newLine();
                out.flush();

                String responseObject = in.readLine();
                return Parser.fromJson(responseObject, RpcResponse.class);
            } catch (IOException e) {
                // This indicates a connection or serialization error, which is critical.
                log.error("CLIENT error during call to {}:{}. Error: {}", host, port, e.getMessage());
                // We should probably close the connection and let the caller decide to retry.
                closeQuietly();
                throw new RpcException("Failed to complete RPC call", e);
            }
        } finally {
            lock.unlock();
        }
    }

    public RpcResponse callWithoutError(String request) {
        try {
            return call(request);
        } catch (RpcException e) {
            return RpcResponse.ERROR(request);
        }
    }

    public RpcResponse getNodeId() {
        return call("INFO NODE ID");
    }

    public RpcResponse primarize(String host, int port) {
        return call(String.format("NODE PRIMARIZE %s@%d", host, port));
    }

    public RpcResponse replicate(String host, int port) {
        return call(String.format("NODE REPLICATE %s@%d", host, port));
    }

    public boolean put(String key, String value, long ttl) {
        String request = String.format("PUT %s %s EXAT %d", key, value, ttl);
        return isSuccess(request);
    }

    public boolean put(String key, String value) {
        return put(key, value, -1);
    }

    public boolean put(Map<String, CacheEntry> entries) {
        if (entries.isEmpty()) {
            return true;
        }

        List<String> failedQueries = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        builder.append("PUT");
        int soFar = 0;
        boolean itemAdded = false;
        for (Map.Entry<String, CacheEntry> entry : entries.entrySet()) {
            CacheEntry item = entry.getValue();
            builder.append(String.format(" %s %s EXAT %d", entry.getKey(), item.getValue(), item.getTtl()));
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
    public boolean put(Map<String, CacheEntry> entries, ExecutorService threadPool) {
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
    public CompletableFuture<Boolean> putAsync(Map<String, CacheEntry> entries, ExecutorService pool) {
        if (entries == null || entries.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        List<String> batchCommands = new ArrayList<>();
        StringBuilder builder = new StringBuilder("PUT");
        int soFar = 0;

        for (Map.Entry<String, CacheEntry> entry : entries.entrySet()) {
            CacheEntry item = entry.getValue();
            builder.append(String.format(" %s %s EXAT %d", entry.getKey(), item.getValue(), item.getTtl()));
            
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
                System.out.println("Request failed, retrying... (" + retriesLeft + " retries left)");
                return attemptWithRetries(request, retriesLeft - 1, pool);
            }
            // If failed and no retries are left, return final failure.
            return CompletableFuture.completedFuture(false);
        }, pool);
    }

    public RpcResponse get(String key) {
        return call(String.format("GET %s", key));
    }

    public boolean delete(String key) {
        String request = String.format("DELETE %s", key);
        return isSuccess(request);
    }

    public String getHost() {
        try {
            connectWithRetry();
            return socket.getInetAddress().getHostAddress();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int getPort() {
        try {
            connectWithRetry();
            return socket.getPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isSuccess(String request) {
        return RpcRespnseStatus.OK.equals(callWithoutError(request).getStatus());
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
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            /* ignore */ }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            /* ignore */ }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            /* ignore */ } finally {
            in = null;
            out = null;
            socket = null;
        }
    }
}
