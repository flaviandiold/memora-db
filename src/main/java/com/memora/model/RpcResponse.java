package com.memora.model;

import java.io.Serializable;

import lombok.Builder;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Builder
public class RpcResponse implements Serializable {
    public static final RpcResponse OK = new RpcResponse("OK");
    public static final RpcResponse ERROR = new RpcResponse("ERROR");
    public static final RpcResponse NOT_FOUND = new RpcResponse("NOT_FOUND");
    public static final RpcResponse UNSUPPORTED_COMMAND = new RpcResponse("UNSUPPORTED_COMMAND");

    private final String response;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Response='").append(response).append('\'').append('\n');
        return sb.toString();
    }

    public String getResponse() {
        return response;
    }
}
