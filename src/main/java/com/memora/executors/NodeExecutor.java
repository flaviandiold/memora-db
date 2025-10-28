package com.memora.executors;

import java.util.List;

import com.google.inject.Inject;
import com.memora.core.MemoraNode;
import com.memora.enums.NodeType;
import com.memora.messages.NodeAddress;
import com.memora.messages.NodeCommand;
import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.model.ClusterMap;
import com.memora.utils.Parser;
import com.memora.messages.NodeCommand.BehaveCommand;
import com.memora.messages.NodeCommand.PrimarizeCommand;
import com.memora.messages.NodeCommand.ReplicateCommand;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NodeExecutor extends Executor {

    private final MemoraNode node;

    @Inject
    public NodeExecutor(
            final MemoraNode node
    ) {
        this.node = node;
    }

    @Override
    public RpcResponse execute(RpcRequest request) {
        NodeCommand command = request.getNodeCommand();

        switch (command.getCommandCase()) {
            case PRIMARIZE ->
                handlePrimarize(command);
            case REPLICATE ->
                handleReplicate(command);
            case BEHAVE ->
                handleBehave(command);
            default -> {
                return UNSUPPORTED_OPERATION(request, "Invalid sub-command for NodeCommand: " + command.getCommandCase());
            }
        }

        return OK(request);
    }

    private void handlePrimarize(NodeCommand request) {
        PrimarizeCommand primarize = request.getPrimarize();
        for (NodeAddress address: primarize.getReplicasList()) {
            node.primarize(address.getHost(), address.getPort());
        }
    }

    private void handleReplicate(NodeCommand request) {
        ReplicateCommand replicate = request.getReplicate();
        switch (replicate.getCommandCase()) {
            case SOURCE:
                ReplicateCommand.Data data = replicate.getSource();
                NodeAddress address = data.getPrimary();
                node.replicate(address.getHost(), address.getPort());
                break;

            case BUCKET:
                ReplicateCommand.Bucket bucket = replicate.getBucket();
                node.copyBucketIds(bucket.getNodeId(), List.copyOf(bucket.getBucketIdsList()));
                break;
            
            case CLUSTER:
                ReplicateCommand.Cluster cluster = replicate.getCluster();
                node.copyClusterMap(Parser.fromJson(cluster.getMap(), ClusterMap.class));
                break;
            default:
                break;
        }
    }

    private void handleBehave(NodeCommand request) {
        BehaveCommand behave = request.getBehave();
        NodeType type = switch (behave.getType()) {
            case PRIMARY -> NodeType.PRIMARY;
            case STANDALONE -> NodeType.STANDALONE;
            case REPLICA -> NodeType.REPLICA; 
            default -> throw new IllegalArgumentException("Invalid node type: " + behave.getType());
        };
        if (MemoraNode.getInfo().getType().equals(type)) return;

        // Intentional, since a node should not behave as REPLICA without a primary
        if (type.equals(NodeType.REPLICA)) type = NodeType.STANDALONE;

        node.behave(type);
    }
}
