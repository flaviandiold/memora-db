package com.memora.services;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.google.inject.Inject;
import com.memora.core.MemoraClient;
import com.memora.core.MemoraClientChannel;
import com.memora.enums.ThreadPool;
import com.memora.messages.RpcResponse;
import com.memora.model.NodeBase;
import com.memora.model.NodeInfo;

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

    private static final Map<String, CompletableFuture<RpcResponse>> PENDING_REQUESTS = new ConcurrentHashMap<>();


    @Inject
    public ClientManager(ThreadPoolService threadPoolService) {
        ThreadPool clientPool = ThreadPool.CLIENT_THREAD_POOL;
        this.group = new NioEventLoopGroup(clientPool.getSize(),
            threadPoolService.getThreadPool(clientPool));
        this.bootstrap = new Bootstrap();
        this.bootstrap.group(group)
                      .channel(NioSocketChannel.class)
                      .handler(new MemoraClientChannel());
        clientMap = new HashMap<>();
    }


    public void addClient(String nodeId, MemoraClient client) {
        if (clientMap.containsKey(nodeId)) {
            return;
        }
        clientMap.put(nodeId, client);
    }

    public MemoraClient getOrCreate(NodeInfo node) throws InterruptedException, IOException {
        MemoraClient client = getClient(node.getNodeId());
        if (client == null) {
            client = create(node.getNodeBase());
            addClient(node.getNodeId(), client);
        }
        return client;
    }

    public synchronized MemoraClient create(NodeBase base) throws IOException, InterruptedException {
        Channel channel = this.bootstrap.connect(base.getHost(), base.getPort()).sync().channel();
        return new MemoraClient(base.getHost(), base.getPort(), channel);
    }

    public NodeBase getAddress(String hostName, int port) throws IOException {
        InetSocketAddress address = new InetSocketAddress(hostName, port);
        if (address.isUnresolved()) {
            throw new IOException("Unable to resolve address: " + hostName + ":" + port);
        }
        return NodeBase.create(address.getAddress().getHostAddress(), address.getPort());
    }

    public MemoraClient getClient(String nodeId) {
        return clientMap.get(nodeId);
    }

    public static void addRequest(String correlationId, CompletableFuture<RpcResponse> future) {
        PENDING_REQUESTS.put(correlationId, future);
    }

    public static void resolve(String correlationId, RpcResponse response) {
        CompletableFuture<RpcResponse> future = PENDING_REQUESTS.remove(correlationId);

        Optional.ofNullable(future).ifPresent(f -> f.complete(response));
    }
}
