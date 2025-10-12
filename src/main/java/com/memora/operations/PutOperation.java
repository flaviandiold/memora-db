package com.memora.operations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.inject.Inject;
import com.memora.core.MemoraNode;
import com.memora.enums.Operations;
import com.memora.model.CacheEntry;
import com.memora.model.NodeInfo;
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
        String[] parts = request.getCommand().split(" ");
        if (!parts[0].equalsIgnoreCase(PUT_COMMAND)) {
            throw new IllegalCallerException("Invalid command for PutCommand");
        }
        
        if (parts.length < 3 || parts.length % 2 == 0) {
            return RpcResponse.BAD_REQUEST("PUT command malformed");
        }

    
        NodeInfo currentNode = MemoraNode.getInfo();
        if (currentNode.isReplica() && Objects.isNull(request.getVersion())) {
            return node.forwardToPrimary(request.getCommand());
        }

        Map<String, CacheEntry> entries = parsePutCommand(request.getCommand());
        List<String> keys = new ArrayList<>(entries.keySet());

        if (currentNode.isPrimary()) {
            Map<String, List<String>> nodeToKeysMap = node.getKeyToNodeMap(keys);
            System.out.println("Node to Keys Map: " + nodeToKeysMap);
            if (nodeToKeysMap.size() > 1) {
                Map<String, List<CacheEntry>> entriesByNode = new HashMap<>();
                for (Map.Entry<String, List<String>> entry : nodeToKeysMap.entrySet()) {
                    String nodeId = entry.getKey();
                    for (String key : entry.getValue()) {
                        entriesByNode.computeIfAbsent(nodeId, k -> new ArrayList<>()).add(entries.get(key));
                    }
                }
                node.forwardPut(entriesByNode);
                return RpcResponse.OK;
            }
        }

        WAL.log(request);
        System.out.println("Entries to put: " + entries);
        if (entries.size() == 1) {
            node.put(entries.get(keys.get(0)));
        } else {
            node.putAll(entries.values());
        }
        return RpcResponse.OK;
    }

    private Map<String, CacheEntry> parsePutCommand(String command) {
        final String EXPIRY = "EX";
        final String EXACT_EXPIRY = "EXAT";
        final long DEFAULT_EXPIRY = -1;

        String[] parts = command.split(" ");
        if (parts.length < 3 || parts.length % 2 == 0) {
            throw new IllegalArgumentException("Malformed PUT command");
        }

        Map<String, CacheEntry> entries = new HashMap<>();

        int i = 1; // Start after "PUT"
        while (i < parts.length) {
            String key = parts[i++];
            if (i >= parts.length) throw new IllegalArgumentException("Missing value for key: " + key);

            String value = parts[i++];
            long ttl = DEFAULT_EXPIRY;

            // Check if next token is EX/EXAT
            if (i < parts.length) {
                String nextToken = parts[i];
                if (nextToken.equalsIgnoreCase(EXPIRY) || nextToken.equalsIgnoreCase(EXACT_EXPIRY)) {
                    if (i + 1 >= parts.length) throw new IllegalArgumentException("Missing TTL value for key: " + key);
                    ttl = getTTL(parts[i + 1], nextToken);
                    i += 2; // Skip TTL token and its value
                }
            }

            CacheEntry entry = CacheEntry.builder()
                    .key(key)
                    .value(value)
                    .ttl(ttl)
                    .build();
            entries.put(key, entry);
        }

        return entries;
    }


    private long getTTL(String value, String type) {
        try {
            System.out.println("Parsing TTL: " + value + " of type: " + type);
            return switch (type.toUpperCase()) {
                case EXPIRY -> {
                    System.out.println("Current time: " + System.currentTimeMillis() + ", Expiry in ms: " + (Long.parseLong(value) * 1000L));
                   yield System.currentTimeMillis() + (Long.parseLong(value) * 1000L);
            }
                case EXACT_EXPIRY -> Long.parseLong(value);
                default -> DEFAULT_EXPIRY;
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid TTL value: " + value, e);
        }
    }

}
