package com.memora.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.memora.commands.DelCommand;
import com.memora.commands.GetCommand;
import com.memora.commands.PutCommand;
import com.memora.commands.ReplicateCommand;
import com.memora.commands.UnknownCommand;
import com.memora.constants.Constants;
import com.memora.constants.ThreadPool;
import com.memora.services.BucketManager;
import com.memora.services.CommandExecutor;
import com.memora.services.RoutingService;
import com.memora.services.ThreadPoolService;

public class ServiceModule extends AbstractModule {

    @Provides
    @Singleton
    public ThreadPoolService provideThreadPoolService() {
        ThreadPoolService threadPoolService = new ThreadPoolService();
        for (ThreadPool pool : ThreadPool.getAllThreadPool()) {
            if (!pool.isCluster()) {
                threadPoolService.createThreadPool(pool);
            }
        }
        return threadPoolService;
    }

    @Provides
    @Singleton
    public BucketManager provideBucketManager(
            @Named(Constants.NODE_ID) String nodeId,
            @Named(Constants.NUMBER_OF_BUCKETS) int numberOfBuckets,
            RoutingService routingService
    ) {
        return new BucketManager(nodeId, numberOfBuckets, routingService);
    }

    @Provides
    @Singleton
    public CommandExecutor provideCommandExecutor(
            final PutCommand putCommand,
            final GetCommand getCommand,
            final DelCommand delCommand,
            final UnknownCommand unknownCommand,
            final ReplicateCommand replicateCommand
    ) {
        return new CommandExecutor(
                putCommand,
                getCommand,
                delCommand,
                replicateCommand,
                unknownCommand
        );
    }

    @Provides
    @Singleton
    public RoutingService provideRoutingService() {
        return new RoutingService();
    }

}
