package com.memora.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.memora.core.MemoraNode;
import com.memora.exceptions.MemoraException;
import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.messages.RpcStatus;
import com.memora.model.CacheEntry;
import com.memora.model.ClusterMap;
import com.memora.utils.Parser;
import com.memora.utils.ResponseFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ForwarderService {

    private final ClusterMap clusterMap;
    private final ClientManager clientManager;



    public RpcResponse.Builder forwardGet(Map<String, List<String>> nodeToKeysMap) {
        for (String nodeId : nodeToKeysMap.keySet()) {
            if (!clusterMap.containsNode(nodeId)) {
                return ResponseFactory.builder().setStatus(RpcStatus.BAD_REQUEST)
                        .setResponse("Node " + nodeId + " not found in cluster");
            }
        }
        List<String> values = new ArrayList<>();
        RpcStatus status = RpcStatus.OK;

        for (String nodeId : nodeToKeysMap.keySet()) {
            List<String> keys = nodeToKeysMap.get(nodeId);
            try {
                RpcResponse response = clientManager.getClient(nodeId).get(keys).get();
                if (!response.getStatus().equals(RpcStatus.OK) && status.equals(RpcStatus.OK)) {
                    status = RpcStatus.PARTIAL_FULFILLMENT;
                }
                if (status.equals(RpcStatus.OK)) {
                    if (keys.size() == 1) {
                        values.add(response.getResponse());
                    } else {
                        List<String> responses = Parser.fromJson(response.getResponse(), new TypeToken<List<String>>() {
                        }.getType());
                        for (String value : responses) {
                            values.add(value);
                        }
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("Failed to forward get request to node {}: {}", nodeId, e.getMessage());
                status = RpcStatus.ERROR;
            }
        }
        if (values.size() == 1) {
            if (Objects.isNull(values.get(0)))
                return ResponseFactory.builder().setStatus(RpcStatus.NOT_FOUND);
            else
                return ResponseFactory.builder().setStatus(status).setResponse(values.get(0));
        }

        return ResponseFactory.builder().setStatus(status).setResponse(values.toString());
    }

    public RpcResponse.Builder forwardPut(Map<String, List<CacheEntry>> entriesByNode) {
        for (String nodeId : entriesByNode.keySet()) {
            if (!clusterMap.containsNode(nodeId)) {
                return ResponseFactory.builder().setStatus(RpcStatus.BAD_REQUEST)
                        .setResponse("Node " + nodeId + " not found in cluster");
            }
        }
        List<String> failedKeys = new ArrayList<>();
        for (String nodeId : entriesByNode.keySet()) {
            List<CacheEntry> entries = entriesByNode.get(nodeId);
            List<String> failedPuts = clientManager.getClient(nodeId).put(entries);
            if (!failedKeys.isEmpty()) {
                failedKeys.addAll(failedPuts);
            }
        }
        if (failedKeys.isEmpty()) {
            return ResponseFactory.builder().setStatus(RpcStatus.OK);
        } else {
            return ResponseFactory.builder().setStatus(RpcStatus.PARTIAL_FULFILLMENT)
                    .setResponse("Failed to put keys: " + String.join(", ", failedKeys));
        }
    }

    public RpcResponse.Builder forwardToPrimary(RpcRequest request) {
        String primaryId = clusterMap.getMyPrimary(MemoraNode.getInfo().getNodeId()).getNodeId();
        return forwardToNode(request, primaryId);
    }

    public RpcResponse.Builder forwardToNode(RpcRequest request, String nodeId) {
        try {
            return RpcResponse.newBuilder(clientManager.getClient(nodeId).call(request).get());
        } catch (MemoraException | InterruptedException | ExecutionException e) {
            log.error("Failed to forward request to node {}: {}", nodeId, e.getMessage());
            return ResponseFactory.builder().setStatus(RpcStatus.ERROR)
                    .setResponse("Failed to forward request to node");
        }
    }

    
}
