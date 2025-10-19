package com.memora.executors;

import com.google.inject.Inject;
import com.memora.core.MemoraNode;
import com.memora.messages.KeyCommand;
import com.memora.messages.KeyCommandBatch;
import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.store.WAL;

public class DelExecutor extends Executor {

    private final MemoraNode node;


    @Inject
    public DelExecutor(
        final MemoraNode node
    ) {
        this.node = node;
    }

    @Override
    public RpcResponse execute(RpcRequest request) {
        KeyCommandBatch commandBatch = request.getDeleteCommand();
        WAL.log(request);

        for (KeyCommand command : commandBatch.getCommandsList()) {
            node.delete(command.getKey());
        }

        return OK(request);
    }

}
