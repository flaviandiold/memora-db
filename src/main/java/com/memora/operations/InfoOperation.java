package com.memora.operations;

import com.memora.core.MemoraNode;
import com.memora.enums.NodeType;
import com.memora.enums.Operations;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;
import com.memora.utils.Parser;

public class InfoOperation extends Operation {

    private static final String INFO_COMMAND = Operations.INFO.operation();
    private static final String NODE_SUB_COMMAND = "NODE";
    private static final String BUCKET_SUB_COMMAND = "BUCKET";
    private static final String CLUSTER_SUB_COMMAND = "CLUSTER";
    private static final String MAP = "MAP";
    private static final String ID = "ID";

    private final MemoraNode node;


    public InfoOperation(MemoraNode node) {
        this.node = node;
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
                    case ID -> RpcResponse.OK(node.getInfo().getNodeId());
                    default -> RpcResponse.UNSUPPORTED_OPERATION("Invalid sub-command for InfoCommand " + parts[2]);
                };
            }
            case BUCKET_SUB_COMMAND -> {
                yield switch (parts[2].toUpperCase()) {
                    case MAP -> RpcResponse.OK(Parser.toJson(node.getAllBuckets()));
                    default -> RpcResponse.UNSUPPORTED_OPERATION("Invalid sub-command for InfoCommand " + parts[2]);
                };
            }
            case CLUSTER_SUB_COMMAND -> {
                if (NodeType.STANDALONE.equals(node.getInfo().getNodeType())) yield RpcResponse.UNSUPPORTED_OPERATION("Node is standalone");
                yield switch (parts[2].toUpperCase()) {
                    case MAP -> RpcResponse.OK(Parser.toJson(node.getClusterMap()));
                    default -> RpcResponse.UNSUPPORTED_OPERATION("Invalid sub-command for InfoCommand " + parts[2]);
                };
            }
            default -> RpcResponse.UNSUPPORTED_OPERATION;
        };
    }
}
