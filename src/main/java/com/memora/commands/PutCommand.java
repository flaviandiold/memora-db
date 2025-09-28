package com.memora.commands;

import com.memora.core.MemoraNode;
import com.memora.model.CacheEntry;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;
import com.memora.store.WAL;

public class PutCommand extends Command {

    private static final String PUT_COMMAND = "PUT";
    private static final String EXPIRY = "EX";

    @Override
    public RpcResponse execute(RpcRequest request) {
        WAL.log(request);
        String[] parts = request.command().split(" ");
        if (parts.length < 3) {
            return RpcResponse.BAD_REQUEST;
        }
        if (!parts[0].equalsIgnoreCase(PUT_COMMAND)) {
            throw new IllegalCallerException("Invalid command for PutCommand");
        }

        String key = parts[1];
        String value = parts[2];
        long ttl = -1;
        if (parts.length > 3) {
            switch (parts[3].toUpperCase()) {
                case EXPIRY -> {
                    if (parts.length < 5) {
                        return RpcResponse.BAD_REQUEST;
                    }
                    try {
                        ttl = Long.parseLong(parts[4]);
                    } catch (NumberFormatException e) {
                        return RpcResponse.BAD_REQUEST;
                    }
                }
            }
        }
        bucketManager.put(key, CacheEntry.builder().value(value).ttl(ttl).build());
        MemoraNode.incrementVersion();
        return RpcResponse.OK;
    }

}
