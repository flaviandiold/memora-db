package com.memora.model;

import java.io.Serializable;
import java.util.List;

import lombok.Builder;

@Builder
public record RpcRequest(
        CommandType commandType,
        List<String> args,
        long version // Used for replication
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum CommandType {
        // Client commands
        PUT,
        GET,
        DELETE,
        // Internal/Cluster commands
        GOSSIP,
        REPLICATE,
        REQUEST_WAL_SYNC
    }

    public RpcRequest(CommandType commandType, List<String> args) {
        this(commandType, args, -1); // Default version for client requests
    }
}