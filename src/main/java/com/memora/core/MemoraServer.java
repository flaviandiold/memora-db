package com.memora.core;

import java.io.IOException;

import com.google.inject.Inject;
import com.memora.enums.ThreadPool;
import com.memora.services.ThreadPoolService;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class MemoraServer implements AutoCloseable {

    private final String host;
    private final int port;
    private final ChannelInitializer<Channel> memoraChannel;
    private final ThreadPoolService threadPoolService;

    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public void start(Runnable callback) throws InterruptedException {
        ThreadPool serverPool = ThreadPool.SERVER_THREAD_POOL;
        bossGroup = new NioEventLoopGroup(); // accepts incoming connections
        workerGroup = new NioEventLoopGroup(serverPool.getSize(),
            threadPoolService.getThreadPool(serverPool)); // handles traffic
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(memoraChannel)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = bootstrap.bind(host, port).sync();
            serverChannel = future.channel();
            log.info("Memora Server started on port {}", port);

            callback.run();

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
