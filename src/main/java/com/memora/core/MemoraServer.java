package com.memora.core;

import java.io.IOException;
import java.util.Objects;

import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;
import com.memora.model.CacheEntry;
import com.memora.services.BucketManager;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoraServer implements AutoCloseable {

    private final int port;
    private final MemoraServer server;
    private final BucketManager bucketManager;

    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;


    public MemoraServer(int port) {
        this.port = port;
        this.server = this;
        this.bucketManager = BucketManager.getInstance();
    }
    
    public void start() throws InterruptedException {
        // ThreadPool serverPool = ThreadPool.SERVER_THREAD_POOL;
        bossGroup = new NioEventLoopGroup(); // accepts incoming connections
        workerGroup = new NioEventLoopGroup(); // handles traffic
        // workerGroup = new NioEventLoopGroup(serverPool.getSize(), ThreadPoolService.getThreadPool(ThreadPool.SERVER_THREAD_POOL)); // handles traffic
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new StringDecoder()); // decode bytes to String
                            pipeline.addLast(new StringEncoder()); // encode String to bytes
                            pipeline.addLast(new SimpleChannelInboundHandler<String>() {
                                @Override
                                public void channelRead0(ChannelHandlerContext ctx, String request) {
                                    request = request.trim(); // Trim whitespace and newlines
                                    log.info("Received request: '{}'", request);
                                    String[] parts = request.split(" ");
                                    RpcRequest rpcRequest = RpcRequest.builder()
                                            .commandType(RpcRequest.commandOf(parts[0]))
                                            .key(parts.length > 1 ? parts[1] : null)
                                            .value(parts.length > 2 ? parts[2] : null)
                                            .version(MemoraNode.getVersion())
                                            .build();
                                    RpcResponse response = server.handler(rpcRequest);
                                    ctx.writeAndFlush(response.toString());
                                }
                            });
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            log.info("Memora Server started on port ", port);

            future.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    private RpcResponse handler(final RpcRequest request) {
        log.info("Handling request: {}", request);
        final String key = request.key();
        boolean isSelf = bucketManager.isInSelf(key);
        if (!isSelf) return RpcResponse.UNSUPPORTED_COMMAND;
        RpcResponse result = switch (request.commandType()) {
            case PUT -> {
                bucketManager.getBucket(request.key()).put(key, CacheEntry.builder().value(request.value()).ttl(-1).build());
                MemoraNode.incrementVersion();
                yield RpcResponse.OK;
            }
            case GET -> {
                CacheEntry entry = bucketManager.getBucket(key).get(key);
                if (Objects.isNull(entry)) {
                    yield RpcResponse.NOT_FOUND;
                } else {
                    yield RpcResponse.builder().response(entry.getValue()).build();
                }
            }
            case DELETE -> {
                bucketManager.getBucket(key).delete(key);
                yield RpcResponse.OK;
            }
            default -> RpcResponse.UNSUPPORTED_COMMAND;
        };
        return result;
    }

    @Override
    public void close() throws IOException {
        if (serverChannel != null) {
            serverChannel.close(); // close the server socket
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        log.info("Memora Server stopped.");
    }
}
