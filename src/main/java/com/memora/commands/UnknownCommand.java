package com.memora.commands;

import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;

public class UnknownCommand extends Command {

    @Override
    public RpcResponse execute(RpcRequest request) {
        return RpcResponse.UNSUPPORTED_COMMAND;
    }

}
