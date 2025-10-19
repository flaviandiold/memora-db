package com.memora.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.memora.core.MemoraNode;
import com.memora.executors.DelExecutor;
import com.memora.executors.GetExecutor;
import com.memora.executors.InfoExecutor;
import com.memora.executors.NodeExecutor;
import com.memora.executors.PutExecutor;
import com.memora.executors.UnknownExecutor;

public class CommandModule extends AbstractModule {

    @Provides
    @Singleton
    public GetExecutor provideGetExecutor(
            final MemoraNode node
    ) {
        return new GetExecutor(node);
    }

    @Provides
    @Singleton
    public PutExecutor providePutExecutor(
            final MemoraNode node
    ) {
        return new PutExecutor(node);
    }

    @Provides
    @Singleton
    public DelExecutor provideDelExecutor(
            final MemoraNode node
    ) {
        return new DelExecutor(node);
    }

    @Provides
    @Singleton
    public NodeExecutor provideNodeExecutor(
            final MemoraNode node
    ) {
        return new NodeExecutor(node);
    }

    @Provides
    @Singleton
    public InfoExecutor provideInfoExecutor(
            MemoraNode node
    ) {
        return new InfoExecutor(node);
    }


    @Provides
    @Singleton
    public UnknownExecutor provideUnknownExecutor() {
        return new UnknownExecutor();
    }

}
