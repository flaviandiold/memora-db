package com.memora.commands;

import com.google.inject.Inject;
import com.memora.core.Version;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;
import com.memora.services.BucketManager;
import com.memora.store.WAL;

public class DelCommand extends Operation {

    private final BucketManager bucketManager;
    private final Version version;

    @Inject
    public DelCommand(
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
        if (parts.length < 2) {
            return RpcResponse.BAD_REQUEST;
        }
        String key = parts[1];
        bucketManager.delete(key);
        version.increment();
        return RpcResponse.OK;
    }

}
