package com.memora.operations;

import com.memora.model.NodeInfo;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;

public class InfoOperation extends Operation {

    private static final String INFO_COMMAND = "INFO";
    private static final String NODE_SUB_COMMAND = "NODE";
    private static final String ID = "ID";

    private final NodeInfo info;

    public InfoOperation(NodeInfo info) {
        this.info = info;
    }

    @Override
    public RpcResponse execute(RpcRequest request) {
        String command = request.command();
        String[] parts = command.split(" ");
        if (parts.length < 3) {
            throw new IllegalArgumentException("INFO command requires at least 2 arguments");
        }
        if (!INFO_COMMAND.equalsIgnoreCase(parts[0])) {
            throw new IllegalArgumentException("Invalid command for InfoCommand: " + command);
        }
        
        return switch (parts[1]) {
            case NODE_SUB_COMMAND -> {
                yield switch (parts[2]) {
                    case ID -> RpcResponse.builder().response(info.getNodeId()).build();
                    default -> RpcResponse.UNSUPPORTED_OPERATION;
                };
            }
            default -> RpcResponse.UNSUPPORTED_OPERATION;
        };
    }
    
}
