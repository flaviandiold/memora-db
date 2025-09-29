package com.memora.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.memora.constants.Constants;
import com.memora.enums.ThreadPool;
import com.memora.operations.DelOperation;
import com.memora.operations.GetOperation;
import com.memora.operations.InfoOperation;
import com.memora.operations.PutOperation;
import com.memora.operations.NodeOperation;
import com.memora.operations.UnknownOperation;
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
            final PutOperation putCommand,
            final GetOperation getCommand,
            final DelOperation delCommand,
            final UnknownOperation unknownCommand,
            final InfoOperation infoCommand,
            final NodeOperation replicateCommand
    ) {
        return new CommandExecutor(
                putCommand,
                getCommand,
                delCommand,
                replicateCommand,
                infoCommand,
                unknownCommand
        );
    }

    @Provides
    @Singleton
    public RoutingService provideRoutingService() {
        return new RoutingService();
    }

}
