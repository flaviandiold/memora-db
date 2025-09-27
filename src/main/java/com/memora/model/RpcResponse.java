package com.memora.model;

import java.io.Serializable;

import lombok.Builder;

@Builder
public record RpcResponse(
        Status status,
        Object result
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Status {
        OK,
        ERROR,
        NOT_FOUND,
        REDIRECT, // To redirect client to the correct primary
        PRIMARY_DOWN
    }
}