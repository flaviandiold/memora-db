package com.memora.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.memora.constants.Constants;
import com.memora.enums.ThreadPool;
import com.memora.executors.ClusterExecutor;
import com.memora.executors.DelExecutor;
import com.memora.executors.GetExecutor;
import com.memora.executors.InfoExecutor;
import com.memora.executors.NodeExecutor;
import com.memora.executors.PutExecutor;
import com.memora.executors.UnknownExecutor;
import com.memora.model.ClusterMap;
import com.memora.model.NodeInfo;
import com.memora.services.BucketManager;
import com.memora.services.ClientManager;
import com.memora.services.ClusterOrchestrator;
import com.memora.services.CommandExecutor;
import com.memora.services.ReplicationManager;
import com.memora.services.ThreadPoolService;

public class ServiceModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new CoreServiceModule()); 
    }

    @Provides
    @Singleton
    public BucketManager provideBucketManager(
            @Named(Constants.NODE_ID) String nodeId,
            @Named(Constants.NUMBER_OF_BUCKETS) int numberOfBuckets
    ) {
        return new BucketManager(nodeId, numberOfBuckets);
    }


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
    public CommandExecutor provideCommandExecutor(
            final PutExecutor putExecutor,
            final GetExecutor getExecutor,
            final DelExecutor delExecutor,
            final ClusterExecutor clusterExecutor,
            final UnknownExecutor unknownExecutor,
            final InfoExecutor infoExecutor,
            final NodeExecutor nodeExecutor
    ) {
        return new CommandExecutor(
                putExecutor,
                getExecutor,
                delExecutor,
                nodeExecutor,
                infoExecutor,
                clusterExecutor,
                unknownExecutor
        );
    }

    @Provides
    @Singleton
    public ReplicationManager provideReplicationManager(
        BucketManager bucketManager,
        ClientManager clientManager,
        ThreadPoolService threadPoolService,
        ClusterMap clusterMap,
        @Named(Constants.REPLICATION_FACTOR) int replicationFactor
    ) {
        return new ReplicationManager(bucketManager, clientManager, threadPoolService, clusterMap, replicationFactor);
    }

    @Provides
    @Singleton
    public ClusterOrchestrator provideClusterOrchestrator(
        ReplicationManager replicationManager,
        ClientManager clientManager,
        ThreadPoolService threadPoolService,
        ClusterMap clusterMap
    ) {
        return new ClusterOrchestrator(replicationManager, clientManager, threadPoolService, clusterMap);
    }

}
