package com.memora.model;

import java.io.Serializable;

import lombok.Builder;

@Builder
public record RpcRequest(
    String command,
    long version // Used for replication
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public RpcRequest(String command) {
        this(command, -1);
    }

    public RpcRequest(String command, long version) {
        this.command = command;
        this.version = version;
    }
}