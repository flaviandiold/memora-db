package com.memora.core;

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
import java.util.concurrent.locks.ReentrantLock;

import com.memora.model.RpcResponse;

/**
 * Simple blocking TCP client for cache RPC calls.
 * Keeps a persistent connection to the server.
 */
@Slf4j
public class MemoraClient implements Closeable {

    private final String host;
    private final int port;
    private final int CONNECT_TIMEOUT_MS = 2000;
    private final ReentrantLock lock = new ReentrantLock();
    
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
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setKeepAlive(true);

            // Order is important: create output stream first and flush.
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            out.flush(); // send header immediately
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            log.info("CLIENT connected to {}:{}", host, port);
        } catch (IOException e) {
            log.error("CLIENT failed to connect to {}:{}. Error: {}", host, port, e.getMessage());
            throw e; // Propagate a checked exception
        }
    }

    public RpcResponse call(String request) throws RpcException {
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
                out.write(request);
                out.newLine();
                out.flush();

                String responseObject = in.readLine();
                System.out.println("CLIENT received " + responseObject + " from " + host + ":" + port);
                return Parser.fromJson(responseObject, RpcResponse.class);
            } catch (IOException e) {
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

    public RpcResponse getNodeId() {
        return call("INFO NODE ID");
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
