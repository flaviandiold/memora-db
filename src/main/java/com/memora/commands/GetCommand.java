package com.memora.commands;

import java.util.Objects;

import com.memora.model.CacheEntry;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;

public class GetCommand extends Command {

    @Override
    public RpcResponse execute(RpcRequest request) {
        String[] parts = request.command().split(" ");

        if (parts.length < 2) {
            return RpcResponse.BAD_REQUEST;
        }
        String key = parts[1];
        CacheEntry entry = bucketManager.get(key);
        if (Objects.isNull(entry)) {
            return RpcResponse.NOT_FOUND;
        } else {
            return RpcResponse.builder().response(entry.getValue()).build();
        }
    }
    
}
