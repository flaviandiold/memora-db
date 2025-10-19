package com.memora.services;

import java.util.Map;

import com.google.inject.Inject;
import com.memora.enums.Operations;
import com.memora.executors.DelExecutor;
import com.memora.executors.Executor;
import com.memora.executors.GetExecutor;
import com.memora.executors.InfoExecutor;
import com.memora.executors.NodeExecutor;
import com.memora.executors.PutExecutor;
import com.memora.executors.UnknownExecutor;
import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.messages.RpcStatus;
import com.memora.utils.ResponseFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CommandExecutor {

    private final Map<Operations, Executor> commands;

    @Inject
    public CommandExecutor(
            final PutExecutor putExecutor,
            final GetExecutor getExecutor,
            final DelExecutor delExecutor,
            final NodeExecutor nodeExecutor,
            final InfoExecutor infoExecutor,
            final UnknownExecutor unknownExecutor
    ) {
        commands = Map.of(
                Operations.PUT, putExecutor,
                Operations.GET, getExecutor,
                Operations.DELETE, delExecutor,
                Operations.NODE, nodeExecutor,
                Operations.INFO, infoExecutor,
                Operations.UNKNOWN, unknownExecutor
        );

    }

    public RpcResponse execute(RpcRequest request) {
        try {
            log.info("Executing request: {}", request);
            Operations operation = Executor.commandOf(request.getCommandCase());
            return commands.get(operation).execute(request);
        } catch (Exception e) {
            log.error("Error executing request: {}", request, e);
            return ResponseFactory.create(RpcStatus.ERROR, request, e.getMessage());
        }
    }
}
