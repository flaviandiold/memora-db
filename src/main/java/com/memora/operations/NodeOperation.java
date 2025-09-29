package com.memora.operations;

import com.google.inject.Inject;
import com.memora.core.MemoraNode;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;

public class NodeOperation extends Operation {
    private static final String NODE_COMMAND = "NODE";
    private static final String PRIMARIZE_COMMAND = "PRIMARIZE";
    private static final String REPLICATE_COMMAND = "REPLICATE";
    
    private final MemoraNode memoraNode;

    @Inject
    public NodeOperation(
        final MemoraNode memoraNode
    ) {
        this.memoraNode = memoraNode;
    }

    @Override
    public RpcResponse execute(RpcRequest request) { 
        String command = request.command();
        String[] parts = command.split(" ");
        if (parts.length < 3) {
            throw new IllegalArgumentException("NODE command requires at least 2 arguments");
        }

        if (!NODE_COMMAND.equalsIgnoreCase(parts[0])) {
            throw new IllegalArgumentException("Invalid command for NodeCommand: " + command);
        }

        final String address[] = parts[2].split("@");
        if (address.length != 2) {
            throw new IllegalArgumentException("Invalid address for NodeCommand: " + parts[2]);
        }

        switch (parts[1].toUpperCase()) {
            case PRIMARIZE_COMMAND -> {
                memoraNode.primarize(address[0], Integer.parseInt(address[1]));
            }
            case REPLICATE_COMMAND -> {
                memoraNode.replicate(address[0], Integer.parseInt(address[1]));
            }
            default -> throw new IllegalArgumentException("Invalid sub-command for NodeCommand: " + parts[1]);
        }

        return RpcResponse.OK;
    }
}
