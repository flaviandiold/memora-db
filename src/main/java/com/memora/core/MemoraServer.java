package com.memora.core;

import java.io.IOException;
import java.util.List;

import com.memora.constants.ThreadPool;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;
import com.memora.model.CacheEntry;
import com.memora.services.BucketManager;
import com.memora.services.ThreadPoolService;

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
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoraServer implements AutoCloseable {

    private final int port;
    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private MemoraServer server;
    private final BucketManager bucketManager;


    public MemoraServer(int port) {
        this.port = port;
        this.bucketManager = new BucketManager();
        this.server = this;
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
                                            .commandType(RpcRequest.CommandType.valueOf(parts[0].toUpperCase()))
                                            .args(List.of(parts).subList(1, parts.length))
                                            .build();
                                    RpcResponse response = server.handler(rpcRequest);
                                    ctx.writeAndFlush(response.toString() + "\n");
                                }
                            });
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            System.out.println("Netty RPC Server started on port " + port);

            future.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    private RpcResponse handler(RpcRequest request) {
        log.info("Handling request: {}", request);
        String result = switch (request.commandType()) {
            case PUT -> {
                bucketManager.getBucket().put(request.args().get(0), CacheEntry.builder().value(request.args().get(1)).ttl(-1).build());
                yield "OK";
            }
            case GET -> bucketManager.getBucket().get(request.args().get(0)).value();
            case DELETE -> {
                bucketManager.getBucket().delete(request.args().get(0));
                yield "OK";
            }
            default -> "Unsupported command";
        };
        return RpcResponse.builder().status(RpcResponse.Status.OK).result(result).build();
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
        System.out.println("Netty RPC Server stopped.");
    }
}
