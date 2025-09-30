package com.memora.operations;

import com.memora.enums.Operations;
import com.memora.model.NodeInfo;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;

public class InfoOperation extends Operation {

    private static final String INFO_COMMAND = Operations.INFO.operation();
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
        if (!INFO_COMMAND.equalsIgnoreCase(parts[0])) {
            throw new IllegalArgumentException("Invalid command for InfoCommand: " + command);
        }

        if (parts.length < 3) {
            return RpcResponse.BAD_REQUEST("INFO command requires at least 2 arguments");
        }
        
        return switch (parts[1].toUpperCase()) {
            case NODE_SUB_COMMAND -> {
                yield switch (parts[2].toUpperCase()) {
                    case ID -> RpcResponse.OK(info.getNodeId());
                    default -> RpcResponse.UNSUPPORTED_OPERATION("Invalid sub-command for InfoCommand " + parts[2]);
                };
            }
            default -> RpcResponse.UNSUPPORTED_OPERATION;
        };
    }
    
}
