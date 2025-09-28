package com.memora.core;

import com.memora.exceptions.RpcException;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;

import java.io.*;
import java.net.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simple blocking TCP client for cache RPC calls.
 * Keeps a persistent connection to the server.
 */
public class MemoraClient implements Closeable {

    private final String host;
    private final int port;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean closed = false;
    private final ReentrantLock lock = new ReentrantLock();

    private static final int CONNECT_TIMEOUT_MS = 2000;

    public MemoraClient(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        connectWithRetry();
    }

    private void connectWithRetry() throws IOException {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setKeepAlive(true);

            // Order is important: create output stream first and flush.
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush(); // send header immediately
            in = new ObjectInputStream(socket.getInputStream());
            System.out.println("CLIENT connected to " + host + ":" + port);
        } catch (IOException e) {
            System.err.printf("CLIENT failed to connect to %s:%d. Error: %s%n", host, port, e.getMessage());
            throw e; // Propagate a checked exception
        }
    }

    public RpcResponse call(RpcRequest request) throws RpcException {
        lock.lock();
        try {
            if (closed) {
                throw new RpcException("Client is closed.");
            }
            try {
                if (socket == null || !socket.isConnected() || socket.isClosed()) {
                    System.err.println("CLIENT disconnected. Attempting to reconnect...");
                    connectWithRetry();
                }

                System.out.println("CLIENT sending " + request + " to " + host + ":" + port);
                out.writeObject(request);
                out.reset(); // Important for mutable objects or multiple writes
                out.flush();

                Object responseObject = in.readObject();
                System.out.println("CLIENT received " + responseObject + " from " + host + ":" + port);

                if (responseObject instanceof RpcResponse rpcResponse) {
                    return rpcResponse;
                }
                // Handle cases where the response is not what we expect
                throw new RpcException("Invalid response type received: " + responseObject.getClass().getName());
            } catch (IOException | ClassNotFoundException e) {
                // This indicates a connection or serialization error, which is critical.
                System.err.printf("CLIENT error during call to %s:%d. Error: %s%n", host, port, e.getMessage());
                // We should probably close the connection and let the caller decide to retry.
                closeQuietly();
                throw new RpcException("Failed to complete RPC call", e);
            }
        } finally {
            lock.unlock();
        }
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
            if (in != null) in.close();
        } catch (IOException e) { /* ignore */ }
        try {
            if (out != null) out.close();
        } catch (IOException e) { /* ignore */ }
        try {
            if (socket != null) socket.close();
        } catch (IOException e) { /* ignore */ }
        finally {
            in = null;
            out = null;
            socket = null;
        }
    }
}
