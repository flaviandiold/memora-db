package com.memora.operations;

import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;

public class UnknownOperation extends Operation {

    @Override
    public RpcResponse execute(RpcRequest request) {
        return RpcResponse.UNSUPPORTED_OPERATION;
    }

}
