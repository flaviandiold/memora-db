package com.memora.operations;

import java.util.Objects;

import com.google.inject.Inject;
import com.memora.core.MemoraNode;
import com.memora.enums.Operations;
import com.memora.model.CacheEntry;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;

public class GetOperation extends Operation {

    private static final String GET_COMMAND = Operations.GET.operation();

    private final MemoraNode memoraNode;

    @Inject
    public GetOperation(
        final MemoraNode memoraNode
    ) {
        this.memoraNode = memoraNode;
    }

    @Override
    public RpcResponse execute(RpcRequest request) {
        String[] parts = request.getCommand().split(" ");

        if (!parts[0].equalsIgnoreCase(GET_COMMAND)) {
            throw new IllegalCallerException("Invalid command for GetCommand");
        }

        if (parts.length < 2) {
            return RpcResponse.BAD_REQUEST;
        }
        String key = parts[1];
        CacheEntry entry = memoraNode.get(key);
        if (Objects.isNull(entry)) {
            return RpcResponse.NOT_FOUND;
        } else {
            return RpcResponse.OK(entry.getValue());
        }
    }
    
}
