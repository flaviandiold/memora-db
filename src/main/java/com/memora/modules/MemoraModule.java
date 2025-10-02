package com.memora.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.memora.MemoraDB;
import com.memora.constants.Constants;
import com.memora.core.MemoraChannel;
import com.memora.core.MemoraNode;
import com.memora.core.MemoraServer;
import com.memora.core.Version;
import com.memora.model.NodeInfo;
import com.memora.services.BucketManager;
import com.memora.services.ClusterOrchestrator;
import com.memora.services.CommandExecutor;
import com.memora.services.ReplicationManager;

public class MemoraModule extends AbstractModule {

    @Provides
    @Singleton
    public NodeInfo provideNodeInfo(            
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
        final Version version,
        final BucketManager bucketManager,
        final Provider<ClusterOrchestrator> clusterOrchestratorProvider,
        final Provider<ReplicationManager> replicationManagerProvider
    ) {
        return new MemoraNode(nodeInfo, version, bucketManager, clusterOrchestratorProvider, replicationManagerProvider);
    }

    @Provides
    @Singleton
    public MemoraChannel provideMemoraChannel(
        final Version version,
        final CommandExecutor executor
    ){
        return new MemoraChannel(version, executor);
    }

    @Provides
    @Singleton
    public MemoraServer provideMemoraServer(
            @Named(Constants.NODE_HOST) String host,
            @Named(Constants.NODE_PORT) int port,
            final MemoraChannel channel
    ) {
        return new MemoraServer(host, port, channel);
    }

    @Provides
    @Singleton
    public MemoraDB initiateCache(final MemoraServer server) {
        return new MemoraDB(server);
    }

    @Provides
    @Singleton
    public Version provideVersion(NodeInfo nodeInfo, Provider<ClusterOrchestrator> clusterOrchestratorProvider) {
        return new Version(nodeInfo, clusterOrchestratorProvider);
    }
}
