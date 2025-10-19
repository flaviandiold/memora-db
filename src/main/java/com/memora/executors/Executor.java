package com.memora.executors;

import com.memora.enums.Operations;
import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.messages.RpcStatus;
import com.memora.messages.RpcRequest.CommandCase;
import com.memora.utils.ResponseFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class Executor {
    abstract public RpcResponse execute(RpcRequest request);

    public RpcResponse OK(RpcRequest request) {
        return respond(request, RpcStatus.OK);
    }

    public RpcResponse OK(RpcRequest request, Object response) {
        return respond(request, RpcStatus.OK, response);
    }

    public RpcResponse UNSUPPORTED_OPERATION(RpcRequest request) {
        return respond(request, RpcStatus.UNSUPPORTED_OPERATION);
    }

    public RpcResponse UNSUPPORTED_OPERATION(RpcRequest request, String message) {
        return respond(request, RpcStatus.UNSUPPORTED_OPERATION, message);
    }

    public RpcResponse ERROR(RpcRequest request) {
        return respond(request, RpcStatus.ERROR);
    }

    public RpcResponse ERROR(RpcRequest request, String message) {
        return respond(request, RpcStatus.ERROR, message);
    }

    public RpcResponse NOT_FOUND(RpcRequest request) {
        return respond(request, RpcStatus.NOT_FOUND);
    }

    public RpcResponse BAD_REQUEST(RpcRequest request) {
        return respond(request, RpcStatus.BAD_REQUEST);
    }

    public RpcResponse respond(RpcRequest request, RpcStatus status, Object response) {
        return ResponseFactory.create(status, request, response);
    }

    public RpcResponse respond(RpcRequest request, RpcStatus status) {
        return ResponseFactory.create(status, request);
    }

    public static Operations commandOf(final CommandCase commandCase) {
        return switch (commandCase) {
            case GET_COMMAND -> Operations.GET;
            case PUT_COMMAND -> Operations.PUT;
            case DELETE_COMMAND -> Operations.DELETE;
            case NODE_COMMAND -> Operations.NODE;
            case INFO_COMMAND -> Operations.INFO;
            case CLUSTER_COMMAND, COMMAND_NOT_SET -> Operations.UNKNOWN;
        };
    }
}
