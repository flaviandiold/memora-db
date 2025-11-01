package com.memora.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.memora.model.ClusterMap;
import com.memora.services.ClientManager;

public class CoreServiceModule extends AbstractModule {

    @Provides
    @Singleton
    public ClientManager provideClientManager(ClusterMap clusterMap) {
        return new ClientManager(clusterMap);
    }

    @Provides
    @Singleton
    public ClusterMap provideClusterMap() {
        return new ClusterMap(0);
    }

}
