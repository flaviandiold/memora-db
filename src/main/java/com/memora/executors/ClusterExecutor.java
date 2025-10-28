package com.memora.executors;

import java.util.List;

import com.memora.core.MemoraNode;
import com.memora.enums.ClusterState;
import com.memora.enums.NodeType;
import com.memora.messages.ClusterCommand;
import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.messages.ClusterCommand.ClusterNodeCommand;
import com.memora.messages.ClusterCommand.ClusterNodeCommand.AddNodesCommand;
import com.memora.messages.ClusterCommand.ClusterNodeCommand.AddNodesCommand.Modifier;
import com.memora.model.ClusterInfo;
import com.memora.model.NodeBase;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ClusterExecutor extends Executor {

    private final MemoraNode node;

    @Override
    public synchronized RpcResponse execute(RpcRequest request) {
        final ClusterCommand command = request.getClusterCommand();

        if (!MemoraNode.getInfo().isStandAlone()) {
            String clusterLeader = node.getClusterLeader();
            if (!MemoraNode.getInfo().getNodeId().equals(clusterLeader)) {
                return node
                        .forwardToNode(request, clusterLeader)
                        .setCorrelationId(request.getCorrelationId())
                        .build();
            }
        } else {
            node.behave(NodeType.PRIMARY, false);
        }

        return switch (command.getCommandCase()) {
            case NODE_COMMAND -> {
                final ClusterNodeCommand nodeCommand = command.getNodeCommand();
                yield switch (nodeCommand.getCommandCase()) {
                    case ADD_COMMAND -> {
                        final AddNodesCommand addNodesCommand = nodeCommand.getAddCommand();
                        boolean addPrimary = addNodesCommand.getModifier().equals(Modifier.PRIMARY);
                        if (addPrimary) {
                            if (
                                ClusterInfo.getState().equals(ClusterState.REDISTRIBUTION_LOCKED) ||
                                ClusterInfo.getState().equals(ClusterState.REDISTRIBUTING)
                            ) {
                                yield ERROR(request, "Cluster is already in redistribution state");
                            }
                            ClusterInfo.setState(ClusterState.REDISTRIBUTION_LOCKED);
                        }
                        List<NodeBase> nodes = addNodesCommand.getNodesList().stream()
                            .map(NodeBase::create)
                            .toList();
                        node.handleAddNodes(nodes, addPrimary);
                        yield OK(request);
                    }
                    case JOIN_COMMAND -> {
                        final NodeBase newNode = NodeBase.create(nodeCommand.getJoinCommand().getNode());
                        node.join(newNode);
                        yield OK(request);
                    }
                    case REMOVE_COMMAND -> {
                        yield OK(request);
                    }
                    default -> UNSUPPORTED_OPERATION(request);
                };
            }
            default -> UNSUPPORTED_OPERATION(request);
        };
    }
    
}