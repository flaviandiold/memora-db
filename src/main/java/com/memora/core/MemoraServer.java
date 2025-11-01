package com.memora.core;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.memora.services.ThreadPoolService;

import io.grpc.Server;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class MemoraServer {

    private final Server server;
    private final ThreadPoolService threadPoolService;


    @Inject
    public MemoraServer(Server server, ThreadPoolService threadPoolService) {
        this.server = server;
        this.threadPoolService = threadPoolService;
    }

    public void start() throws IOException {
        server.start();
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            threadPoolService.shutdown();
        }
    }
}