package com.memora.commands;

import com.memora.core.MemoraNode;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;
import com.memora.store.WAL;

public class DelCommand extends Command {

    @Override
    public RpcResponse execute(RpcRequest request) {
        WAL.log(request);
        String[] parts = request.command().split(" ");
        if (parts.length < 2) {
            return RpcResponse.BAD_REQUEST;
        }
        String key = parts[1];
        bucketManager.delete(key);
        MemoraNode.incrementVersion();
        return RpcResponse.OK;
    }

}
