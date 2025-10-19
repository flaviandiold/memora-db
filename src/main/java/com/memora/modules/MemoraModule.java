package com.memora.modules;

import java.util.List;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.memora.MemoraDB;
import com.memora.constants.Constants;
import com.memora.core.MemoraServerChannel;
import com.memora.core.MemoraNode;
import com.memora.core.MemoraServer;
import com.memora.model.NodeBase;
import com.memora.model.NodeInfo;
import com.memora.services.BucketManager;
import com.memora.services.ClusterOrchestrator;
import com.memora.services.CommandExecutor;
import com.memora.services.ReplicationManager;
import com.memora.services.ThreadPoolService;

public class MemoraModule extends AbstractModule {

    @Provides
    @Singleton
    public NodeInfo provideNodeData(
            @Named(Constants.NODE_ID) String nodeId,
            @Named(Constants.NODE_HOST) String host,
            @Named(Constants.NODE_PORT) int port
    ) {
        return NodeInfo.create(nodeId, host, port);
    }


    @Provides
    @Singleton
    public MemoraNode provideMemoraNode(
        final NodeInfo nodeInfo,
        final BucketManager bucketManager,
        final ThreadPoolService threadPoolService,
        final @Named(Constants.MY_REPLICAS) List<NodeBase> myReplicas,
        final Provider<ClusterOrchestrator> clusterOrchestratorProvider,
        final Provider<ReplicationManager> replicationManagerProvider
    ) {
        return new MemoraNode(nodeInfo, myReplicas, threadPoolService, bucketManager, clusterOrchestratorProvider, replicationManagerProvider);
    }

    @Provides
    @Singleton
    public MemoraServerChannel provideMemoraChannel(
        final CommandExecutor executor
    ){
        return new MemoraServerChannel(executor);
    }

    @Provides
    @Singleton
    public MemoraServer provideMemoraServer(
            @Named(Constants.NODE_HOST) String host,
            @Named(Constants.NODE_PORT) int port,
            final MemoraServerChannel channel,
            final ThreadPoolService threadPoolService
    ) {
        return new MemoraServer(host, port, channel, threadPoolService);
    }

    @Provides
    @Singleton
    public MemoraDB initiateCache(final MemoraNode node, final MemoraServer server) {
        return new MemoraDB(node, server);
    }
}
