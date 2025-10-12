package com.memora.operations;

import java.util.Arrays;

import com.google.inject.Inject;
import com.memora.constants.Constants;
import com.memora.core.MemoraNode;
import com.memora.enums.Operations;
import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NodeOperation extends Operation {

    private static final String NODE_COMMAND = Operations.NODE.operation();
    private static final String PRIMARIZE_COMMAND = "PRIMARIZE";
    private static final String REPLICATE_COMMAND = "REPLICATE";

    private final MemoraNode node;

    @Inject
    public NodeOperation(
            final MemoraNode node
    ) {
        this.node = node;
    }

    @Override
    public RpcResponse execute(RpcRequest request) {
        String command = request.getCommand();
        String[] parts = command.split(" ");
        if (!NODE_COMMAND.equalsIgnoreCase(parts[0])) {
            throw new IllegalArgumentException("Invalid command for NodeCommand: " + command);
        }

        if (parts.length < 3) {
            return RpcResponse.BAD_REQUEST("NODE command requires at least 2 arguments");
        }

        final String address[] = parts[2].split(Constants.ADDRESS_DELIMITER);
        if (address.length < 2) {
            return RpcResponse.BAD_REQUEST("Invalid address for NodeCommand: " + parts[2]);
        }

        switch (parts[1].toUpperCase()) {
            case PRIMARIZE_COMMAND ->
                node.primarize(address[0], Integer.parseInt(address[1]));
            case REPLICATE_COMMAND ->
                node.replicate(address[0], Integer.parseInt(address[1]));
            default -> {
                return RpcResponse.UNSUPPORTED_OPERATION("Invalid sub-command for NodeCommand: " + parts[1]);
            }
        }

        return RpcResponse.OK;
    }
}
