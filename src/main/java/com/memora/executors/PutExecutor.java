package com.memora.executors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.inject.Inject;
import com.memora.core.MemoraNode;
import com.memora.messages.PutCommand;
import com.memora.messages.PutCommandBatch;
import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.model.CacheEntry;
import com.memora.model.NodeInfo;
import com.memora.store.WAL;

public class PutExecutor extends Executor {

    private static final int DEFAULT_EXPIRY = -1;
    private static final String EXPIRY = "EX";
    private static final String EXACT_EXPIRY = "EXAT";

    private final MemoraNode node;

    @Inject
    public PutExecutor(
        final MemoraNode node
    ) {
        this.node = node;
    }

    @Override
    public RpcResponse execute(RpcRequest request) {

        NodeInfo currentNode = MemoraNode.getInfo();
        if (currentNode.isReplica() && Objects.isNull(request.getNodeVersion())) {
            return node.forwardToPrimary(request).setCorrelationId(request.getCorrelationId()).build();
        }

        Map<String, CacheEntry> entries = parsePutCommand(request);
        List<String> keys = new ArrayList<>(entries.keySet());

        if (currentNode.isPrimary()) {
            Map<String, List<String>> nodeToKeysMap = node.getKeyToNodeMap(keys);
            if (nodeToKeysMap.size() > 1) {
                Map<String, List<CacheEntry>> entriesByNode = new HashMap<>();
                for (Map.Entry<String, List<String>> entry : nodeToKeysMap.entrySet()) {
                    String nodeId = entry.getKey();
                    for (String key : entry.getValue()) {
                        entriesByNode.computeIfAbsent(nodeId, k -> new ArrayList<>()).add(entries.get(key));
                    }
                }
                return node.forwardPut(entriesByNode).setCorrelationId(request.getCorrelationId()).build();
            }
        }

        WAL.log(request);
        if (entries.size() == 1) {
            node.put(entries.get(keys.get(0)));
        } else {
            node.putAll(entries.values());
        }
        return OK(request);
    }

    private Map<String, CacheEntry> parsePutCommand(RpcRequest request) {
        Map<String, CacheEntry> entries = new HashMap<>();

        PutCommandBatch putCommandBatch = request.getPutCommand();

        for (PutCommand putCommand: putCommandBatch.getCommandsList()) {
            String key = putCommand.getKey();
            CacheEntry entry = CacheEntry.builder()
                    .key(key)
                    .value(putCommand.getValue().toStringUtf8())
                    .ttl(switch (putCommand.getExpiryCase()) {
                        case EXPIRE_IN_SECONDS -> getTTL(String.valueOf(putCommand.getExpireInSeconds()), EXPIRY);
                        case EXPIRE_AT_TIMESTAMP -> getTTL(String.valueOf(putCommand.getExpireAtTimestamp()), EXACT_EXPIRY);
                        case EXPIRY_NOT_SET -> DEFAULT_EXPIRY;
                        default -> DEFAULT_EXPIRY;
                    })
                    .build();
            entries.put(key, entry);
        }

        return entries;
    }


    private long getTTL(String value, String type) {
        try {
            return switch (type.toUpperCase()) {
                case EXPIRY -> {
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
