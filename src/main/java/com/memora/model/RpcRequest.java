package com.memora.model;

import java.io.Serializable;

import lombok.Builder;

@Builder
public record RpcRequest(
        CommandType commandType,
        String key,
        String value,
        long version // Used for replication
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public RpcRequest(CommandType commandType, String key) {
        this(commandType, key, null, -1);
    }

    public RpcRequest(CommandType commandType, String key, String value) {
        this(commandType, key, value, -1);
    }

    public RpcRequest(CommandType commandType, String key, long version) {
        this(commandType, key, null, version);
    }
    public enum CommandType {
        // Client commands
        PUT,
        GET,
        DELETE,
        // Internal/Cluster commands
        GOSSIP,
        REPLICATE,
        REQUEST_WAL_SYNC,
        UNKNOWN
    }

    public static CommandType commandOf(String command) {
        for (CommandType cmd: CommandType.values()) {
            if (cmd.name().equalsIgnoreCase(command)) {
                return cmd;
            }
        }
        return CommandType.UNKNOWN;
    }
}