package com.memora.executors;

import com.google.inject.Inject;
import com.memora.core.MemoraNode;
import com.memora.messages.NodeAddress;
import com.memora.messages.NodeCommand;
import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
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
                handleReplicate(command, request.getClusterEpoch());
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

    private void handleReplicate(NodeCommand request, long clusterEpoch) {
        ReplicateCommand replicate = request.getReplicate();
        NodeAddress address = replicate.getPrimary();
        node.replicate(address.getHost(), address.getPort(), clusterEpoch);
    }
}
