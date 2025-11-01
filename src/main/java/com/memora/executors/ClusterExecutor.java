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
import com.memora.messages.ClusterCommand.ClusterNodeCommand.LockCommand;
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

        return switch (command.getCommandCase()) {
            case NODE_COMMAND -> {
                final ClusterNodeCommand nodeCommand = command.getNodeCommand();
                yield switch (nodeCommand.getCommandCase()) {
                    case ADD_COMMAND -> {
                        if (!MemoraNode.getInfo().isStandAlone()) {
                            String clusterLeader = node.getClusterLeader();
                            if (!MemoraNode.getInfo().getNodeId().equals(clusterLeader)) {
                                yield node
                                    .forwardToNode(request, clusterLeader)
                                    .setCorrelationId(request.getCorrelationId())
                                    .build();
                            }
                        } else {
                            node.behave(NodeType.PRIMARY, false);
                        }

                        final AddNodesCommand addNodesCommand = nodeCommand.getAddCommand();
                        boolean addPrimary = addNodesCommand.getModifier().equals(Modifier.PRIMARY);
                        List<NodeBase> nodes = addNodesCommand.getNodesList().stream()
                                .map(NodeBase::create)
                                .toList();
                        node.addNodes(nodes, addPrimary);
                        yield OK(request);
                    }
                    case JOIN_COMMAND -> {
                        final NodeBase newNode = NodeBase.create(nodeCommand.getJoinCommand().getNode());
                        node.join(newNode);
                        yield OK(request);
                    }
                    case FORGET_COMMAND -> {
                        final List<String> nodeIds = nodeCommand.getForgetCommand().getNodesList();
                        nodeIds.forEach(node::forget);
                        yield OK(request);
                    }
                    case REMOVE_COMMAND -> {
                        yield OK(request);
                    }
                    case LOCK_COMMAND -> {
                        final LockCommand lockCommand = nodeCommand.getLockCommand();
                        if (lockCommand.getLock()) {
                            if (!ClusterInfo.getState().equals(ClusterState.REDISTRIBUTION_LOCKED)
                                    || !ClusterInfo.getState().equals(ClusterState.REDISTRIBUTING)) {
                                ClusterInfo.setState(ClusterState.REDISTRIBUTION_LOCKED);
                            }
                        } else {
                            ClusterInfo.setState(ClusterState.ACTIVE);
                        }
                        yield OK(request);
                    }
                    default -> UNSUPPORTED_OPERATION(request);
                };
            }
            default -> UNSUPPORTED_OPERATION(request);
        };
    }

}