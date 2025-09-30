package com.memora.operations;

import java.util.Objects;

import com.google.inject.Inject;
import com.memora.enums.Operations;
import com.memora.model.CacheEntry;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;
import com.memora.services.BucketManager;

public class GetOperation extends Operation {

    private static final String GET_COMMAND = Operations.GET.operation();

    private final BucketManager bucketManager;

    @Inject
    public GetOperation(
        final BucketManager bucketManager
    ) {
        this.bucketManager = bucketManager;
    }

    @Override
    public RpcResponse execute(RpcRequest request) {
        String[] parts = request.command().split(" ");

        if (!parts[0].equalsIgnoreCase(GET_COMMAND)) {
            throw new IllegalCallerException("Invalid command for GetCommand");
        }

        if (parts.length < 2) {
            return RpcResponse.BAD_REQUEST;
        }
        String key = parts[1];
        CacheEntry entry = bucketManager.get(key);
        if (Objects.isNull(entry)) {
            return RpcResponse.NOT_FOUND;
        } else {
            return RpcResponse.OK(entry.getValue());
        }
    }
    
}
