package com.memora.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.memora.commands.DelCommand;
import com.memora.commands.GetCommand;
import com.memora.commands.PutCommand;
import com.memora.commands.ReplicateCommand;
import com.memora.commands.UnknownCommand;
import com.memora.core.MemoraNode;
import com.memora.core.Version;
import com.memora.services.BucketManager;

public class CommandModule extends AbstractModule {

    @Provides
    @Singleton
    public GetCommand provideGetCommand(
            final BucketManager bucketManager
    ) {
        return new GetCommand(bucketManager);
    }

    @Provides
    @Singleton
    public PutCommand providePutCommand(
            final BucketManager bucketManager,
            final Version version
    ) {
        return new PutCommand(bucketManager, version);
    }

    @Provides
    @Singleton
    public DelCommand provideDelCommand(
            final BucketManager bucketManager,
            final Version version
    ) {
        return new DelCommand(bucketManager, version);
    }

    @Provides
    @Singleton
    public ReplicateCommand provideReplicateCommand(
            final MemoraNode memoraNode
    ) {
        return new ReplicateCommand(memoraNode);
    }

    @Provides
    @Singleton
    public UnknownCommand provideUnknownCommand() {
        return new UnknownCommand();
    }

}
