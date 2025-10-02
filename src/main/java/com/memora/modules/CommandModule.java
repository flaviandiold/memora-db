package com.memora.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.memora.core.MemoraNode;
import com.memora.operations.DelOperation;
import com.memora.operations.GetOperation;
import com.memora.operations.InfoOperation;
import com.memora.operations.PutOperation;
import com.memora.operations.NodeOperation;
import com.memora.operations.UnknownOperation;

public class CommandModule extends AbstractModule {

    @Provides
    @Singleton
    public GetOperation provideGetOperation(
            final MemoraNode node
    ) {
        return new GetOperation(node);
    }

    @Provides
    @Singleton
    public PutOperation providePutOperation(
            final MemoraNode node
    ) {
        return new PutOperation(node);
    }

    @Provides
    @Singleton
    public DelOperation provideDelOperation(
            final MemoraNode node
    ) {
        return new DelOperation(node);
    }

    @Provides
    @Singleton
    public NodeOperation provideNodeOperation(
            final MemoraNode node
    ) {
        return new NodeOperation(node);
    }

    @Provides
    @Singleton
    public InfoOperation provideInfoOperation(
            MemoraNode node
    ) {
        return new InfoOperation(node);
    }


    @Provides
    @Singleton
    public UnknownOperation provideUnknownOperation() {
        return new UnknownOperation();
    }

}
