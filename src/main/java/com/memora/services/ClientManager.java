package com.memora.services;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import com.google.inject.Inject;
import com.memora.core.MemoraClient;
import com.memora.core.MemoraClientChannel;
import com.memora.enums.ThreadPool;
import com.memora.messages.RpcResponse;
import com.memora.model.ClusterMap;
import com.memora.model.NodeBase;
import com.memora.model.NodeInfo;
import com.memora.utils.ResponseFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientManager {

    private final Bootstrap bootstrap;
    private final EventLoopGroup group;
    private final Map<String, MemoraClient> clientMap;
    private final ThreadPoolService threadPoolService;
    private final ClusterMap clusterMap;

    private static final Map<String, CompletableFuture<RpcResponse>> PENDING_REQUESTS = new ConcurrentHashMap<>();

    @Inject
    public ClientManager(ThreadPoolService threadPoolService, ClusterMap clusterMap) {
        ThreadPool clientPool = ThreadPool.CLIENT_THREAD_POOL;
        this.group = new NioEventLoopGroup(clientPool.getSize(),
                threadPoolService.getThreadPool(clientPool));
        this.bootstrap = new Bootstrap();
        this.bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new MemoraClientChannel(this));
        clientMap = new ConcurrentHashMap<>();
        this.clusterMap = clusterMap;
        this.threadPoolService = threadPoolService;
    }

    public synchronized void addClient(String nodeId, MemoraClient client) {
        clientMap.put(nodeId, client);
    }

    public synchronized void removeClient(String nodeId) {
        clientMap.remove(nodeId);
    }

    public MemoraClient getOrCreate(NodeInfo node) throws InterruptedException, IOException {
        return getOrCreate(node.getNodeId(), node.getNodeBase());
    }

    public MemoraClient getOrCreate(String nodeId, NodeBase base) throws InterruptedException, IOException {
        MemoraClient client = getClient(nodeId);
        if (client == null || !client.isActive()) {
            String remoteNodeId = createAndAdd(base);
            if (remoteNodeId != nodeId)
                throw new RuntimeException("Node ID mismatch");
            client = getClient(nodeId);
        }
        return client;
    }

    public String createAndAdd(NodeBase base) {
        return createAndAdd(base, Optional.empty());
    }

    public String createAndAdd(NodeBase base, Optional<String> nodeId) {
        try {
            // We must create a temporary client just to ask its ID.
            if (new InetSocketAddress(base.getHost(), base.getPort()).isUnresolved()) {
                throw new IOException("Unable to resolve host: " + base.getHost());
            }

            Channel channel = this.bootstrap.connect(base.getHost(), base.getPort()).sync().channel();
            MemoraClient tempClient = new MemoraClient(channel, base, clusterMap, this);

            String id = nodeId.orElse(tempClient.getNodeId().get().getResponse());

            // Now, register it properly (this is synchronized)
            addClient(id, tempClient);
            return id;

        } catch (Exception exception) {
            log.error("Unable to create client for {}", base, exception);
            throw new RuntimeException(String.format("Unable to create client for %s", base));
        }
    }

    public List<String> createAndAddAll(Collection<NodeBase> base) {
        return base.stream().map(this::createAndAdd).toList();
    }

    public NodeBase getAddress(String hostName, int port) throws IOException {
        InetSocketAddress address = new InetSocketAddress(hostName, port);
        if (address.isUnresolved()) {
            throw new IOException("Unable to resolve address: " + hostName + ":" + port);
        }
        return NodeBase.create(address.getAddress().getHostAddress(), address.getPort());
    }

    public MemoraClient getClient(String nodeId) {
        MemoraClient client = clientMap.get(nodeId);
        if (client == null) {
            throw new RuntimeException("Client not found for node " + nodeId);
        }

        if (!client.isActive()) {
            createAndAdd(client.getBase(), Optional.of(nodeId));
        }
        return clientMap.get(nodeId);
    }

    public NodeBase getBase(String nodeId) {
        return getClient(nodeId).getBase();
    }

    /**
     * Registers a request and schedules a timeout for it.
     */
    public void addRequest(final String correlationId, final CompletableFuture<RpcResponse> future) {
        
        // 1. Store the future so it can be 'resolved'
        PENDING_REQUESTS.put(correlationId, future);

        // 2. Schedule a task to run after 10 seconds.
        final ScheduledFuture<?> timeoutTask = this.threadPoolService.submitAfter(ThreadPool.CLIENT_THREAD_POOL, () -> {
            // This code runs *only* if the timeout is reached.
            future.complete(ResponseFactory.TIMEOUT());
            return null;
        }, 30);

        // 3. Add a cleanup listener that runs when 'future' completes
        //    (either by 'resolve' or by our timeout).
        future.whenComplete((response, exception) -> {
            // This runs *after* 'resolve' or the timeout.
            // We cancel the timeout task (if 'resolve' won)
            timeoutTask.cancel(false); 
            // We remove from the map (if 'resolve' won)
            PENDING_REQUESTS.remove(correlationId);
        });
    }

    /**
     * Resolves a pending request with a successful response.
     */
    public void resolveRequest(String correlationId, RpcResponse response) {
        // Get the future. We don't remove it here.
        CompletableFuture<RpcResponse> future = PENDING_REQUESTS.get(correlationId);

        if (future != null) {
            // Try to complete the future.
            future.complete(response);
            // This completion will trigger the 'whenComplete' listener,
            // which will cancel the timeout and remove from the map.
        } else {
            log.warn("No pending request for correlation ID {}.", correlationId);
        }
        // If future is null, it was already timed out and removed.
        // We just drop this late response.
    }
}
