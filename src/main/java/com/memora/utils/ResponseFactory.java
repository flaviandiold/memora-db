package com.memora.utils;

import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.messages.RpcStatus;

/**
 * A factory class for creating common RpcResponse messages.
 * This keeps custom logic separate from the Protobuf-generated code.
 */
public final class ResponseFactory {

    // Private constructor to prevent instantiation
    private ResponseFactory() {}

    private static RpcResponse.Builder withStatus(RpcStatus status) {
        return RpcResponse.newBuilder().setStatus(status);
    }

    public static RpcResponse create(RpcStatus status) {
        return withStatus(status).build();
    }

    public static RpcResponse create(RpcStatus status, RpcRequest request) {
        return withStatus(status).setCorrelationId(request.getCorrelationId()).build();
    }
    
    public static RpcResponse create(RpcStatus status, RpcRequest request, Object message) {
        return withStatus(status).setCorrelationId(request.getCorrelationId()).setResponse(message.toString()).build();
    }

    public static RpcResponse.Builder builder() {
        return RpcResponse.newBuilder();
    }
}