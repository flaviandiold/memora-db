package com.memora.executors;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.inject.Inject;
import com.memora.core.MemoraNode;
import com.memora.messages.KeyCommand;
import com.memora.messages.KeyCommandBatch;
import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.messages.RpcStatus;
import com.memora.model.CacheEntry;

public class GetExecutor extends Executor {

    private final MemoraNode memoraNode;

    @Inject
    public GetExecutor(
        final MemoraNode memoraNode
    ) {
        this.memoraNode = memoraNode;
    }

    @Override
    public RpcResponse execute(RpcRequest request) {
        KeyCommandBatch commandBatch = request.getGetCommand();

        RpcStatus status = RpcStatus.OK;

        List<String> values = new ArrayList<>();
        for (KeyCommand command : commandBatch.getCommandsList()) {
            CacheEntry entry = memoraNode.get(command.getKey());
            if (Objects.isNull(entry)) {
                status = RpcStatus.PARTIAL_FULFILLMENT;
                values.add(null);
            } else {
                values.add(entry.getValue());
            }
        }

        if (values.size() == 1) {
            if (Objects.isNull(values.get(0))) return NOT_FOUND(request);
            else return OK(request, values.get(0));
        }

        return respond(request, status, values);
    }
    
}
