package com.memora.services;

import java.util.Map;

import com.google.inject.Inject;
import com.memora.commands.DelCommand;
import com.memora.commands.GetCommand;
import com.memora.commands.Operation;
import com.memora.commands.PutCommand;
import com.memora.commands.ReplicateCommand;
import com.memora.commands.UnknownCommand;
import com.memora.constants.Operations;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CommandExecutor {

    private final Map<Operations, Operation> commands;

    @Inject
    public CommandExecutor(
            final PutCommand putCommand,
            final GetCommand getCommand,
            final DelCommand delCommand,
            final ReplicateCommand replicateCommand,
            final UnknownCommand unknownCommand
    ) {
        commands = Map.of(
                Operations.PUT, putCommand,
                Operations.GET, getCommand,
                Operations.DELETE, delCommand,
                Operations.REPLICATE, replicateCommand,
                Operations.UNKNOWN, unknownCommand
        );

    }

    public RpcResponse execute(RpcRequest request) {
        log.info("Executing request: {}", request);
        Operations operation = Operation.commandOf(request.operation());
        return commands.get(operation).execute(request);
    }
}
