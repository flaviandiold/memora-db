package com.memora.services;

import java.util.Map;

import com.google.inject.Inject;
import com.memora.enums.Operations;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;
import com.memora.operations.DelOperation;
import com.memora.operations.GetOperation;
import com.memora.operations.InfoOperation;
import com.memora.operations.Operation;
import com.memora.operations.PutOperation;
import com.memora.operations.NodeOperation;
import com.memora.operations.UnknownOperation;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CommandExecutor {

    private final Map<Operations, Operation> commands;

    @Inject
    public CommandExecutor(
            final PutOperation putCommand,
            final GetOperation getCommand,
            final DelOperation delCommand,
            final NodeOperation nodeCommand,
            final InfoOperation infoCommand,
            final UnknownOperation unknownCommand
    ) {
        commands = Map.of(
                Operations.PUT, putCommand,
                Operations.GET, getCommand,
                Operations.DELETE, delCommand,
                Operations.NODE, nodeCommand,
                Operations.INFO, infoCommand,
                Operations.UNKNOWN, unknownCommand
        );

    }

    public boolean isStream() {
        return true;
    }

    public RpcResponse execute(RpcRequest request) {
        try {
            log.info("Executing request: {}", request);
            Operations operation = Operation.commandOf(request.operation());
            return commands.get(operation).execute(request);
        } catch (Exception e) {
            log.error("Error executing request: {}", request, e);
            return RpcResponse.ERROR(e.getLocalizedMessage());
        }
    }
}
