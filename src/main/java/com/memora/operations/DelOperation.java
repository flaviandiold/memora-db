package com.memora.operations;

import com.google.inject.Inject;
import com.memora.core.Version;
import com.memora.enums.Operations;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;
import com.memora.services.BucketManager;
import com.memora.store.WAL;

public class DelOperation extends Operation {

    private final BucketManager bucketManager;
    private final Version version;


    @Inject
    public DelOperation(
        final BucketManager bucketManager,
        final Version version
    ) {
        this.bucketManager = bucketManager;
        this.version = version;
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
        bucketManager.delete(key);
        version.increment();
        return RpcResponse.OK;
    }

}
