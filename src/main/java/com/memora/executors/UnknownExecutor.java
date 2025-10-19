package com.memora.executors;

import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;

public class UnknownExecutor extends Executor {

    @Override
    public RpcResponse execute(RpcRequest request) {
        return UNSUPPORTED_OPERATION(request);
    }

}
