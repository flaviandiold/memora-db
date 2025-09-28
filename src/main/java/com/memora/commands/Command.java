package com.memora.commands;

import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;
import com.memora.services.BucketManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class Command {
    protected final BucketManager bucketManager = BucketManager.getInstance();

    abstract public RpcResponse execute(RpcRequest request);

    public enum CommandType {
        // Client commands
        PUT("PUT", new PutCommand()),
        GET("GET", new GetCommand()),
        DELETE("DELETE", new DelCommand()),
        UNKNOWN("UNKNOWN", new UnknownCommand());

        private final String operation;
        private final Command command;

        CommandType(String operation, Command command) {
            this.operation = operation;
            this.command = command;
        }

        public RpcResponse execute(RpcRequest request) {
            log.info("Executing request: {}", request);
            return command.execute(request);
        }

        public String operation() {
            return operation;
        }
    }

    public static CommandType commandOf(String operation) {
        for (CommandType cmd: CommandType.values()) {
            if (cmd.operation().equalsIgnoreCase(operation)) {
                return cmd;
            }
        }
        return CommandType.UNKNOWN;
    }
}
