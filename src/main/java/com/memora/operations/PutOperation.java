package com.memora.operations;

import java.util.HashMap;
import java.util.Map;

import com.google.inject.Inject;
import com.memora.core.MemoraNode;
import com.memora.enums.Operations;
import com.memora.model.CacheEntry;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;
import com.memora.store.WAL;

public class PutOperation extends Operation {

    private static final String PUT_COMMAND = Operations.PUT.operation();
    private static final String EXPIRY = "EX";
    private static final String EXACT_EXPIRY = "EXAT";
    private static final int DEFAULT_EXPIRY = -1;

    private final MemoraNode node;

    @Inject
    public PutOperation(
        final MemoraNode node
    ) {
        this.node = node;
    }

    @Override
    public RpcResponse execute(RpcRequest request) {
        String[] parts = request.command().split(" ");
        if (!parts[0].equalsIgnoreCase(PUT_COMMAND)) {
            throw new IllegalCallerException("Invalid command for PutCommand");
        }
        
        if (parts.length < 3 || parts.length % 2 == 0) {
            return RpcResponse.BAD_REQUEST("PUT command malformed");
        }
        
        Map<String, CacheEntry> entries = new HashMap<>();
        String prevKey = parts[1];
        CacheEntry.CacheEntryBuilder previousEntry = CacheEntry.builder().value(parts[2]);

        for (int i = 3; i < parts.length; i++) {
            if (parts[i].equals(EXPIRY) || parts[i].equals(EXACT_EXPIRY)) {
                previousEntry = previousEntry.ttl(getTTL(parts[i + 1], parts[i]));
                i++;
            } else {
                entries.put(prevKey, previousEntry.ttl(DEFAULT_EXPIRY).build());
                prevKey = parts[i];
                previousEntry = CacheEntry.builder().value(parts[i + 1]);
                i++;
            }
        }

        // Add the last entry
        entries.put(prevKey, previousEntry.ttl(DEFAULT_EXPIRY).build());
        
        // if (node.getInfo().isReplica()) {
        //     return node.forwardToPrimary(request);
        // }
        WAL.log(request);
        if (entries.size() == 1) {
            node.put(prevKey, entries.get(prevKey));
        } else {
            node.putAll(entries);
        }
        return RpcResponse.OK;
    }

    private long getTTL(String value, String type) {
        try {
            return switch (type.toUpperCase()) {
                case EXPIRY -> System.currentTimeMillis() + (Long.parseLong(value) * 1000L);
                case EXACT_EXPIRY -> Long.parseLong(value);
                default -> DEFAULT_EXPIRY;
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid TTL value: " + value, e);
        }
    }

}
