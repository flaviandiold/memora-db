package com.memora.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.memora.core.MemoraNode;
import com.memora.core.Version;
import com.memora.model.NodeInfo;
import com.memora.operations.DelOperation;
import com.memora.operations.GetOperation;
import com.memora.operations.InfoOperation;
import com.memora.operations.PutOperation;
import com.memora.operations.NodeOperation;
import com.memora.operations.UnknownOperation;
import com.memora.services.BucketManager;

public class CommandModule extends AbstractModule {

    @Provides
    @Singleton
    public GetOperation provideGetOperation(
            final BucketManager bucketManager
    ) {
        return new GetOperation(bucketManager);
    }

    @Provides
    @Singleton
    public PutOperation providePutOperation(
            final BucketManager bucketManager,
            final Version version
    ) {
        return new PutOperation(bucketManager, version);
    }

    @Provides
    @Singleton
    public DelOperation provideDelOperation(
            final BucketManager bucketManager,
            final Version version
    ) {
        return new DelOperation(bucketManager, version);
    }

    @Provides
    @Singleton
    public NodeOperation provideNodeOperation(
            final MemoraNode memoraNode
    ) {
        return new NodeOperation(memoraNode);
    }

    @Provides
    @Singleton
    public InfoOperation provideInfoOperation(
            final NodeInfo nodeInfo
    ) {
        return new InfoOperation(nodeInfo);
    }


    @Provides
    @Singleton
    public UnknownOperation provideUnknownOperation() {
        return new UnknownOperation();
    }

}
