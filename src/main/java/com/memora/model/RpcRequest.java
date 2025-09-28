package com.memora.model;

import java.io.Serializable;

import lombok.Builder;

@Builder
public record RpcRequest(
    String operation, // PUT, GET, DELETE
    String command,
    long version // Used for replication
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public RpcRequest(String operation, String command, long version) {
        this.operation = operation;
        this.command = command;
        this.version = version;
    }
}