package com.memora.operations;

import com.memora.constants.Operations;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class Operation {
    abstract public RpcResponse execute(RpcRequest request);

    public static Operations commandOf(String operation) {
        for (Operations cmd: Operations.values()) {
            if (cmd.operation().equalsIgnoreCase(operation)) {
                return cmd;
            }
        }
        return Operations.UNKNOWN;
    }
}
