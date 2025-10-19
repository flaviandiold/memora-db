package com.memora.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.memora.enums.ThreadPool;
import com.memora.services.ClientManager;
import com.memora.services.ThreadPoolService;

public class ClientModule extends AbstractModule {

    @Provides
    @Singleton
    public ClientManager provideClientManager(ThreadPoolService threadPoolService) {
        return new ClientManager(threadPoolService);
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

}
