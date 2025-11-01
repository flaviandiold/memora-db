package com.memora.core;

import com.google.inject.Inject;
import com.memora.enums.ThreadPool;
import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.service.MemoraServerGrpc;
import com.memora.services.CommandExecutor;
import com.memora.services.ThreadPoolService;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;

@Slf4j
public class MemoraGrpcService extends MemoraServerGrpc.MemoraServerImplBase {

    private final CommandExecutor commandExecutor;
    private final ExecutorService grpcExecutor;

    @Inject
    public MemoraGrpcService(CommandExecutor commandExecutor, ThreadPoolService threadPoolService) {
        this.commandExecutor = commandExecutor;
        grpcExecutor = threadPoolService.getThreadPool(ThreadPool.EXECUTOR_THREAD_POOL);
    }

    @Override
    public void execute(RpcRequest request, StreamObserver<RpcResponse> responseObserver) {
        grpcExecutor.submit(() -> {
            log.info("gRPC Request received with correlation id: {}", request.getCorrelationId());
            RpcResponse response = commandExecutor.execute(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        });
    }
}