package com.memora.core;

import java.io.IOException;

import com.google.inject.Inject;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;
import com.memora.services.CommandExecutor;

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

    private final String host;
    private final int port;
    private final Version version;
    private final CommandExecutor commandExecutor;

    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    @Inject
    public MemoraServer(String host, int port, CommandExecutor commandExecutor, Version version) {
        this.host = host;
        this.port = port;
        this.commandExecutor = commandExecutor;
        this.version = version;
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
                                    request = request.trim();
                                    log.info("Received request: '{}'", request);
                                    int idx = request.indexOf(' ');
                                    if (idx == -1) {
                                        ctx.writeAndFlush(RpcResponse.BAD_REQUEST.toString());
                                        return;
                                    }
                                    String operation = request.substring(0, idx);
                                    RpcRequest rpcRequest = RpcRequest.builder()
                                            .operation(operation)
                                            .version(version.get())
                                            .command(request)
                                            .build();
                                    RpcResponse response = commandExecutor.execute(rpcRequest);
                                    ctx.writeAndFlush(response.toString());
                                }
                            });
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = bootstrap.bind(host, port).sync();
            serverChannel = future.channel();
            log.info("Memora Server started on port ", port);

            future.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
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
