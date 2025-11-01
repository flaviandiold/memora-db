package com.memora.utils;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.memora.messages.RpcResponse;

public final class CommonUtils {

    public static void resolveFutures(final List<CompletableFuture<RpcResponse>> futures)  throws InterruptedException, ExecutionException {
        for (CompletableFuture<RpcResponse> future : futures)
            future.get();
        futures.clear();
    }
    
}
