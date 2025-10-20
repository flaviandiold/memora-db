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
    public ClusterMap provideClusterMap() {
        return new ClusterMap(0);
    }

    @Provides
    @Singleton
    public ReplicationManager provideReplicationManager(NodeInfo nodeInfo, BucketManager bucketManager, ClientManager clientManager, ThreadPoolService threadPoolService, ClusterMap clusterMap) {
        return new ReplicationManager(nodeInfo, bucketManager, clientManager, threadPoolService, clusterMap);
    }


    @Provides
    @Singleton
    public ClusterOrchestrator provideClusterOrchestrator(NodeInfo nodeInfo, ReplicationManager replicationManager, ClientManager clientManager, ThreadPoolService threadPoolService, ClusterMap clusterMap) {
        return new ClusterOrchestrator(nodeInfo, replicationManager, clientManager, threadPoolService, clusterMap);
    }

}
