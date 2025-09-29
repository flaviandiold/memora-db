package com.memora.services;

import java.util.Map;

import com.google.inject.Inject;
import com.memora.constants.Operations;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;
import com.memora.operations.DelOperation;
import com.memora.operations.GetOperation;
import com.memora.operations.InfoOperation;
import com.memora.operations.Operation;
import com.memora.operations.PutOperation;
import com.memora.operations.ReplicateOperation;
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
            final ReplicateOperation replicateCommand,
            final InfoOperation infoCommand,
            final UnknownOperation unknownCommand
    ) {
        commands = Map.of(
                Operations.PUT, putCommand,
                Operations.GET, getCommand,
                Operations.DELETE, delCommand,
                Operations.REPLICATE, replicateCommand,
                Operations.INFO, infoCommand,
                Operations.UNKNOWN, unknownCommand
        );

    }

    public RpcResponse execute(RpcRequest request) {
        try {
            log.info("Executing request: {}", request);
            Operations operation = Operation.commandOf(request.operation());
            return commands.get(operation).execute(request);
        } catch (Exception e) {
            log.error("Error executing request: {}", request, e);
            return RpcResponse.ERROR;
        }
    }
}
