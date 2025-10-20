package com.memora.executors;

import com.memora.messages.ClusterCommand;
import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.messages.ClusterCommand.ClusterNodeCommand;

public class ClusterExecutor extends Executor {

    @Override
    public RpcResponse execute(RpcRequest request) {
        final ClusterCommand command = request.getClusterCommand();

        return switch (command.getCommandCase()) {
            case NODE_COMMAND -> {
                final ClusterNodeCommand nodeCommand = command.getNodeCommand();
                yield switch (nodeCommand.getType()) {
                    case ADD_NODES -> {
                        yield null;
                    }
                    case REMOVE_NODES -> {
                        yield null;
                    }
                    default -> UNSUPPORTED_OPERATION(request);
                };
            }
            default -> UNSUPPORTED_OPERATION(request);
        };
    }
    
}