package com.memora.operations;

import com.google.inject.Inject;
import com.memora.core.MemoraNode;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;

public class ReplicateOperation extends Operation {
    private static final String REPLICATE_COMMAND = "REPLICATE";
    
    private final MemoraNode memoraNode;

    @Inject
    public ReplicateOperation(
        final MemoraNode memoraNode
    ) {
        this.memoraNode = memoraNode;
    }

    @Override
    public RpcResponse execute(RpcRequest request) { 
        String command = request.command();
        String[] parts = command.split(" ");
        if (!REPLICATE_COMMAND.equalsIgnoreCase(parts[0])) {
            throw new IllegalArgumentException("Invalid command for ReplicateCommand: " + command);
        }

        if (parts.length < 3) {
            throw new IllegalArgumentException("REPLICATE command requires exactly 2 arguments: host and port");
        }

        memoraNode.replicate(parts[1], Integer.parseInt(parts[2]));

        return RpcResponse.OK;
    }
}
