package com.memora.executors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.inject.Inject;
import com.memora.core.MemoraNode;
import com.memora.messages.KeyCommand;
import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.messages.RpcStatus;
import com.memora.model.CacheEntry;
import com.memora.model.NodeInfo;

public class GetExecutor extends Executor {

    private final MemoraNode memoraNode;

    @Inject
    public GetExecutor(
            final MemoraNode memoraNode) {
        this.memoraNode = memoraNode;
    }

    @Override
    public RpcResponse execute(RpcRequest request) {
        RpcStatus status = RpcStatus.OK;
        NodeInfo currentNode = MemoraNode.getInfo();

        List<String> keys = parseGetRequest(request);
        List<String> values = new ArrayList<>();

        Map<String, List<String>> nodeToKeysMap = memoraNode.getKeyToNodeMap(keys);

        if (!currentNode.isStandAlone()) {
            String nodeId = currentNode.isReplica()
                    ? memoraNode.getClusterMap().getMyPrimary(currentNode.getNodeId()).getNodeId()
                    : currentNode.getNodeId();
            if (nodeToKeysMap.size() > 1 ||
                    !nodeToKeysMap.containsKey(nodeId)) {
                return memoraNode.forwardGet(nodeToKeysMap).setCorrelationId(request.getCorrelationId()).build();
            }
        }

        for (String key : keys) {
            CacheEntry entry = memoraNode.get(key);
            if (Objects.isNull(entry)) {
                status = RpcStatus.PARTIAL_FULFILLMENT;
                values.add(null);
            } else {
                values.add(entry.getValue());
            }
        }

        if (values.size() == 1) {
            if (Objects.isNull(values.get(0)))
                return NOT_FOUND(request);
            else
                return OK(request, values.get(0));
        }

        return respond(request, status, values);
    }

    private List<String> parseGetRequest(RpcRequest request) {
        return request.getGetCommand().getCommandsList().stream().map(KeyCommand::getKey).toList();
    }

}
