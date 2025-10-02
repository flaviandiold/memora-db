package com.memora.operations;

import com.google.inject.Inject;
import com.memora.core.MemoraNode;
import com.memora.enums.Operations;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;
import com.memora.store.WAL;

public class DelOperation extends Operation {

    private final MemoraNode node;


    @Inject
    public DelOperation(
        final MemoraNode node
    ) {
        this.node = node;
    }

    @Override
    public RpcResponse execute(RpcRequest request) {
        WAL.log(request);
        String[] parts = request.command().split(" ");
        if (!parts[0].equalsIgnoreCase(Operations.DELETE.operation())) {
            throw new IllegalCallerException("Invalid command for DeleteCommand");
        }
        if (parts.length < 2) {
            return RpcResponse.BAD_REQUEST("DELETE command requires at least 1 argument");
        }
        String key = parts[1];
        node.delete(key);
        return RpcResponse.OK;
    }

}
