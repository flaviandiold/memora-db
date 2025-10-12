package com.memora.model;

import java.io.Serializable;

import javax.annotation.Nullable;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RpcRequest implements Serializable {


    private final String operation; // PUT, GET, DELETE
    private final String command;
    private @Nullable final Long version; // Used for replication

    private static final long serialVersionUID = 1L;

    public RpcRequest(String operation, String command, Long version) {
        this.operation = operation;
        this.command = command;
        this.version = version;
    }

    public RpcRequest(String operation, String command) {
        this(operation, command, null);
    }
}